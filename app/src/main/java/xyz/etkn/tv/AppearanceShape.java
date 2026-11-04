package xyz.etkn.tv;

import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;

/** Re-resolves semantic colors without replacing focused views or their state. */
final class AppearanceShape extends GradientDrawable {
    private final int fill,border;
    private final boolean forceDark;
    private int lastFill,lastBorder;
    AppearanceShape(int fill,int border,boolean forceDark){this.fill=fill;this.border=border;this.forceDark=forceDark;setCornerRadius(12*android.content.res.Resources.getSystem().getDisplayMetrics().density);refresh();}
    private void refresh(){int color=TvAppearance.resolve(fill,forceDark),stroke=TvAppearance.resolve(border,forceDark);if(getColor()==null||lastFill!=color){lastFill=color;setColor(color);}if(border!=0&&lastBorder!=stroke){lastBorder=stroke;setStroke(3,stroke);}}
    @Override public void draw(Canvas canvas){refresh();super.draw(canvas);}
    // GradientDrawable's ConstantState recreates a plain drawable and loses live colors.
    @Override public ConstantState getConstantState(){return null;}
}
