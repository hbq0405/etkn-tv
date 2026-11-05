package xyz.etkn.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PixelTimeBarTest {
    @Test public void bufferFollowsThemeAndBothBarsRepaintWithoutLosingProgress(){
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            JSONObject previous=TvAppearance.current;
            try{
                PixelTimeBar[] bars={new PixelTimeBar(instrumentation.getTargetContext()),new PixelTimeBar(instrumentation.getTargetContext())};
                float density=instrumentation.getTargetContext().getResources().getDisplayMetrics().density;
                int height=Math.round(52*density);
                for(PixelTimeBar bar:bars){bar.layout(0,0,720,height);bar.setDuration(10000);bar.setPosition(3000);bar.setBufferedPosition(7000);}
                for(String theme:new String[]{"#e5484d","#4389ea","#9563db","#55c7b0","#f2d668","#ffffff","#000000","#3c087d"}){
                    TvAppearance.current=new JSONObject().put("mode","dark").put("theme_color",theme);
                    int played=PixelTimeBar.playedColor(),buffer=PixelTimeBar.bufferedColor(),unplayed=PixelTimeBar.unplayedColor();
                    assertEquals(Color.parseColor(theme),played);
                    assertTrue(theme+" 已播放和预缓存应有明暗差",ColorUtils.calculateContrast(played,buffer)>=1.65);
                    assertTrue(theme+" 预缓存和未播放应可区分",ColorUtils.calculateContrast(buffer,unplayed)>=1.5);
                    if(theme.equals("#e5484d"))assertEquals("红主题应使用柔红",TvAppearance.mix(played,Color.WHITE,.65f),buffer);
                    if(theme.equals("#ffffff"))assertTrue("浅色主题使用较深的预缓存色",ColorUtils.calculateLuminance(buffer)<ColorUtils.calculateLuminance(played));
                    assertEquals(played,PixelTimeBar.stateColor(1000,3000,7000));
                    assertEquals(buffer,PixelTimeBar.stateColor(5000,3000,7000));
                    assertEquals(unplayed,PixelTimeBar.stateColor(9000,3000,7000));
                    assertEquals(unplayed,PixelTimeBar.stateColor(5000,3000,1000));
                    for(PixelTimeBar bar:bars){
                        Bitmap bitmap=Bitmap.createBitmap(720,height,Bitmap.Config.ARGB_8888);
                        try{
                            bar.draw(new Canvas(bitmap));
                            int left=Math.round(6*density),count=Math.max(1,(int)((720-12*density)/(8*density)));
                            float cell=(720-12*density)/count;
                            int[] colors={played,buffer,unplayed};float[] positions={.15f,.5f,.85f};
                            for(int i=0;i<colors.length;i++){
                                int x=Math.round(left+((int)(count*positions[i])+.25f)*cell),y=height-Math.round(16*density);
                                int actual=bitmap.getPixel(x,y),expected=colors[i];
                                // The middle of each cell is shaded to about five sixths of its top color.
                                assertEquals(theme+" 绘制红通道",Color.red(expected)*5/6,Color.red(actual),3);
                                assertEquals(theme+" 绘制绿通道",Color.green(expected)*5/6,Color.green(actual),3);
                                assertEquals(theme+" 绘制蓝通道",Color.blue(expected)*5/6,Color.blue(actual),3);
                            }
                        }finally{bitmap.recycle();}
                    }
                }
            }catch(Exception error){throw new AssertionError(error);}finally{TvAppearance.current=previous;}
        });
    }
}
