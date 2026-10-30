package xyz.etkn.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import org.junit.Test;
import static org.junit.Assert.*;

/** 使用实际 Canvas 像素检查边框流光，不依赖网络或媒体内容。 */
public class FocusBorderTest {
    @Test public void greenCyanBorderMovesAndStopsWhenHidden(){
        FocusPulse border=new FocusPulse(18,4,Color.rgb(118,213,176));
        int[] callbacks=new int[2];
        border.setCallback(new Drawable.Callback(){
            public void invalidateDrawable(Drawable drawable){}
            public void scheduleDrawable(Drawable drawable,Runnable frame,long when){callbacks[0]++;}
            public void unscheduleDrawable(Drawable drawable,Runnable frame){callbacks[1]++;}
        });
        border.setBounds(8,8,392,232);
        Bitmap first=Bitmap.createBitmap(400,240,Bitmap.Config.ARGB_8888),second=Bitmap.createBitmap(400,240,Bitmap.Config.ARGB_8888);
        try{
            border.draw(new Canvas(first));SystemClock.sleep(600);border.draw(new Canvas(second));
            int cyan=0,changed=0;
            for(int y=0;y<240;y++)for(int x=0;x<400;x++){
                int a=first.getPixel(x,y),b=second.getPixel(x,y);
                if(Color.alpha(a)>200&&Color.blue(a)>Color.green(a)+10&&Color.blue(a)>Color.red(a)+80&&Color.green(a)>150)cyan++;
                if(a!=b)changed++;
            }
            assertTrue("边框须有明显青蓝色流光",cyan>50);
            assertTrue("流光须随时间移动",changed>200);
            assertEquals("只绘制边框，卡片内容不染色",0,Color.alpha(first.getPixel(200,120)));
            border.setVisible(false,false);assertTrue("隐藏时取消后续帧",callbacks[1]>0);
            int scheduled=callbacks[0];border.draw(new Canvas(second));assertEquals(scheduled,callbacks[0]);
        }finally{first.recycle();second.recycle();}
    }
}
