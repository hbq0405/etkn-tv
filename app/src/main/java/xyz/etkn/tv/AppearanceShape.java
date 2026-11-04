package xyz.etkn.tv;

import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;

/** Re-resolves semantic colors without replacing focused views or their state. */
final class AppearanceShape extends GradientDrawable {
    private final int fill,border;
    private int lastFill,lastBorder;
    AppearanceShape(int fill,int border){this.fill=fill;this.border=border;setCornerRadius(12*android.content.res.Resources.getSystem().getDisplayMetrics().density);refresh();}
    private void refresh(){int color=TvAppearance.resolve(fill),stroke=TvAppearance.resolve(border);if(getColor()==null||lastFill!=color){lastFill=color;setColor(color);}if(border!=0&&lastBorder!=stroke){lastBorder=stroke;setStroke(3,stroke);}}
    @Override public void draw(Canvas canvas){refresh();super.draw(canvas);}
}
