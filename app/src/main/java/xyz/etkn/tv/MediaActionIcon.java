package xyz.etkn.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Shared 24-unit geometry keeps detail actions the same size and weight. */
final class MediaActionIcon extends Drawable {
    private final String name;
    private final boolean active; private int alpha=255;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    MediaActionIcon(String name,boolean active){this.name=name;this.active=active;paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){
        paint.setColor(active?(name.equals("favorite")?Color.rgb(245,65,85):TvAppearance.accent(true)):TvAppearance.text(true));paint.setAlpha(alpha);int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setStyle(Paint.Style.STROKE);
        String path=switch(name){
            case "play","resume"->"M8 5 L19 12 L8 19 Z";
            case "restart"->"M4 10 A8 8 0 1 1 5 18 M4 4 V10 H10 M10 8 L16 12 L10 16 Z";
            case "favorite"->"M12 21 L4 13 C-2 7 6 0 12 7 C18 0 26 7 20 13 Z";
            case "played"->"M8 12 L11 15 L17 9";
            case "refresh"->"M20 9 A8 8 0 0 0 6 6 L3 9 M3 4 V9 H8 M4 15 A8 8 0 0 0 18 18 L21 15 M16 15 H21 V20";
            case "search"->"M16 10 A6 6 0 1 1 4 10 A6 6 0 1 1 16 10 M14.5 14.5 L21 21";
            default->"M4 6 H20 M9 6 V3 H15 V6 M6 6 L7 21 H17 L18 6 M10 10 V17 M14 10 V17";
        };
        if(name.equals("play")||name.equals("resume")||name.equals("favorite")&&active)paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(path),paint);
        paint.setStyle(Paint.Style.STROKE);
        if(name.equals("resume"))canvas.drawLine(4,6,4,18,paint);
        if(name.equals("played"))canvas.drawCircle(12,12,9,paint);
        canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
