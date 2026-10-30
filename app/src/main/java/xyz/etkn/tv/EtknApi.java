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
    static final String USER_AGENT="ETKN TV/"+BuildConfig.VERSION_NAME+" (Android)";
    final SessionStore session;
    final String server;
    final String token;
    final java.io.File cacheDir;
    EtknApi(Context context) { session = new SessionStore(context); server = session.server(); token = session.token(); cacheDir=new java.io.File(context.getCacheDir(),"artwork"); }
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
    JSONObject post(String path, JSONObject body) throws Exception { return request(server, token, "POST", path, body); }
    JSONObject delete(String path) throws Exception { return request(server, token, "DELETE", path, null); }
    JSONObject request(String origin, String access, String method, String path, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(origin + path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(7000); conn.setReadTimeout(35000); conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"ETKN TV\", Device=\"" + Build.MODEL.replace("\"", "") + "\", DeviceId=\"" + session.deviceId() + "\", Version=\"" + BuildConfig.VERSION_NAME + "\"");
        if (!access.isEmpty()) { conn.setRequestProperty("X-Emby-Token", access); conn.setRequestProperty("Cookie", "etkn_session=" + access); }
        try {
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
        } finally { conn.disconnect(); }
    }
    static String readText(InputStream stream) throws Exception {
        try (stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = stream.read(buffer)) != -1) { bytes.write(buffer, 0, length); if (bytes.size() > 8*1024*1024) throw new Exception("服务响应过大"); }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    void login(String address, String username, String password) throws Exception {
        address = LanDiscovery.resolve(address,7000).getString("url");
        session.ensureServer(address);
        JSONObject result = request(address, "", "POST", "/api/tv/Users/AuthenticateByName", new JSONObject().put("Username", username).put("Pw", password));
        String access = result.getString("AccessToken"); JSONObject user = result.getJSONObject("User");
        session.save(address, user.getString("Id"), user.optString("Name"), access);
    }
    String userPath() { return "/api/tv/Users/" + encode(session.userId()); }
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
