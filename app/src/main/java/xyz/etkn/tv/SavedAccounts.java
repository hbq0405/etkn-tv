package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class SavedAccounts {
    static final class Account {
        final String userId,name;
        /** true 表示注册表只保存了账号信息（如管理员），选择时需要输入密码完成登录。 */
        final boolean needsPassword;
        Account(String userId,String name,boolean needsPassword){this.userId=userId;this.name=name;this.needsPassword=needsPassword;}
    }
    private SavedAccounts(){}
    /** 新账号追加到末尾、已有账号原地更新：列表顺序即首次登录顺序，固定不变。 */
    static String upsert(String stored,String userId,String name,String encryptedToken){
        JSONObject account=new JSONObject();
        try{account.put("userId",userId).put("name",name).put("token",encryptedToken);}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
        JSONArray result=new JSONArray();boolean replaced=false;
        for(JSONObject entry:entries(stored)){
            if(entry.optString("userId").equals(userId)){result.put(account);replaced=true;}
            else result.put(entry);
        }
        if(!replaced)result.put(account);
        return result.toString();
    }
    static String remove(String stored,String userId){
        JSONArray result=new JSONArray();
        for(JSONObject entry:entries(stored))if(!entry.optString("userId").equals(userId))result.put(entry);
        return result.toString();
    }
    static JSONObject find(String stored,String userId){
        for(JSONObject entry:entries(stored))if(entry.optString("userId").equals(userId))return entry;
        return null;
    }
    static List<Account> list(String stored){
        List<Account> result=new ArrayList<>();
        for(JSONObject entry:entries(stored)){
            String userId=entry.optString("userId");
            if(!userId.isEmpty())result.add(new Account(userId,entry.optString("name"),entry.optString("token").isEmpty()));
        }
        return result;
    }
    private static List<JSONObject> entries(String stored){
        JSONArray array;
        try{array=new JSONArray(stored);}catch(JSONException error){array=new JSONArray();}
        List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){JSONObject entry=array.optJSONObject(i);if(entry!=null)result.add(entry);}
        return result;
    }
}
