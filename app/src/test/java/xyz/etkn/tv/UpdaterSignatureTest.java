package xyz.etkn.tv;

import android.content.pm.*;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
public class UpdaterSignatureTest {
    private final File apk=new File("update.part");
    private final PackageManager manager=mock(PackageManager.class);
    private final PackageInfo installed=info(2),candidate=info(3);

    private static PackageInfo info(int version){
        PackageInfo info=mock(PackageInfo.class);
        info.packageName=BuildConfig.APPLICATION_ID;info.versionCode=version;
        when(info.getLongVersionCode()).thenReturn((long)version);return info;
    }
    private static Signature cert(String value){
        Signature cert=mock(Signature.class);
        when(cert.toByteArray()).thenReturn(value.getBytes(StandardCharsets.UTF_8));return cert;
    }
    private static void sign(PackageInfo info,Signature... signatures){
        info.signatures=signatures;info.signingInfo=mock(SigningInfo.class);
        when(info.signingInfo.getApkContentsSigners()).thenReturn(signatures);
    }
    private void validate(int sdk)throws Exception {
        when(manager.getPackageInfo(eq(BuildConfig.APPLICATION_ID),anyInt())).thenReturn(installed);
        when(manager.getPackageArchiveInfo(eq(apk.getAbsolutePath()),anyInt())).thenAnswer(call->{
            int flags=call.getArgument(1);
            // Android 10's archive parser collects certificates only with GET_SIGNATURES.
            return (flags&PackageManager.GET_SIGNATURES)!=0?candidate:info(3);
        });
        Updater.Release release=new Updater.Release(new JSONObject().put("packageName",BuildConfig.APPLICATION_ID)
            .put("versionCode",3).put("versionName","0.1.2").put("size",200000).put("sha256","a".repeat(64))
            .put("apkUrl","https://github.com/hbq0405/etkn-tv/releases/download/v0.1.2/etkn-tv-0.1.2.apk"));
        Updater.validatePackage(manager,BuildConfig.APPLICATION_ID,apk,release,sdk);
    }
    @Test public void sameSignerAcceptedOnAndroid7()throws Exception {
        sign(installed,cert("release"));sign(candidate,cert("release"));validate(24);
    }
    @Test public void archiveCertificateCollectionRequestedOnAndroid9And10()throws Exception {
        sign(installed,cert("release"));sign(candidate,cert("release"));
        validate(28);validate(29);
    }
    @Test public void legacyCertificateFallbackAcceptedWhenSigningInfoAbsent()throws Exception {
        sign(installed,cert("release"));sign(candidate,cert("release"));
        installed.signingInfo=null;candidate.signingInfo=null;validate(29);
    }
    @Test public void missingAndEmptyCertificatesRejected()throws Exception {
        sign(installed,cert("release"));
        assertEquals("无法读取更新包签名，请重新下载或手动覆盖安装",assertThrows(Exception.class,()->validate(29)).getMessage());
        sign(candidate);assertEquals("无法读取更新包签名，请重新下载或手动覆盖安装",assertThrows(Exception.class,()->validate(29)).getMessage());
    }
    @Test public void differentSignerRejectedEvenWhenLegacySignerMatches()throws Exception {
        sign(installed,cert("release"));sign(candidate,cert("foreign"));candidate.signatures=installed.signatures;
        assertEquals("更新包签名不符",assertThrows(Exception.class,()->validate(29)).getMessage());
    }
    @Test public void multipleSignersComparedWithoutDependingOnOrder()throws Exception {
        sign(installed,cert("first"),cert("second"));sign(candidate,cert("second"),cert("first"));validate(29);
        sign(candidate,cert("first"));assertEquals("更新包签名不符",assertThrows(Exception.class,()->validate(29)).getMessage());
    }
    @Test public void wrongPackageAndNonIncreasingVersionRejected()throws Exception {
        sign(installed,cert("release"));sign(candidate,cert("release"));candidate.packageName="another.app";
        assertEquals("更新包版本或应用标识不符",assertThrows(Exception.class,()->validate(29)).getMessage());
        candidate.packageName=BuildConfig.APPLICATION_ID;when(candidate.getLongVersionCode()).thenReturn(2L);
        assertEquals("更新包版本或应用标识不符",assertThrows(Exception.class,()->validate(29)).getMessage());
    }
}
