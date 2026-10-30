package xyz.etkn.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Green/cyan light travels around the focus border; hidden or detached borders stop drawing frames. */
final class FocusPulse extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectangle=new RectF();
    private final Matrix rotation=new Matrix();
    private final int color;
    private final float radius,width;
    private final Runnable frame=this::invalidateSelf;
    private long started;
    private int alpha=255;
    FocusPulse(float radius,float width,int color){this.radius=radius;this.width=width;this.color=color;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);}
    @Override protected void onBoundsChange(Rect bounds){
        rectangle.set(bounds);rectangle.inset(width/2,width/2);
        paint.setShader(new SweepGradient(rectangle.centerX(),rectangle.centerY(),new int[]{color,Color.rgb(53,207,255),Color.rgb(210,255,255),Color.rgb(53,207,255),color},null));
    }
    @Override public void draw(Canvas canvas){
        long now=SystemClock.uptimeMillis();if(started==0)started=now;
        paint.setAlpha(alpha);rotation.setRotate((now-started)%2400*360f/2400,rectangle.centerX(),rectangle.centerY());
        if(paint.getShader()!=null)paint.getShader().setLocalMatrix(rotation);
        canvas.drawRoundRect(rectangle,radius,radius,paint);
        if(isVisible()&&getCallback()!=null)scheduleSelf(frame,now+40);
    }
    @Override public boolean setVisible(boolean visible,boolean restart){if(!visible){unscheduleSelf(frame);started=0;}return super.setVisible(visible,restart);}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
