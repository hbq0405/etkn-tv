package xyz.etkn.tv;

import org.json.*;
import java.util.*;

/** Cache identities survive a foreground stream reconnect. No detail DTOs. */
final class DetailChanges {
    private Map<String,String> versions;
    final Set<String> updated=new LinkedHashSet<>();
    boolean structure;
    void accept(JSONObject event){
        JSONObject incoming=event.optJSONObject("Versions");
        if(event.optBoolean("Initial")){
            Map<String,String> next=new HashMap<>();
            if(incoming!=null)for(Iterator<String> keys=incoming.keys();keys.hasNext();){String id=keys.next();next.put(id,incoming.optString(id));}
            if(versions!=null){
                for(String id:next.keySet())if(versions.containsKey(id)&&!next.get(id).equals(versions.get(id)))updated.add(id);
                structure|=!versions.keySet().equals(next.keySet());
            }
            versions=next;return;
        }
        JSONArray ids=event.optJSONArray("Updated");if(ids!=null)for(int i=0;i<ids.length();i++)updated.add(ids.optString(i));
        JSONArray added=event.optJSONArray("Added"),removed=event.optJSONArray("Removed");
        structure|=added!=null&&added.length()>0||removed!=null&&removed.length()>0;
        if(versions==null)versions=new HashMap<>();
        if(incoming!=null)for(Iterator<String> keys=incoming.keys();keys.hasNext();){String id=keys.next();versions.put(id,incoming.optString(id));}
        if(removed!=null)for(int i=0;i<removed.length();i++)versions.remove(removed.optString(i));
    }
}
