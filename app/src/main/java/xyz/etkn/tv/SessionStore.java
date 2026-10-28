package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
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
        String value = prefs.getString("token", "");
        if (value.isEmpty()) return "";
        try {
            String[] parts = value.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception error) { clear(); return ""; }
    }
    void save(String server, String userId, String name, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        prefs.edit().putString("server", server).putString("userId", userId).putString("name", name).putString("token", encrypted).apply();
    }
    void clear() { prefs.edit().remove("token").remove("userId").remove("name").apply(); }
}
