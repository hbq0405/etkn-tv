package xyz.etkn.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class CardGeometryTest {
    @Test public void partialShelfCardCountsAndLargerCardsNeedFewerItems(){
        assertEquals(6,CardGeometry.shelfCapacity(1772,312));
        assertEquals(4,CardGeometry.shelfCapacity(1772,552));
    }
    @Test public void wallUsesAspectCaptionAndBothRowMargins(){
        assertEquals(24,CardGeometry.capacity(1760,800,8,12,1.5f,64,12));
        assertEquals(56,CardGeometry.capacity(1760,800,8,24,9f/16f,0,12));
        assertEquals(16,CardGeometry.capacity(1760,800,8,12,1.5f,200,12));
        assertEquals(16,CardGeometry.capacity(1760,800,8,12,1.5f,64,70));
    }
    @Test public void densityScalingPreservesCapacityAndViewportHeightChangesIt(){
        assertEquals(CardGeometry.capacity(880,400,8,6,1.5f,32,6),CardGeometry.capacity(1760,800,8,12,1.5f,64,12));
        assertEquals(8,CardGeometry.capacity(880,140,8,6,1.5f,32,6));
        assertEquals(32,CardGeometry.capacity(880,700,8,6,1.5f,32,6));
    }
    @Test public void allFiveLevelsKeepTheExistingColumnOffsetsAndSmallWindowsStayValid(){
        for(int size=0;size<5;size++)assertEquals(8-size,CardGeometry.columns(880,132,6,size));
        assertEquals(1,CardGeometry.columns(30,192,12,4));
        assertEquals(1,CardGeometry.capacity(1,0,1,12,1.5f,0,6));
    }
}
