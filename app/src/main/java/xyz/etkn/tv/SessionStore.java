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
    static final int PERSIST_FULL=0,PERSIST_ACCOUNT=1,PERSIST_NONE=2;
    /** 私密账号及未开启记住登录的管理员令牌只存内存，后台结束临时会话。 */
    private static String memoryToken,memoryUserId,memoryName;
    private final SharedPreferences prefs;
    private static final String KEY = "etkn-tv-session";
    SessionStore(Context context) { prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE); }
    /** 私密账号优先不落盘；管理员默认只留账号，可在本机显式开启加密令牌保存。 */
    static int persistPolicy(boolean privateAccount,boolean isAdmin){return persistPolicy(privateAccount,isAdmin,false);}
    static int persistPolicy(boolean privateAccount,boolean isAdmin,boolean rememberAdmin){return privateAccount?PERSIST_NONE:isAdmin&&!rememberAdmin?PERSIST_ACCOUNT:PERSIST_FULL;}
    private String rememberAdminKey(String address,String id){return "remember-admin/"+new org.json.JSONArray().put(address).put(id);}
    boolean rememberAdmin(String address,String id){return prefs.getBoolean(rememberAdminKey(address,id),false);}
    void setRememberAdmin(String address,String id,boolean enabled)throws Exception{if(!prefs.edit().putBoolean(rememberAdminKey(address,id),enabled).commit())throw new Exception("无法保存记住登录设置，请重试");}
    String server() { return prefs.getString("server", ""); }
    String userId() { return persist()==PERSIST_NONE ? (memoryUserId==null?"":memoryUserId) : prefs.getString("userId", ""); }
    String name() { return persist()==PERSIST_NONE ? (memoryName==null?"":memoryName) : prefs.getString("name", ""); }
    /** 当前会话的令牌持久化策略；旧版本没有该键时按完整保存处理。 */
    int persist(){int value=prefs.getInt("persist",PERSIST_FULL);return value>=PERSIST_FULL&&value<=PERSIST_NONE?value:PERSIST_FULL;}
    List<SavedAccounts.Account> accounts(){
        String currentId=userId(),currentToken=prefs.getString("token","");
        if(persist()==PERSIST_FULL&&prefs.getBoolean("mediaEntryVerified",false)&&!currentId.isEmpty()&&!currentToken.isEmpty()){
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
        if(account.optString("token").isEmpty())throw new Exception("该账号需要输入密码登录");
        endTemporarySession();
        prefs.edit().putString("userId",targetUserId).putString("name",account.optString("name"))
                .putString("token",account.getString("token")).putInt("persist",PERSIST_FULL).putBoolean("mediaEntryVerified",true).apply();
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
        String id=userId();
        if(persist()!=PERSIST_FULL)return !id.isEmpty()&&id.equals(memoryUserId)&&memoryToken!=null?memoryToken:"";
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
        prefs.edit().putString("accounts",registry).putString("server", server).putString("userId", userId).putString("name", name).putString("token", encrypted).putInt("persist",PERSIST_FULL).putBoolean("mediaEntryVerified",true).apply();
        endTemporarySession();
        LoginGate.satisfy();
    }
    /** 按持久化策略保存会话：受限账号不保存令牌，完整会话使用系统密钥加密保存。 */
    void saveSession(String server, String userId, String name, String token, int persist) throws Exception {
        if(persist==PERSIST_FULL){save(server,userId,name,token);return;}
        ensureServer(server);
        String registry=persist==PERSIST_ACCOUNT
                ?SavedAccounts.upsert(prefs.getString("accounts","[]"),userId,name,"")
                :SavedAccounts.remove(prefs.getString("accounts","[]"),userId);
        SharedPreferences.Editor edit=prefs.edit().putString("accounts",registry).putString("server",server).putInt("persist",persist).remove("token").putBoolean("mediaEntryVerified",true);
        if(persist==PERSIST_NONE)edit.remove("userId").remove("name");
        else edit.putString("userId",userId).putString("name",name);
        if(!edit.commit())throw new Exception("无法清除已保存的登录凭据，请重试");
        memoryUserId=userId;memoryName=name;memoryToken=token;
    }
    /** 恢复旧账号前清理受限账号的历史令牌，也覆盖非当前账号。 */
    void restrictSavedAccount(String userId,int persist)throws Exception{
        org.json.JSONObject account=SavedAccounts.find(prefs.getString("accounts","[]"),userId);
        if(account==null)return;
        String registry=persist==PERSIST_NONE?SavedAccounts.remove(prefs.getString("accounts","[]"),userId)
                :SavedAccounts.upsert(prefs.getString("accounts","[]"),userId,account.optString("name"),"");
        SharedPreferences.Editor edit=prefs.edit().putString("accounts",registry);
        if(userId.equals(userId())){
            edit.remove("token").putInt("persist",persist);
            if(persist==PERSIST_NONE)edit.remove("userId").remove("name");
            endTemporarySession();
        }
        if(!edit.commit())throw new Exception("无法清除已保存的登录凭据，请重试");
    }
    static void endTemporarySession(){memoryToken=null;memoryUserId=null;memoryName=null;}
    void clear() {
        String currentId=userId(),registry=currentId.isEmpty()?prefs.getString("accounts","[]"):SavedAccounts.remove(prefs.getString("accounts","[]"),currentId);
        endTemporarySession();
        prefs.edit().putString("accounts",registry).remove("token").remove("userId").remove("name").remove("mediaEntryVerified").remove("persist").apply();
    }
}
