package xyz.etkn.tv;

import android.content.Context;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class EtknApi {
    static volatile int accountPreferenceGeneration;
    static final String USER_AGENT="ETKN TV/"+BuildConfig.VERSION_NAME+" (Android)";
    final SessionStore session;
    final String server;
    final String token;
    final String userId;
    final java.io.File cacheDir;
    private final Context context;
    EtknApi(Context context) { this.context=context.getApplicationContext();session = new SessionStore(context); server = session.server(); token = session.token(); userId=session.userId(); cacheDir=new java.io.File(context.getCacheDir(),"artwork"); }
    static String encode(Object value) { try { return URLEncoder.encode(String.valueOf(value), "UTF-8"); } catch(java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); } }
    static String tmdbImage(String path, String size) { return path == null || path.isEmpty() ? "" : "https://image.tmdb.org/t/p/" + size + path; }
    static String normalizeServer(String input) throws Exception {
        String value = input.trim();
        if (!value.contains("://")) value = "http://" + value;
        URI uri = new URI(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("请输入有效的 ETKN 地址");
        while (value.endsWith("/")) value = value.substring(0, value.length()-1);
        return value;
    }
    JSONObject get(String path) throws Exception { return request(server, token, "GET", path, null); }
    JSONObject get(String path, android.os.CancellationSignal cancellation) throws Exception { return request(server, token, "GET", path, null, cancellation); }
    JSONObject post(String path, JSONObject body) throws Exception { return request(server, token, "POST", path, body); }
    JSONObject post(String path,JSONObject body,android.os.CancellationSignal cancellation)throws Exception{return request(server,token,"POST",path,body,cancellation);}
    JSONObject put(String path, JSONObject body) throws Exception { return request(server, token, "PUT", path, body); }
    JSONObject delete(String path) throws Exception { return request(server, token, "DELETE", path, null); }
    JSONObject request(String origin, String access, String method, String path, JSONObject body) throws Exception {
        return request(origin,access,method,path,body,null);
    }
    private JSONObject request(String origin, String access, String method, String path, JSONObject body, android.os.CancellationSignal cancellation) throws Exception {
        if(cancellation!=null)cancellation.throwIfCanceled();
        HttpURLConnection conn = (HttpURLConnection) new URL(origin + path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(7000); conn.setReadTimeout("POST".equals(method)&&(path.contains("/FolderCover?")||path.contains("/FacetArtwork?"))?180000:35000); conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"ETKN TV\", Device=\"" + Build.MODEL.replace("\"", "") + "\", DeviceId=\"" + session.deviceId() + "\", Version=\"" + BuildConfig.VERSION_NAME + "\"");
        if (!access.isEmpty()) { conn.setRequestProperty("X-Emby-Token", access); conn.setRequestProperty("Cookie", "etkn_session=" + access); }
        if(context!=null&&origin.equals(server)&&access.equals(token)&&TvAppearance.sameSession(this,userId)&&path.startsWith("/api/tv/")&&TvAppearance.locked(context,server)){
            JSONObject snapshot=TvAppearance.account(context,server,userId).optJSONObject("playback_preferences");
            if(snapshot!=null){JSONObject effective=snapshot.optJSONObject("effective_preferences");if(effective!=null){JSONObject frozen=new JSONObject(effective.toString());frozen.put("hide_watched_latest",snapshot.optBoolean("hide_watched_latest"));conn.setRequestProperty("X-ETKN-Locked-Preferences",frozen.toString());}}
        }
        if(cancellation!=null)cancellation.setOnCancelListener(conn::disconnect);
        try {
            if(cancellation!=null)cancellation.throwIfCanceled();
            if (body != null) {
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (java.io.OutputStream stream = conn.getOutputStream()) { stream.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) throw new Exception("服务器地址发生跳转，请使用最终地址登录");
            InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = stream == null ? "" : readText(stream);
            JSONObject json;
            try { json = text.startsWith("[") ? new JSONObject().put("Items", new JSONArray(text)) : text.trim().isEmpty() ? new JSONObject() : new JSONObject(text); }
            catch (Exception error) { throw new Exception("服务响应异常（HTTP " + status + "）"); }
            if (status == 404 && path.startsWith("/api/tv/")) throw new Exception("请先更新 ETKN 服务端以支持独立 TV 接口");
            if (status >= 400) {
                if (status == 401) throw new AuthException();
                throw new Exception(json.optString("detail", "请求失败（HTTP " + status + "）"));
            }
            return json;
        } finally { if(cancellation!=null)cancellation.setOnCancelListener(null);conn.disconnect(); }
    }
    static String readText(InputStream stream) throws Exception {
        try (stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = stream.read(buffer)) != -1) { bytes.write(buffer, 0, length); if (bytes.size() > 8*1024*1024) throw new Exception("服务响应过大"); }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    void login(String address, String username, String password) throws Exception {
        int generation=LoginGate.generation();
        address = LanDiscovery.resolve(address,7000).getString("url");
        session.ensureServer(address);
        JSONObject result = request(address, "", "POST", "/api/tv/Users/AuthenticateByName", new JSONObject().put("Username", username).put("Pw", password));
        String access = result.getString("AccessToken"); JSONObject user = result.getJSONObject("User");
        JSONObject policy=user.optJSONObject("Policy");
        boolean isAdmin=policy!=null&&policy.optBoolean("IsAdministrator");
        boolean remember=isAdmin&&!result.optBoolean("PrivateAccount")&&readRememberAdministrator(address,access,user.getString("Id"));
        int persist=SessionStore.persistPolicy(result.optBoolean("PrivateAccount"),isAdmin,remember);
        if(persist!=SessionStore.PERSIST_FULL&&password.isEmpty())throw new Exception("请先为该账号设置密码，再输入密码登录");
        if(generation!=LoginGate.generation())throw new Exception("应用已离开前台，请重新登录");
        if(isAdmin)session.setRememberAdmin(address,user.getString("Id"),remember);
        session.saveSession(address, user.getString("Id"), user.optString("Name"), access, persist);
        if(generation!=LoginGate.generation()){SessionStore.endTemporarySession();throw new Exception("应用已离开前台，请重新登录");}
    }
    /** 先查询当前服务端策略，避免旧版或模板调整前的令牌直接恢复管理员/私密账号。 */
    int savedAccountPolicy(String userId)throws Exception{
        String access=session.accountToken(userId);
        if(access.isEmpty())return SessionStore.PERSIST_ACCOUNT;
        JSONObject user=request(session.server(),access,"GET","/api/tv/Users/Me",null);
        if(!userId.equals(user.optString("Id")))throw new Exception("已保存账号身份不一致，请重新登录");
        JSONObject policy=user.optJSONObject("Policy");
        boolean admin=policy!=null&&policy.optBoolean("IsAdministrator");
        boolean remember=admin&&!user.optBoolean("PrivateAccount")&&readRememberAdministrator(session.server(),access,userId);
        int persist=SessionStore.persistPolicy(user.optBoolean("PrivateAccount"),admin,remember);
        if(admin)session.setRememberAdmin(session.server(),userId,remember);
        if(persist!=SessionStore.PERSIST_FULL)session.restrictSavedAccount(userId,persist);
        return persist;
    }
    void rememberAdministrator(boolean enabled)throws Exception{
        int generation=LoginGate.generation();
        JSONObject user=get("/api/tv/Users/Me"),policy=user.optJSONObject("Policy");
        if(!session.userId().equals(user.optString("Id"))||policy==null||!policy.optBoolean("IsAdministrator"))throw new Exception("只有管理员可以设置记住登录");
        if(user.optBoolean("PrivateAccount"))throw new Exception("私密账号不保存登录状态");
        if(generation!=LoginGate.generation())throw new Exception("应用已离开前台，请重新设置记住登录");
        put("/api/tv/Users/Me/AccountPreferences",new JSONObject().put("remember_admin_account",enabled));
        if(generation!=LoginGate.generation()||!TvAppearance.sameSession(this,user.getString("Id")))throw new Exception("会话已变化，请重新设置");
        session.saveSession(server,user.getString("Id"),user.optString("Name"),token,enabled?SessionStore.PERSIST_FULL:SessionStore.PERSIST_ACCOUNT);
        session.setRememberAdmin(server,user.getString("Id"),enabled);
        if(generation!=LoginGate.generation()){session.restrictSavedAccount(user.getString("Id"),SessionStore.PERSIST_ACCOUNT);throw new Exception("应用已离开前台，请重新登录");}
    }
    private boolean readRememberAdministrator(String address,String access,String id)throws Exception{
        JSONObject value=request(address,access,"GET","/api/tv/Users/Me/AccountPreferences",null);
        if(value.isNull("remember_admin_account"))value=request(address,access,"PUT","/api/tv/Users/Me/AccountPreferences",new JSONObject().put("remember_admin_account",session.rememberAdmin(address,id)).put("initialize_only",true));
        return value.optBoolean("remember_admin_account");
    }
    boolean syncAccountPreference()throws Exception{
        int generation=LoginGate.generation(),revision=accountPreferenceGeneration;
        JSONObject user=get("/api/tv/Users/Me"),policy=user.optJSONObject("Policy");
        if(policy==null||!policy.optBoolean("IsAdministrator")||user.optBoolean("PrivateAccount"))return false;
        boolean previous=session.rememberAdmin(server,userId),enabled=readRememberAdministrator(server,token,userId);
        if(generation!=LoginGate.generation()||revision!=accountPreferenceGeneration||!TvAppearance.sameSession(this,userId))return false;
        session.setRememberAdmin(server,userId,enabled);
        int persist=enabled?SessionStore.PERSIST_FULL:SessionStore.PERSIST_ACCOUNT;
        if(session.persist()!=persist)session.saveSession(server,userId,user.optString("Name"),token,persist);
        if(generation!=LoginGate.generation()){session.restrictSavedAccount(userId,SessionStore.PERSIST_ACCOUNT);return false;}
        return previous!=enabled;
    }
    void refreshSavedAccounts()throws Exception{
        for(SavedAccounts.Account account:session.accounts()){
            if(account.needsPassword)continue;
            try{savedAccountPolicy(account.userId);}
            catch(AuthException expired){session.restrictSavedAccount(account.userId,SessionStore.PERSIST_ACCOUNT);}
        }
    }
    String userPath() { return "/api/tv/Users/" + encode(session.userId()); }
    String folderArtwork(String libraryId,JSONObject folder,int width){JSONObject tags=folder.optJSONObject("ImageTags");return server+userPath()+"/Libraries/"+encode(libraryId)+"/FolderCover?FolderId="+encode(folder.optString("Id"))+"&maxWidth="+width+"&tag="+encode(tags==null?"":tags.optString("Primary"));}
    String artwork(JSONObject media, String type, int width) {
        String id=media.optString("Id"),tag="";
        if(type.equals("Backdrop")&&!media.optString("ParentBackdropItemId").isEmpty()){
            id=media.optString("ParentBackdropItemId");org.json.JSONArray tags=media.optJSONArray("ParentBackdropImageTags");tag=tags==null?"":tags.optString(0);
        }else if(type.equals("Logo")&&!media.optString("ParentLogoItemId").isEmpty()){
            id=media.optString("ParentLogoItemId");tag=media.optString("ParentLogoImageTag");
        }else{
            JSONObject tags=media.optJSONObject("ImageTags");
            JSONArray backdrops=media.optJSONArray("BackdropImageTags");
            tag=type.equals("Backdrop")?(backdrops==null?media.optString("BackdropImageTag"):backdrops.optString(0)):tags==null?"":tags.optString(type);
        }
        return server+"/api/tv/Items/"+encode(id)+"/Images/"+type+"?maxWidth="+width+"&quality=90&tag="+encode(tag);
    }
    String chapterArtwork(JSONObject media, JSONObject chapter, int width) {
        String path=chapter.optString("ImagePath");
        if(path.isEmpty()) {
            int index=chapter.optInt("ChapterIndex",0);
            path="/api/tv/Items/"+encode(media.optString("Id"))+"/Images/Chapter/"+index;
        } else if(!path.startsWith("/api/")) {
            path=path.startsWith("/")?"/api/tv"+path:"/api/tv/"+path;
        }
        return server+path+"?maxWidth="+width+"&quality=85&tag="+encode(chapter.optString("ImageTag"));
    }
    static String resumeTitle(JSONObject item) {
        if(!"Episode".equals(item.optString("Type")))return title(item);
        String series=item.optString("SeriesName");
        int season=item.optInt("ParentIndexNumber",-1),episode=item.optInt("IndexNumber",-1);
        String number=season<0||episode<0?"":"S"+season+"E"+episode+" - ";
        String episodeTitle=number+title(item);
        return series.isEmpty()?episodeTitle:series+"\n"+episodeTitle;
    }
    String image(JSONObject media) {
        if (!media.optString("poster_path").isEmpty()) return "https://image.tmdb.org/t/p/w342" + media.optString("poster_path");
        String id = media.optString("Id"), type = "Primary";
        if (media.optString("Type").equals("Episode") && !media.optString("ParentBackdropItemId").isEmpty()) { id = media.optString("ParentBackdropItemId"); type = "Backdrop"; }
        JSONObject tags=media.optJSONObject("ImageTags");
        String tag=tags==null?"":tags.optString(type);
        if(type.equals("Backdrop")){JSONArray parentTags=media.optJSONArray("ParentBackdropImageTags");tag=parentTags==null?"":parentTags.optString(0);}
        return server + "/api/tv/Items/" + encode(id) + "/Images/" + type + "?maxWidth=320&quality=85&tag="+encode(tag);
    }
    static List<JSONObject> rows(JSONObject payload) {
        for (String key : new String[]{"Items", "results", "resources", "items", "data"}) {
            JSONArray array = payload.optJSONArray(key);
            if (array != null) { List<JSONObject> rows = new ArrayList<>(); for (int i=0; i<array.length(); i++) if (array.optJSONObject(i)!=null) rows.add(array.optJSONObject(i)); return rows; }
            if (payload.optJSONObject(key) != null) return rows(payload.optJSONObject(key));
        }
        return new ArrayList<>();
    }
    static String title(JSONObject item) { return item.optString("Name", item.optString("title", item.optString("name", "未命名"))); }
    static class AuthException extends Exception { AuthException() { super("登录已过期，请重新登录"); } }
}
