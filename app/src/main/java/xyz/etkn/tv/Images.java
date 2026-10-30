package xyz.etkn.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.util.LruCache;
import android.view.ViewTreeObserver;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class Images {
    private static final ExecutorService IO=Executors.newFixedThreadPool(4);
    private static final ExecutorService HERO=Executors.newSingleThreadExecutor();
    private static final LruCache<String,Bitmap> CACHE=new LruCache<>(20*1024*1024) { @Override protected int sizeOf(String key,Bitmap image) { return image.getAllocationByteCount(); } };
    static void rounded(ImageView view,int radiusPx) {
        GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(radiusPx);
        view.setBackground(outline);view.setClipToOutline(true);
    }
    static void load(EtknApi api,String url,ImageView view) {
        load(api,url,view,success->{});
    }
    static void load(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done) {
        load(api,url,view,done,IO);
    }
    /** 用指定账号自己的令牌加载图片：用户选择框中非活动账号的头像按服务端按用户鉴权取图。 */
    static void load(EtknApi api,String url,ImageView view,String token) {
        load(api,url,view,success->{},IO,token);
    }
    static void backdrop(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done){load(api,url,view,done,HERO);}
    static void visible(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done){
        ViewTreeObserver.OnPreDrawListener listener=new ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                if(view.isShown()&&view.getGlobalVisibleRect(new android.graphics.Rect())){
                    view.getViewTreeObserver().removeOnPreDrawListener(this);load(api,url,view,done);
                }
                return true;
            }
        };
        view.getViewTreeObserver().addOnPreDrawListener(listener);
    }
    static String digest(String value){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte part:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",part&255));return result.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static void load(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done,ExecutorService executor){
        load(api,url,view,done,executor,api.token);
    }
    private static void load(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done,ExecutorService executor,String token){
        String key=api.session.userId()+url; view.setTag(key); Bitmap cached=CACHE.get(key); if(cached!=null){view.setImageBitmap(cached);done.accept(true);return;}
        executor.execute(() -> {
            if(!key.equals(view.getTag()))return;
            HttpURLConnection conn=null;
            try {
                File file=new File(api.cacheDir,digest(key));
                Bitmap stored=file.isFile()&&System.currentTimeMillis()-file.lastModified()<24*60*60*1000L?BitmapFactory.decodeFile(file.getPath()):null;
                if(stored!=null){CACHE.put(key,stored);view.post(()->{if(key.equals(view.getTag())){view.setImageBitmap(stored);done.accept(true);}});return;}
                conn=Http.image(url,api,token);
                try(java.io.InputStream stream=conn.getInputStream()) {
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);byte[] payload=bytes.toByteArray();
                    Bitmap bitmap=BitmapFactory.decodeByteArray(payload,0,payload.length);
                    if(bitmap!=null){CACHE.put(key,bitmap);view.post(() -> {if(key.equals(view.getTag())){view.setImageBitmap(bitmap);done.accept(true);}});save(file,payload);}
                    else view.post(()->{if(key.equals(view.getTag()))done.accept(false);});
                }
            }catch(Exception ignored){view.post(()->{if(key.equals(view.getTag()))done.accept(false);});} finally {if(conn!=null)conn.disconnect();}
        });
    }
    private static synchronized void save(File file,byte[] payload){
        try{
            File folder=file.getParentFile();if(folder==null||!folder.isDirectory()&&!folder.mkdirs())return;
            File temporary=File.createTempFile("image-",".tmp",folder);
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(temporary)){output.write(payload);}
            if(!temporary.renameTo(file))temporary.delete();
            File[] files=folder.listFiles();if(files==null)return;long total=0;for(File entry:files)total+=entry.length();
            if(total>64*1024*1024L){java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));for(File entry:files){if(total<=48*1024*1024L)break;long size=entry.length();if(entry.delete())total-=size;}}
        }catch(Exception ignored){}
    }
    static void clear() { CACHE.evictAll(); }
}
