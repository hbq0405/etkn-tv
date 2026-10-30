package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SessionStore {
    private final SharedPreferences prefs;
    private static final String KEY = "etkn-tv-session";
    SessionStore(Context context) { prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE); }
    String server() { return prefs.getString("server", ""); }
    String userId() { return prefs.getString("userId", ""); }
    String name() { return prefs.getString("name", ""); }
    List<SavedAccounts.Account> accounts(){
        String currentId=userId(),currentToken=prefs.getString("token","");
        if(prefs.getBoolean("mediaEntryVerified",false)&&!currentId.isEmpty()&&!currentToken.isEmpty()){
            String stored=prefs.getString("accounts","[]"),updated=SavedAccounts.upsert(stored,currentId,name(),currentToken);
            if(!updated.equals(stored))prefs.edit().putString("accounts",updated).apply();
        }
        return SavedAccounts.list(prefs.getString("accounts","[]"));
    }
    void ensureServer(String address)throws Exception{
        if(!accounts().isEmpty()&&!server().equals(address))throw new Exception("账号切换仅支持当前 ETKN 媒体服务器");
    }
    void activate(String targetUserId)throws Exception{
        accounts();
        org.json.JSONObject account=SavedAccounts.find(prefs.getString("accounts","[]"),targetUserId);
        if(account==null)throw new Exception("该用户的登录信息不可用，请重新登录");
        prefs.edit().putString("userId",targetUserId).putString("name",account.optString("name"))
                .putString("token",account.getString("token")).putBoolean("mediaEntryVerified",true).apply();
    }
    String deviceId() {
        String value = prefs.getString("deviceId", "");
        if (value.isEmpty()) { value = UUID.randomUUID().toString(); prefs.edit().putString("deviceId", value).apply(); }
        return value;
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(KEY)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(KEY, null);
    }
    String token() {
        if (!prefs.getBoolean("mediaEntryVerified",false)) return "";
        String value = prefs.getString("token", "");
        if (value.isEmpty()) return "";
        try { return decrypt(value); } catch (Exception error) { clear(); return ""; }
    }
    /** 解密指定已保存账号的令牌，供用户选择框加载该账号自己的头像；失败只返回空串，不影响当前会话。 */
    String accountToken(String userId) {
        org.json.JSONObject account=SavedAccounts.find(prefs.getString("accounts","[]"),userId);
        if(account==null)return "";
        try{return decrypt(account.getString("token"));}catch(Exception error){return "";}
    }
    private String decrypt(String value) throws Exception {
        String[] parts = value.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void save(String server, String userId, String name, String token) throws Exception {
        ensureServer(server);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        String registry=SavedAccounts.upsert(prefs.getString("accounts","[]"),userId,name,encrypted);
        prefs.edit().putString("accounts",registry).putString("server", server).putString("userId", userId).putString("name", name).putString("token", encrypted).putBoolean("mediaEntryVerified",true).apply();
    }
    void clear() {
        String currentId=userId(),registry=currentId.isEmpty()?prefs.getString("accounts","[]"):SavedAccounts.remove(prefs.getString("accounts","[]"),currentId);
        prefs.edit().putString("accounts",registry).remove("token").remove("userId").remove("name").remove("mediaEntryVerified").apply();
    }
}
