package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** 焦点卡片滚动容器：焦点定位时把目标矩形按缩放余量外扩，避免放大后的卡片被视口边缘裁切。 */
public final class FocusScroll {
    /** 焦点缩放单侧最大溢出的预留余量；必须覆盖 TvActivity.FOCUS_SCALE 对全部卡片尺寸的溢出。 */
    static final int MARGIN_DP = 12;
    static int margin(Context context) { return Math.round(context.getResources().getDisplayMetrics().density * MARGIN_DP); }

    public static final class Vertical extends ScrollView {
        public Vertical(Context context) { super(context); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rectangle) {
            return super.computeScrollDeltaToGetChildRectOnScreen(expand(rectangle, margin(getContext())));
        }
    }

    public static final class Row extends HorizontalScrollView {
        public Row(Context context) {
            super(context);
            // 片架本身也要允许越界绘制；只关闭 GridLayout 的子裁剪仍会被本层裁掉。
            setClipChildren(false);
        }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rectangle) {
            return super.computeScrollDeltaToGetChildRectOnScreen(expand(rectangle, margin(getContext())));
        }
    }

    private static Rect expand(Rect rectangle, int margin) {
        if (rectangle == null) return null;
        Rect expanded = new Rect(rectangle);
        expanded.inset(-margin, -margin);
        return expanded;
    }
}
