package xyz.etkn.tv;

import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;

final class AppearanceCapsule extends GradientDrawable {
    private int primary,fill;
    private final int stroke;
    AppearanceCapsule(float radius){stroke=Math.max(1,Math.round(2*android.content.res.Resources.getSystem().getDisplayMetrics().density));setCornerRadius(radius);update();}
    private void update(){int color=TvAppearance.primary(),surface=TvAppearance.translucent(TvAppearance.surface());if(color!=primary||surface!=fill||getColor()==null){primary=color;fill=surface;setColor(surface);setStroke(stroke,color);}}
    @Override public void draw(Canvas canvas){update();super.draw(canvas);}
}
