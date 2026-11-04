package xyz.etkn.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Hardware screenshots exercise shadow composition; no server or real account is used. */
public class AppearanceShadowTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private SetupActivity host;
    private FrameLayout stage;
    private final View[] surfaces=new View[3];
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private void settle(){instrumentation.waitForIdleSync();SystemClock.sleep(180);instrumentation.waitForIdleSync();}
    private void open(){
        instrumentation.getTargetContext().getSharedPreferences("session",0).edit().clear().commit();SessionStore.endTemporarySession();
        host=(SetupActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),SetupActivity.class).putExtra(SetupActivity.EXTRA_ADD_ACCOUNT,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{
            stage=new FrameLayout(host);stage.setClipChildren(false);stage.setClipToPadding(false);host.getWindow().setContentView(stage);
            surfaces[0]=new FrameLayout(host);surfaces[1]=host.button("",()->{});surfaces[2]=new FrameLayout(host);surfaces[2].setClipToOutline(true);
            for(int i=0;i<surfaces.length;i++){View view=surfaces[i];view.setFocusable(false);view.setBackground(host.shape(TvActivity.PANEL,0));FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(host.dp(170),host.dp(84));bounds.leftMargin=host.dp(90+i*250);bounds.topMargin=host.dp(140);stage.addView(view,bounds);}
        });settle();
    }
    private void appearance(boolean dark,double opacity,double strength)throws Exception{
        TvAppearance.current=new JSONObject().put("mode",dark?"dark":"light").put("glass_opacity",opacity).put("shadow_strength",strength);
        main(()->{stage.setBackgroundColor(dark?0xff394c45:0xffe1ece7);for(View view:surfaces){host.shadow(view,8);view.invalidate();}});settle();
    }
    private Bitmap screenshot(String name)throws Exception{
        // A fresh hardware frame may still be rendering after the main thread becomes idle.
        SystemClock.sleep(1000);Bitmap frame=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(frame);
        File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"outer-shadow");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name+".png"))){frame.compress(Bitmap.CompressFormat.PNG,100,stream);}return frame;
    }
    private int[] location(View view){int[] position=new int[2];main(()->view.getLocationOnScreen(position));return position;}
    private int difference(int first,int second){return Math.max(Math.abs(Color.red(first)-Color.red(second)),Math.max(Math.abs(Color.green(first)-Color.green(second)),Math.abs(Color.blue(first)-Color.blue(second))));}
    @Test public void transparentSurfacesHaveShadowsOutsideOnly()throws Exception{
        open();try{
            for(boolean dark:new boolean[]{false,true})for(double opacity:new double[]{0,.55}){
                appearance(dark,opacity,0);Bitmap without=screenshot("surface-"+dark+"-"+opacity+"-off");
                appearance(dark,opacity,1);Bitmap with=screenshot("surface-"+dark+"-"+opacity+"-on");
                try{for(View view:surfaces){int[] p=location(view);int y=p[1]+view.getHeight()/2,inside=p[0]+host.dp(3),outside=p[0]-host.dp(4);
                    android.util.Log.i("AppearanceShadow","inside="+difference(without.getPixel(inside,y),with.getPixel(inside,y))+" outside="+difference(without.getPixel(outside,y),with.getPixel(outside,y)));
                    for(int x=inside;x<p[0]+view.getWidth()-host.dp(3);x++)assertEquals("阴影不能改变透明或半透明控件内部像素",without.getPixel(x,y),with.getPixel(x,y));
                    assertTrue("透明控件外侧仍须有阴影",difference(without.getPixel(outside,y),with.getPixel(outside,y))>=2);
                }}finally{without.recycle();with.recycle();}
            }
        }finally{main(host::finish);TvAppearance.current=new JSONObject();}
    }
    @Test public void shadowsFollowTransformsClippingAndDetach()throws Exception{
        open();try{
            FrameLayout viewport=new FrameLayout(host);View target=surfaces[0];
            main(()->{stage.removeView(target);viewport.setClipChildren(true);viewport.setClipToPadding(false);FrameLayout.LayoutParams area=new FrameLayout.LayoutParams(host.dp(220),host.dp(150));area.leftMargin=host.dp(80);area.topMargin=host.dp(80);stage.addView(viewport,area);FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(host.dp(170),host.dp(84));bounds.leftMargin=host.dp(65);bounds.topMargin=host.dp(32);viewport.addView(target,bounds);viewport.setScaleX(1.08f);viewport.setScaleY(1.08f);target.setAlpha(.5f);stage.scrollTo(0,host.dp(20));});
            appearance(false,0,0);Bitmap without=screenshot("transformed-off");appearance(false,0,1);Bitmap with=screenshot("transformed-on");
            int[] p=location(target),v=location(viewport);int y=p[1]+Math.round(target.getHeight()*1.08f/2),left=p[0]-host.dp(4),outside=v[0]+Math.round(viewport.getWidth()*1.08f)+host.dp(2);
            try{assertEquals("移动和缩放后内部仍全透",without.getPixel(p[0]+host.dp(4),y),with.getPixel(p[0]+host.dp(4),y));assertTrue("阴影跟随滚动和祖先缩放",difference(without.getPixel(left,y),with.getPixel(left,y))>=2);assertEquals("阴影不能越过父级裁剪区域",without.getPixel(outside,y),with.getPixel(outside,y));}finally{without.recycle();with.recycle();}
            main(()->viewport.removeView(target));settle();Bitmap detached=screenshot("detached");try{assertEquals("移除控件时必须移除阴影",0xffe1ece7,detached.getPixel(left,y));}finally{detached.recycle();}
        }finally{main(host::finish);TvAppearance.current=new JSONObject();}
    }
}