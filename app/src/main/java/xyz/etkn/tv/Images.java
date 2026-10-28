package xyz.etkn.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.util.LruCache;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class Images {
    private static final ExecutorService IO=Executors.newFixedThreadPool(4);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<>(20*1024*1024) { @Override protected int sizeOf(String key,Bitmap image) { return image.getAllocationByteCount(); } };
    static void rounded(ImageView view,int radiusPx) {
        GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(radiusPx);
        view.setBackground(outline);view.setClipToOutline(true);
    }
    static void load(EtknApi api,String url,ImageView view) {
        load(api,url,view,success->{});
    }
    static void load(EtknApi api,String url,ImageView view,java.util.function.Consumer<Boolean> done) {
        String key=api.session.userId()+url; view.setTag(key); Bitmap cached=CACHE.get(key); if(cached!=null){view.setImageBitmap(cached);done.accept(true);return;}
        IO.execute(() -> {
            HttpURLConnection conn=null;
            try {
                conn=Http.image(url,api);
                try(java.io.InputStream stream=conn.getInputStream()) {
                    Bitmap bitmap=BitmapFactory.decodeStream(stream);
                    if(bitmap!=null){CACHE.put(key,bitmap);view.post(() -> {if(key.equals(view.getTag())){view.setImageBitmap(bitmap);done.accept(true);}});}
                    else view.post(()->{if(key.equals(view.getTag()))done.accept(false);});
                }
            }catch(Exception ignored){view.post(()->{if(key.equals(view.getTag()))done.accept(false);});} finally {if(conn!=null)conn.disconnect();}
        });
    }
    static void clear() { CACHE.evictAll(); }
}
