package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.TextureView;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** Own the aspect ratio as well as the texture transform for unrotated decoder output. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class RotatingPlayerView extends PlayerView {
    public RotatingPlayerView(Context context,AttributeSet attrs){super(context,attrs);}
    @Override protected void onContentAspectRatioChanged(AspectRatioFrameLayout frame,float ratio){
        if(getPlayer() instanceof ExoPlayer){
            ExoPlayer player=(ExoPlayer)getPlayer();Format format=player.getVideoFormat();VideoSize size=player.getVideoSize();
            if(size.width>0&&size.height>0){
                ratio=size.width*(format==null?size.pixelWidthHeightRatio:format.pixelWidthHeightRatio)/size.height;
                if(format!=null&&(format.rotationDegrees==90||format.rotationDegrees==270))ratio=1f/ratio;
            }
        }
        super.onContentAspectRatioChanged(frame,ratio);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);updateRotation();
    }
    void updateRotation(){
        if(!(getPlayer() instanceof ExoPlayer))return;
        Format format=((ExoPlayer)getPlayer()).getVideoFormat();
        onContentAspectRatioChanged(findViewById(androidx.media3.ui.R.id.exo_content_frame),0);
        TextureView surface=(TextureView)getVideoSurfaceView();int w=surface.getWidth(),h=surface.getHeight();
        Matrix transform=new Matrix();int rotation=format==null?0:format.rotationDegrees;
        if(rotation!=0&&w>0&&h>0){
            float x=w/2f,y=h/2f;transform.postRotate(rotation,x,y);
            RectF bounds=new RectF(0,0,w,h);transform.mapRect(bounds);
            transform.postScale(w/bounds.width(),h/bounds.height(),x,y);
        }
        surface.setTransform(transform);
    }
}
