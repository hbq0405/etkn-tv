package xyz.etkn.tv;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FocusScrollGeometryTest {
    // 卡片尺寸须与 TvActivity.mediaCard 保持一致：竖版 132x248，横版 264x215。
    private static final int[][] CARD_SIZES = {{132, 248}, {264, 215}};

    @Test public void focusScaleHaloFitsInsideScrollMargin() {
        for (int[] size : CARD_SIZES) {
            float haloWidth = size[0] * (TvActivity.FOCUS_SCALE - 1f) / 2f;
            float haloHeight = size[1] * (TvActivity.FOCUS_SCALE - 1f) / 2f;
            assertTrue("宽 " + size[0] + "dp 卡片缩放溢出 " + haloWidth + "dp 超出余量", haloWidth <= FocusScroll.MARGIN_DP);
            assertTrue("高 " + size[1] + "dp 卡片缩放溢出 " + haloHeight + "dp 超出余量", haloHeight <= FocusScroll.MARGIN_DP);
        }
    }

    @Test public void marginStaysPositiveInDp() {
        assertTrue(FocusScroll.MARGIN_DP > 0);
    }
}
