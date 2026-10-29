package xyz.etkn.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Only the focus border pulses; frames stop when the drawable is hidden or no longer drawn. */
final class FocusPulse extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectangle=new RectF();
    private final float radius,width;
    private final Runnable frame=this::invalidateSelf;
    private long started;
    private int alpha=255;
    FocusPulse(float radius,float width,int color){this.radius=radius;this.width=width;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);}
    @Override public void draw(Canvas canvas){
        long now=SystemClock.uptimeMillis();if(started==0)started=now;
        paint.setAlpha(Math.round(alpha*(0.68f+0.32f*(float)(1+Math.cos((now-started)*Math.PI/1200))/2)));
        rectangle.set(getBounds());rectangle.inset(width/2,width/2);canvas.drawRoundRect(rectangle,radius,radius,paint);
        if(isVisible()&&getCallback()!=null)scheduleSelf(frame,now+40);
    }
    @Override public boolean setVisible(boolean visible,boolean restart){if(!visible){unscheduleSelf(frame);started=0;}return super.setVisible(visible,restart);}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
