package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class SavedAccounts {
    static final class Account {
        final String userId,name;
        Account(String userId,String name){this.userId=userId;this.name=name;}
    }
    private SavedAccounts(){}
    static String upsert(String stored,String userId,String name,String encryptedToken){
        JSONArray result=new JSONArray();
        JSONObject account=new JSONObject();
        try{account.put("userId",userId).put("name",name).put("token",encryptedToken);}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
        result.put(account);
        for(JSONObject entry:entries(stored))if(!entry.optString("userId").equals(userId))result.put(entry);
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
            if(!userId.isEmpty()&&!entry.optString("token").isEmpty())result.add(new Account(userId,entry.optString("name")));
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
