package xyz.etkn.tv;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Shared server wallpaper, decoded and blurred off the UI thread on API 24+. */
final class AppearanceBackground extends FrameLayout {
    private final TvActivity host;
    private final ImageView image,loader;
    private String key="";
    private int ticket;
    AppearanceBackground(TvActivity host){
        super(host);this.host=host;setTag("server-background");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        image=new ImageView(host);image.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(image,new LayoutParams(-1,-1));
        loader=new ImageView(host);loader.setVisibility(INVISIBLE);addView(loader,new LayoutParams(0,0));update();
    }
    void update(){
        setBackgroundColor(TvAppearance.background(host.appearanceDark()));
        String path=TvAppearance.current.optString("background_url");int blur=TvAppearance.current.optInt("background_blur",10);
        String next=path+":"+blur;if(next.equals(key))return;key=next;int request=++ticket;
        image.setImageDrawable(null);if(path.isEmpty()){loader.setTag(next);return;}
        Images.thumbnail(host.api,host.api.server+path,loader,1920,1080,success->{
            if(request!=ticket||!success||!(loader.getDrawable() instanceof BitmapDrawable))return;
            Bitmap source=((BitmapDrawable)loader.getDrawable()).getBitmap();
            TvActivity.IO.execute(()->{Bitmap result=BackdropBlur.create(source,blur);post(()->{if(request==ticket&&!host.isDestroyed())image.setImageBitmap(result);});});
        });
    }
}
