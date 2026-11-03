package xyz.etkn.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 在真实首页和 Android 焦点滚动路径上验收；只连接模拟器内的合成媒体服务。 */
public class HomeFocusTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    @org.junit.Before public void clearPreviousLoopbackFixture(){if(new SessionStore(instrumentation.getTargetContext()).server().startsWith("http://127.0.0.1:")){instrumentation.getTargetContext().getSharedPreferences("session",0).edit().clear().commit();SessionStore.endTemporarySession();}}
    private HomeActivity home;
    private File evidence;
    private void await(BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){boolean[] ready={false};instrumentation.runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0]){settle();return;}Thread.sleep(100);}fail("等待媒体库状态超时");}
    @SuppressWarnings("unchecked") private View library(String id){return ((java.util.Map<String,View>)field(HomeActivity.class,"libraryCards")).get(id);}
    private void call(String name,Class<?>[] types,Object... args){instrumentation.runOnMainSync(()->{try{java.lang.reflect.Method method=HomeActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(home,args);}catch(Exception error){throw new AssertionError(error);}});}
    private int expectedAdaptiveColumns(GridLayout grid,int cardWidthDp,int gapDp,int imageSize){float density=home.getResources().getDisplayMetrics().density;int availableDp=Math.max(1,Math.round((grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight())/density));int medium=Math.max(1,(availableDp+2*gapDp)/(cardWidthDp+2*gapDp));return Math.max(1,medium+TvPreferences.IMAGE_MEDIUM-imageSize);}

    @Test public void adaptiveRequestsKeepCardSizesAndBannerMetadataInsideArtwork()throws Exception{
        try(Fixture server=new Fixture()){
            server.respectLimits=true;
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"adaptive-fixture","动态请求测试","loopback-only");
            EtknApi api=new EtknApi(instrumentation.getTargetContext());TvPreferences preferences=new TvPreferences(instrumentation.getTargetContext(),api);preferences.setImageSize(TvPreferences.IMAGE_MEDIUM);preferences.saveLibraryView("library-0","portrait");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"adaptive-media");assertTrue(evidence.isDirectory()||evidence.mkdirs());
            await(()->library("library-0")!=null&&field(HomeActivity.class,"resumeFirst")!=null);
            int previous=0;
            for(int size=0;size<5;size++){
                final int level=size;instrumentation.runOnMainSync(()->{preferences.setImageSize(level);home.onResume();});
                int width=home.getResources().getDisplayMetrics().widthPixels-home.dp(74),gap=home.imageDp(12);
                int resume=(int)Math.ceil((double)width/(home.imageDp(192)+2*gap))+1,latest=(int)Math.ceil((double)width/(home.imageDp(132)+2*gap))+1;
                await(()->cards().size()==6+resume+6*latest&&server.lastResumePath.contains("Limit="+resume));
                final View first=(View)field(HomeActivity.class,"resumeFirst");assertEquals("五档卡片宽度保持原样",home.imageDp(192),first.getWidth());
                assertTrue(server.latestPaths.get(server.latestPaths.size()-1).contains("Limit="+latest));
                if(size>0)assertTrue("大图请求量不应增加",resume<=previous);previous=resume;
            }
            instrumentation.runOnMainSync(()->{preferences.setImageSize(TvPreferences.IMAGE_EXTREMELY_SMALL);home.onResume();});
            await(()->library("library-0")!=null&&library("library-0").getWidth()>0);
            instrumentation.runOnMainSync(()->library("library-0").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()>0&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            int firstCount=posterGrid().getChildCount(),limit=Integer.parseInt(android.net.Uri.parse(server.lastListingPath).getQueryParameter("Limit"));assertEquals(limit,firstCount);assertTrue("不再固定拉取60项",limit<60);
            call("loadPosterPage",new Class<?>[]{boolean.class},false);await(()->posterGrid().getChildCount()>firstCount&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            assertEquals(String.valueOf(firstCount),android.net.Uri.parse(server.lastListingPath).getQueryParameter("StartIndex"));
            java.util.Set<String> ids=new java.util.HashSet<>();for(int i=0;i<posterGrid().getChildCount();i++)assertTrue("追加分页不能重复",ids.add(((JSONObject)posterGrid().getChildAt(i).getTag()).optString("Id")));
            int loaded=posterGrid().getChildCount();View focus=posterGrid().getChildAt(1);instrumentation.runOnMainSync(focus::requestFocus);call("refreshPosters",new Class<?>[]{});await(()->!Boolean.TRUE.equals(field(HomeActivity.class,"refreshingPosters")));assertEquals(loaded,posterGrid().getChildCount());assertSame(focus,home.getCurrentFocus());
            server.styledBannerTitles=true;server.imagePaths.clear();call("toggleView",new Class<?>[]{});await(()->"landscape".equals(field(HomeActivity.class,"viewMode"))&&posterGrid().getChildCount()>0&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            instrumentation.runOnMainSync(()->{
                ViewGroup card=(ViewGroup)posterGrid().getChildAt(0);assertEquals("横幅下方不再保留片名年份",1,card.getChildCount());
                ViewGroup art=(ViewGroup)card.getChildAt(0),ratings=(ViewGroup)art.getChildAt(1),identity=(ViewGroup)art.getChildAt(2);
                assertEquals("2020",((android.widget.TextView)ratings.getChildAt(0)).getText().toString());assertTrue(ratings.getChildAt(1) instanceof android.widget.ImageView);
                assertEquals(Math.round(art.getWidth()*9f/16f),art.getHeight());assertTrue(identity.getTop()<ratings.getTop());
            });
            float[] previousMetrics=null;
            for(int size=0;size<5;size++){
                final int level=size,requestedWidth=size==TvPreferences.IMAGE_EXTREMELY_LARGE?720:360;
                instrumentation.runOnMainSync(()->{preferences.setImageSize(level);home.onResume();});
                await(()->posterGrid().getColumnCount()==expectedAdaptiveColumns(posterGrid(),192,12,level)&&!Boolean.TRUE.equals(field(HomeActivity.class,"refreshingPosters")));
                await(()->{ViewGroup art=(ViewGroup)((ViewGroup)posterGrid().getChildAt(0)).getChildAt(0);return String.valueOf(art.getChildAt(0).getTag()).contains("maxWidth="+requestedWidth+"&")&&((android.widget.ImageView)art.getChildAt(0)).getDrawable()!=null;});
                float[] metrics=new float[13];
                instrumentation.runOnMainSync(()->{
                    for(int i=0;i<Math.min(4,posterGrid().getChildCount());i++){
                        ViewGroup art=(ViewGroup)((ViewGroup)posterGrid().getChildAt(i)).getChildAt(0),identity=(ViewGroup)art.getChildAt(2);
                        assertEquals("横幅仅保留文字片名",1,identity.getChildCount());
                        android.widget.TextView title=(android.widget.TextView)identity.getChildAt(0);
                        assertTrue("文字无需等待图片响应",title.isShown());assertEquals(View.VISIBLE,title.getVisibility());
                        assertEquals(2,title.getMaxLines());assertTrue(title.getLineCount()<=2);assertTrue(title.getPaint().getShadowLayerRadius()>0);
                        assertEquals(androidx.core.content.res.ResourcesCompat.getFont(home,R.font.smiley_sans),title.getTypeface());
                        assertTrue("片名靠左",identity.getPaddingLeft()<art.getWidth()/10);assertTrue(title.getLeft()<art.getWidth()/10);
                        assertTrue("片名区域覆盖完整行高",title.getLayout().getHeight()<=title.getHeight());
                        if(art.getChildCount()>3)assertTrue("长片名不能与集数角标重叠",title.getRight()<=art.getChildAt(3).getLeft());
                        if(i==0){
                            ViewGroup scores=(ViewGroup)art.getChildAt(1);android.widget.TextView year=(android.widget.TextView)scores.getChildAt(0),score=(android.widget.TextView)scores.getChildAt(2);
                            android.widget.TextView badgeText=(android.widget.TextView)art.getChildAt(3);
                            metrics[0]=title.getTextSize();metrics[1]=year.getTextSize();metrics[2]=score.getTextSize();metrics[3]=scores.getChildAt(1).getWidth();metrics[4]=scores.getHeight();metrics[5]=year.getPaddingRight();metrics[6]=((android.widget.LinearLayout.LayoutParams)score.getLayoutParams()).leftMargin;
                            metrics[7]=badgeText.getTextSize();metrics[8]=badgeText.getWidth();metrics[9]=badgeText.getHeight();metrics[10]=badgeText.getPaddingLeft();metrics[11]=((android.widget.FrameLayout.LayoutParams)badgeText.getLayoutParams()).rightMargin;metrics[12]=((android.widget.FrameLayout.LayoutParams)badgeText.getLayoutParams()).topMargin;
                        }
                    }
                });
                if(previousMetrics!=null){
                    assertTrue("卡片变大时片名随之增大",metrics[0]>previousMetrics[0]);
                    assertTrue("年份字号必须随卡片变大",metrics[1]>previousMetrics[1]);
                    float scale=metrics[0]/previousMetrics[0];for(int part=1;part<metrics.length;part++)assertEquals("横幅缩放指标 "+part+" 必须与片名等比例变化",previousMetrics[part]*scale,metrics[part],part<=2?0.05f:part==8?5.0f:1.5f);
                }
                previousMetrics=metrics;
                if(size==0||size==2||size==4)saveScreenshot("banner-title-size-"+size+".png");
            }
            assertTrue(server.imagePaths.stream().anyMatch(path->path.contains("/Items/item-0/Images/Backdrop?")&&"360".equals(android.net.Uri.parse(path).getQueryParameter("maxWidth"))));
            assertTrue(server.imagePaths.stream().anyMatch(path->path.contains("/Items/item-0/Images/Backdrop?")&&"720".equals(android.net.Uri.parse(path).getQueryParameter("maxWidth"))));
            assertFalse("媒体库横幅不请求Logo",server.imagePaths.stream().anyMatch(path->path.contains("/Images/Logo")));
            call("toggleView",new Class<?>[]{});await(()->"portrait".equals(field(HomeActivity.class,"viewMode"))&&posterGrid().getChildCount()>0&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            float[] previousPortraitMetrics=null;
            for(int size=0;size<5;size++){
                final int level=size;instrumentation.runOnMainSync(()->{preferences.setImageSize(level);home.onResume();});
                await(()->posterGrid().getColumnCount()==expectedAdaptiveColumns(posterGrid(),132,6,level)&&posterGrid().getChildCount()>0&&!Boolean.TRUE.equals(field(HomeActivity.class,"refreshingPosters")));
                await(()->posterGrid().getChildAt(0).getWidth()>0&&((ViewGroup)posterGrid().getChildAt(0)).getChildAt(0).getWidth()>0);
                float[] portraitMetrics=new float[15];
                instrumentation.runOnMainSync(()->{
                    ViewGroup card=(ViewGroup)posterGrid().getChildAt(0);ViewGroup art=(ViewGroup)card.getChildAt(0);assertEquals("竖幅保持海报比例",Math.round(art.getWidth()*1.5f),art.getHeight());
                    android.widget.TextView title=(android.widget.TextView)card.getChildAt(1),year=(android.widget.TextView)card.getChildAt(2);ViewGroup scores=(ViewGroup)art.getChildAt(1);android.widget.TextView rating=(android.widget.TextView)scores.getChildAt(1),badgeText=(android.widget.TextView)art.getChildAt(2);
                    portraitMetrics[0]=title.getTextSize();portraitMetrics[1]=title.getHeight();portraitMetrics[2]=title.getPaddingTop();portraitMetrics[3]=year.getTextSize();portraitMetrics[4]=year.getHeight();portraitMetrics[5]=rating.getTextSize();portraitMetrics[6]=scores.getChildAt(0).getWidth();portraitMetrics[7]=scores.getHeight();portraitMetrics[8]=((android.widget.LinearLayout.LayoutParams)rating.getLayoutParams()).leftMargin;portraitMetrics[9]=badgeText.getTextSize();portraitMetrics[10]=badgeText.getWidth();portraitMetrics[11]=badgeText.getHeight();portraitMetrics[12]=badgeText.getPaddingLeft();portraitMetrics[13]=((android.widget.FrameLayout.LayoutParams)badgeText.getLayoutParams()).rightMargin;portraitMetrics[14]=((android.widget.FrameLayout.LayoutParams)badgeText.getLayoutParams()).topMargin;
                });
                if(previousPortraitMetrics!=null){assertTrue("竖幅卡片变大时片名随之增大",portraitMetrics[0]>previousPortraitMetrics[0]);float scale=portraitMetrics[0]/previousPortraitMetrics[0];for(int part=1;part<portraitMetrics.length;part++)assertEquals("竖幅缩放指标 "+part+" 必须与片名等比例变化",previousPortraitMetrics[part]*scale,portraitMetrics[part],part==0||part==3||part==5||part==9?0.05f:part==10?5.0f:2.0f);}
                previousPortraitMetrics=portraitMetrics;
                if(size==0||size==2||size==4)saveScreenshot("poster-size-"+size+".png");
            }
        }finally{closePosterFixture();}
    }

    @Test public void shortPosterPagesFillViewportAndStationaryBottomFocusKeepsLoading()throws Exception{
        try(Fixture server=new Fixture()){
            server.respectLimits=true;server.listingPageCap=6;
            openMenuHomeFixture(server);
            TvPreferences preferences=new TvPreferences(home,home.api);preferences.setImageSize(TvPreferences.IMAGE_MEDIUM);preferences.saveLibraryView("library-0","landscape");
            instrumentation.runOnMainSync(()->library("library-0").performClick());
            await(()->posterGrid()!=null&&posterGrid().getChildCount()>6&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            int loaded=posterGrid().getChildCount();assertTrue("首屏补齐不能全量拉取媒体库",loaded<120);
            ScrollView scroll=(ScrollView)field(HomeActivity.class,"homeScroll");
            server.holdNextListing=true;
            View last=posterGrid().getChildAt(loaded-1);
            instrumentation.runOnMainSync(()->{scroll.setOnScrollChangeListener(null);assertTrue(last.requestFocus());});
            assertTrue("没有滚动事件时末行焦点仍应触发分页",server.listingStarted.await(5,java.util.concurrent.TimeUnit.SECONDS));
            int requests=server.listingCalls;
            for(int i=0;i<3;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertEquals("请求在途时按下不能重复派发",requests,server.listingCalls);
            server.listingGate.countDown();
            await(()->posterGrid().getChildCount()>loaded&&!Boolean.TRUE.equals(field(HomeActivity.class,"loadingMore")));
            assertTrue(server.listingPaths.stream().anyMatch(path->String.valueOf(loaded).equals(android.net.Uri.parse(path).getQueryParameter("StartIndex"))));
            assertEquals("每个分页起点只能派发一次",server.listingPaths.size(),new java.util.HashSet<>(server.listingPaths).size());
            assertSame("追加页面保留原卡片",last,posterGrid().getChildAt(loaded-1));
            java.util.Set<String> ids=new java.util.HashSet<>();for(int i=0;i<posterGrid().getChildCount();i++)assertTrue(ids.add(((JSONObject)posterGrid().getChildAt(i).getTag()).optString("Id")));
        }finally{closePosterFixture();}
    }

    @Test public void continueWatchingClickOpensPlayerDirectly()throws Exception{
        PlayerActivity playback=null;
        Instrumentation.ActivityMonitor details=instrumentation.addMonitor(DetailActivity.class.getName(),null,true);
        Instrumentation.ActivityMonitor player=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"resume-fixture","直接续播测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->field(HomeActivity.class,"resumeFirst")!=null);
            assertSame("首页只应聚焦第一个继续观看",field(HomeActivity.class,"resumeFirst"),home.getCurrentFocus());
            assertEquals("用户确认前不能自动打开播放器",0,player.getHits());
            instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).performClick());
            playback=(PlayerActivity)player.waitForActivityWithTimeout(8000);assertNotNull(playback);assertEquals("item-0",playback.getIntent().getStringExtra("id"));assertTrue(playback.getIntent().getBooleanExtra("resume",false));assertEquals(0,details.getHits());
        }finally{instrumentation.removeMonitor(details);instrumentation.removeMonitor(player);if(playback!=null){PlayerActivity current=playback;instrumentation.runOnMainSync(current::finish);}if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void heroLoadsWhileCardImagesAreBlockedAndResumeSitsAtBottom()throws Exception{
        try(Fixture server=new Fixture()){
            server.holdCardImages=true;
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"hero-fixture","首屏测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            assertTrue("背景图不能排在卡片图片之后",server.heroLoaded.await(8,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("卡片请求仍被阻塞",1L,server.cardGate.getCount());
            await(()->field(HomeActivity.class,"resumeFirst")!=null);
            instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).requestFocus());settle();
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"home-performance");assertTrue(evidence.isDirectory()||evidence.mkdirs());saveScreenshot("compact-resume.png");
            await(()->library("library-0")!=null&&library("library-0").getWidth()>0);
            instrumentation.runOnMainSync(()->{
                View card=home.getCurrentFocus();assertEquals(home.dp(192),card.getWidth());assertEquals(home.dp(150),card.getHeight());
                View library=library("library-0");assertEquals("媒体库宽度与继续观看一致",card.getWidth(),library.getWidth());assertEquals("媒体库高度与继续观看一致",card.getHeight(),library.getHeight());
                assertEquals(home.dp(108),((ViewGroup)library).getChildAt(0).getHeight());
                View selectedArtwork=((ViewGroup)card).getChildAt(0),libraryArtwork=((ViewGroup)library).getChildAt(0);
                assertEquals(home.dp(8),selectedArtwork.getElevation(),0f);assertEquals(home.dp(4),libraryArtwork.getElevation(),0f);
                assertTrue(selectedArtwork.getClipToOutline());assertFalse(((ViewGroup)card).getClipChildren());
                for(View first:new View[]{card,library}){
                    View second=((ViewGroup)first.getParent()).getChildAt(1);
                    assertEquals("横向卡片实际间距",home.dp(24),second.getLeft()-first.getRight());
                }
                int[] location=new int[2];card.getLocationOnScreen(location);int bottom=Math.round(location[1]+card.getHeight()*card.getScaleY());
                int screen=home.getWindow().getDecorView().getHeight();
                assertTrue("聚焦卡片须完整保留在底部安全区，bottom="+bottom+" screen="+screen,bottom<=screen&&screen-bottom<=home.dp(24));
            });
            instrumentation.runOnMainSync(()->library("library-0").requestFocus());settle();saveScreenshot("compact-libraries.png");
            instrumentation.runOnMainSync(()->assertEquals(home.dp(8),((ViewGroup)library("library-0")).getChildAt(0).getElevation(),0f));
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void resumeAndCarouselDetailsFocusKeepBackdropSharpAndDetailsVisible()throws Exception{
        try(Fixture server=new Fixture()){
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"home-backdrop");assertTrue(evidence.isDirectory()||evidence.mkdirs());
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"blur-fixture","背景测试","synthetic-test-session");
            for(boolean carousel:new boolean[]{false,true}){
                new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"blur-fixture","背景测试","synthetic-test-session");
                new TvPreferences(instrumentation.getTargetContext(),new EtknApi(instrumentation.getTargetContext())).setSmartCarousel(carousel);
                home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                await(()->cards().size()==90&&home.getCurrentFocus()==field(HomeActivity.class,"resumeFirst"));settle();
                FocusBackdrop hero=(FocusBackdrop)field(HomeActivity.class,"homeBackdrop");
                android.widget.ImageView image=(android.widget.ImageView)hero.getChildAt(0);
                await(()->image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
                Bitmap sharp=((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();
                double sharpDetail=imageDetail(sharp);
                saveScreenshot("initial-state-"+carousel+".png");assertTrue("继续观看须显示简介 carousel="+carousel+" focus="+home.getCurrentFocus()+" window="+home.hasWindowFocus()+" finishing="+home.isFinishing()+" copy="+((View)backdropField(hero,"copy")).getVisibility()+" information="+((View)backdropField(hero,"information")).getVisibility()+" resume="+backdropField(hero,"resumeFocused"),shownText(hero,"背景简介"));
                assertEquals(carousel,hero.actionView().isShown());
                saveScreenshot("resume-clear-"+carousel+".png");
                instrumentation.runOnMainSync(()->library("library-0").requestFocus());settle();
                await(()->image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&imageDetail(((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap())<sharpDetail*0.6);
                assertFalse("媒体库聚焦时隐藏简介",shownText(hero,"背景简介"));
                assertFalse("离开继续观看时隐藏查看详情",hero.actionView().isShown());
                assertFalse("媒体库聚焦时隐藏年份等信息",shownText(hero,"2026"));
                saveScreenshot("library-blurred-"+carousel+".png");
                View latest=cards().stream().filter(card->!((java.util.List<?>)field(HomeActivity.class,"resumeCards")).contains(card)&&!((java.util.Map<?,?>)field(HomeActivity.class,"libraryCards")).containsValue(card)).findFirst().orElseThrow();
                instrumentation.runOnMainSync(latest::requestFocus);settle();assertFalse("最新片架聚焦时隐藏简介",shownText(hero,"背景简介"));
                await(()->image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&imageDetail(((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap())<sharpDetail*0.6);
                instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).requestFocus());settle();
                assertTrue("回到继续观看恢复清晰度",sharp.sameAs(((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap()));
                assertTrue("回到继续观看恢复简介",shownText(hero,"背景简介"));
                assertEquals(carousel,hero.actionView().isShown());
                if(carousel){
                    instrumentation.runOnMainSync(()->assertTrue("轮播详情入口仍可聚焦",hero.actionView().requestFocus()));settle();
                    assertTrue("轮播详情聚焦时显示简介",shownText(hero,"背景简介"));
                    assertSame(backdropField(hero,"sharp"),((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap());
                    android.widget.ImageView outgoing=(android.widget.ImageView)backdropField(hero,"previousBackdrop");
                    android.widget.TextView heroTitle=(android.widget.TextView)backdropField(hero,"title");
                    android.widget.LinearLayout copy=(android.widget.LinearLayout)backdropField(hero,"copy");
                    String oldTitle=heroTitle.getText().toString(),nextTitle="切换后标题";
                    java.util.concurrent.atomic.AtomicInteger oldFadeFrames=new java.util.concurrent.atomic.AtomicInteger(),newFadeFrames=new java.util.concurrent.atomic.AtomicInteger(),overlapFrames=new java.util.concurrent.atomic.AtomicInteger(),textMismatchFrames=new java.util.concurrent.atomic.AtomicInteger();
                    JSONObject next=new JSONObject().put("Id","transition-next").put("Name",nextTitle).put("Type","Movie").put("Overview","切换后简介").put("ProductionYear",2026);
                    instrumentation.runOnMainSync(()->hero.show(next,false,true));
                    long transitionDeadline=System.currentTimeMillis()+3000;
                    while(System.currentTimeMillis()<transitionDeadline){
                        instrumentation.runOnMainSync(()->{boolean oldFading=outgoing.getDrawable()!=null&&outgoing.getAlpha()>0&&outgoing.getAlpha()<1;boolean newFading=image.getDrawable()!=null&&image.getAlpha()>0&&image.getAlpha()<1;if(oldFading){oldFadeFrames.incrementAndGet();if(!oldTitle.equals(heroTitle.getText().toString()))textMismatchFrames.incrementAndGet();}if(newFading){newFadeFrames.incrementAndGet();if(!nextTitle.equals(heroTitle.getText().toString()))textMismatchFrames.incrementAndGet();}if(outgoing.getDrawable()!=null&&outgoing.getAlpha()>0.01f&&image.getAlpha()>0.01f)overlapFrames.incrementAndGet();});
                        boolean[] done={false};instrumentation.runOnMainSync(()->done[0]=!((Boolean)backdropField(hero,"pendingContent"))&&outgoing.getDrawable()==null&&image.getAlpha()>0.99f&&copy.getAlpha()>0.99f&&nextTitle.equals(heroTitle.getText().toString()));if(done[0])break;Thread.sleep(16);
                    }
                    assertTrue("切图必须先绘制旧图渐暗帧",oldFadeFrames.get()>0);assertTrue("切图必须再绘制新图渐亮帧",newFadeFrames.get()>0);assertEquals("顺序淡出淡入期间不能同时显示两张图",0,overlapFrames.get());assertEquals("文字必须和对应图片同步切换",0,textMismatchFrames.get());assertNull(outgoing.getDrawable());
                    assertTrue("轮播切图后保留简介",shownText(hero,"切换后简介"));
                    assertTrue(shownText(hero,"2026"));assertSame(backdropField(hero,"sharp"),((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap());assertEquals(1f,copy.getAlpha(),0.01f);
                    instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).requestFocus());settle();
                }
                call("showMenu",new Class<?>[]{});settle();assertFalse("菜单聚焦时隐藏简介",shownText(hero,"背景简介"));assertFalse(hero.actionView().isShown());
                call("hideMenu",new Class<?>[]{});settle();assertTrue("菜单退出后恢复简介",((View)backdropField(hero,"information")).isShown()&&((View)backdropField(hero,"overview")).isShown());
                saveScreenshot("resume-restored-"+carousel+".png");
                HomeActivity closing=home;instrumentation.runOnMainSync(closing::finish);await(closing::isDestroyed);home=null;
            }
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }
    @Test public void backdropSwitchNeverDisplaysSharpImageWhileBlurIsPending()throws Exception{
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"blur-transition-fixture","背景切换测试","synthetic-test-session");
            new TvPreferences(instrumentation.getTargetContext(),new EtknApi(instrumentation.getTargetContext())).setSmartCarousel(false);
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->cards().size()==90&&home.getCurrentFocus()==field(HomeActivity.class,"resumeFirst"));
            FocusBackdrop hero=(FocusBackdrop)field(HomeActivity.class,"homeBackdrop");android.widget.ImageView image=(android.widget.ImageView)hero.getChildAt(0);
            instrumentation.runOnMainSync(()->library("library-0").requestFocus());
            await(()->backdropField(hero,"blurred")!=null);Bitmap previous=((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();
            java.util.concurrent.CountDownLatch blocked=new java.util.concurrent.CountDownLatch(6);
            for(int i=0;i<6;i++)TvActivity.IO.execute(()->{blocked.countDown();try{release.await(12,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
            assertTrue(blocked.await(5,java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicInteger sharpFrames=new java.util.concurrent.atomic.AtomicInteger();
            android.view.ViewTreeObserver.OnPreDrawListener frames=()->{if(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap()==backdropField(hero,"sharp"))sharpFrames.incrementAndGet();return true;};
            instrumentation.runOnMainSync(()->image.getViewTreeObserver().addOnPreDrawListener(frames));
            for(String id:new String[]{"transition-a","transition-b","transition-b"}){
                JSONObject item=new JSONObject().put("Id",id).put("Type","Movie");
                instrumentation.runOnMainSync(()->hero.show(item,false,true));await(()->backdropField(hero,"sharp")!=null);
                instrumentation.runOnMainSync(()->{assertNotNull(image.getDrawable());assertSame("新图模糊未完成时应继续显示上一张模糊图",previous,((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap());});
            }
            release.countDown();await(()->backdropField(hero,"blurred")!=null);
            instrumentation.runOnMainSync(()->{assertSame(backdropField(hero,"blurred"),((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap());image.getViewTreeObserver().removeOnPreDrawListener(frames);});
            assertEquals("切换过程中不能绘制任何清晰帧",0,sharpFrames.get());
        }finally{release.countDown();if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }
    private Object backdropField(FocusBackdrop hero,String name){try{java.lang.reflect.Field field=FocusBackdrop.class.getDeclaredField(name);field.setAccessible(true);return field.get(hero);}catch(Exception error){throw new AssertionError(error);}}

    private View findText(View view,String value){if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().contains(value))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findText(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private boolean shownText(View root,String value){View found=findText(root,value);return found!=null&&found.isShown();}
    private double imageDetail(Bitmap source){
        Bitmap sample=Bitmap.createScaledBitmap(source,256,144,true);long detail=0;
        for(int y=0;y<144;y++)for(int x=1;x<256;x++){int a=sample.getPixel(x-1,y),b=sample.getPixel(x,y);detail+=Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));}
        if(sample!=source)sample.recycle();return detail;
    }

    @Test public void cachedHomeAppearsBeforeNetworkAndServerEventRefreshesWithoutStealingFocus()throws Exception{
        try(Fixture server=new Fixture()){
            SessionStore session=new SessionStore(instrumentation.getTargetContext());session.save(server.origin(),"cache-fixture","缓存测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->cards().size()==90&&home.getCurrentFocus()!=null);assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));
            HomeActivity previous=home;instrumentation.runOnMainSync(previous::finish);await(previous::isDestroyed);LoginGate.satisfy();server.holdResume=true;server.holdFirstLatest=true;
            long started=android.os.SystemClock.elapsedRealtime();
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->cards().size()==90&&home.getCurrentFocus()!=null);
            android.util.Log.i("HomePerformance","cached interactive ms="+(android.os.SystemClock.elapsedRealtime()-started));
            assertEquals("缓存应在续播请求结束前展示",1L,server.resumeGate.getCount());assertEquals(1L,server.latestGate.getCount());
            instrumentation.runOnMainSync(()->library("library-2").requestFocus());
            server.resumeGate.countDown();server.latestGate.countDown();
            await(()->((java.util.Set<?>)field(HomeActivity.class,"pendingShelves")).isEmpty());
            server.changed=true;server.updateGate.countDown();
            await(()->cards().stream().anyMatch(card->((android.widget.TextView)((ViewGroup)card).getChildAt(1)).getText().toString().contains("更新后的影片")));
            assertSame("后台刷新不能抢走其它片架的焦点",library("library-2"),home.getCurrentFocus());
            assertEquals("服务端更新后不能重复追加片架",90,cards().size());
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void slowLibraryDoesNotBlockOtherShelvesOrInitialFocus()throws Exception{
        try(Fixture server=new Fixture()){
            server.holdFirstLatest=true;server.holdResume=true;
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"slow-fixture","首页延迟测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->library("library-2")!=null&&cards().size()>=18);
            assertEquals("媒体库列表只能请求一次",1,server.viewCalls);assertEquals(1L,server.latestGate.getCount());assertEquals(1L,server.resumeGate.getCount());
            assertFalse("续播结果未到时不能先选媒体库作为默认焦点",(Boolean)field(HomeActivity.class,"initialFocusChosen"));
            server.resumeGate.countDown();await(()->home.getCurrentFocus()==field(HomeActivity.class,"resumeFirst"));
            View focused=home.getCurrentFocus();server.latestGate.countDown();await(()->cards().size()==90);assertSame("后续加载不可抢走焦点",focused,home.getCurrentFocus());
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void mediaLibraryMenuGridResizesSafelyAcrossAllImageLevels()throws Exception{
        try(Fixture server=new Fixture()){
            openMenuHomeFixture(server);call("loadLibraries",new Class<?>[]{});
            await(()->"library".equals(field(HomeActivity.class,"mode"))&&library("library-0")!=null&&library("library-0").getWidth()>0);
            GridLayout grid=(GridLayout)library("library-0").getParent();TvPreferences preferences=new TvPreferences(home,home.api);
            for(int size:new int[]{TvPreferences.IMAGE_EXTREMELY_SMALL,TvPreferences.IMAGE_SMALL,TvPreferences.IMAGE_MEDIUM,TvPreferences.IMAGE_LARGE,TvPreferences.IMAGE_EXTREMELY_LARGE}){
                instrumentation.runOnMainSync(()->{preferences.setImageSize(size);home.onResume();});
                await(()->grid.getColumnCount()==expectedAdaptiveColumns(grid,192,12,size));
                instrumentation.runOnMainSync(()->{View card=library("library-0"),art=((ViewGroup)card).getChildAt(0);assertEquals(Math.round(art.getWidth()*9f/16f),art.getHeight());});
            }
            instrumentation.runOnMainSync(()->{preferences.setImageSize(TvPreferences.IMAGE_MEDIUM);home.onResume();});
            await(()->grid.getColumnCount()==expectedAdaptiveColumns(grid,192,12,TvPreferences.IMAGE_MEDIUM));
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }
    @Test public void landscapeCardsMatchHomeLibrariesAndKeepFocusedEdgesVisible()throws Exception{
        try(Fixture server=new Fixture()){
            openMenuHomeFixture(server);await(()->library("library-0").getWidth()>0);
            new TvPreferences(home,home.api).saveLibraryView("library-0","portrait");
            instrumentation.runOnMainSync(()->library("library-0").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            call("toggleView",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&"landscape".equals(field(HomeActivity.class,"viewMode")));
            instrumentation.runOnMainSync(()->{
                View first=posterGrid().getChildAt(0),second=posterGrid().getChildAt(1);
                View artwork=((ViewGroup)first).getChildAt(0);
                assertTrue("横幅宽度按网格列数填充",first.getWidth()>0);assertEquals(Math.round(artwork.getWidth()*9f/16f),artwork.getHeight());assertEquals(home.dp(24),second.getLeft()-first.getRight());
                assertEquals(expectedAdaptiveColumns(posterGrid(),192,12,TvPreferences.IMAGE_MEDIUM),posterGrid().getColumnCount());
                assertNotNull(findText(first,"海报影片"));assertNotNull(findText(first,"2020"));assertNotNull(findText(first,"8.1"));assertNotNull(findText(first,"7.1"));
            });
            for(int index:new int[]{0,posterGrid().getColumnCount()-1,posterGrid().getColumnCount()}){
                instrumentation.runOnMainSync(()->posterGrid().getChildAt(index).requestFocus());settle();
                instrumentation.runOnMainSync(()->{
                    View card=home.getCurrentFocus(),art=((ViewGroup)card).getChildAt(0);Rect visible=new Rect();int[] position=new int[2];art.getLocationOnScreen(position);assertTrue(art.getGlobalVisibleRect(visible));
                    assertTrue("焦点框须保留左侧安全区",position[0]>=home.dp(40));assertEquals("聚焦横幅宽度不能裁切",Math.round(art.getWidth()*card.getScaleX()),visible.width(),2);assertEquals("聚焦横幅高度不能裁切",Math.round(art.getHeight()*card.getScaleY()),visible.height(),2);
                });saveScreenshot("landscape-focused-"+index+".png");
            }
            TvPreferences imagePreferences=new TvPreferences(home,home.api);
            for(int size:new int[]{TvPreferences.IMAGE_EXTREMELY_SMALL,TvPreferences.IMAGE_SMALL,TvPreferences.IMAGE_MEDIUM,TvPreferences.IMAGE_LARGE,TvPreferences.IMAGE_EXTREMELY_LARGE}){
                instrumentation.runOnMainSync(()->{imagePreferences.setImageSize(size);home.applyImageSizeNow();});
                await(()->posterGrid().getColumnCount()==expectedAdaptiveColumns(posterGrid(),192,12,size));
                instrumentation.runOnMainSync(()->{View first=posterGrid().getChildAt(0),art=((ViewGroup)first).getChildAt(0);assertEquals(Math.round(art.getWidth()*9f/16f),art.getHeight());});
            }
            call("toggleView",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&"portrait".equals(field(HomeActivity.class,"viewMode"))&&posterGrid().getChildAt(0).getWidth()>0&&!posterGrid().isLayoutRequested());
            instrumentation.runOnMainSync(()->{View first=posterGrid().getChildAt(0),art=((ViewGroup)first).getChildAt(0);assertEquals(Math.round(art.getWidth()*1.5f),art.getHeight());Rect visible=new Rect();int[] position=new int[2];art.getLocationOnScreen(position);assertTrue(art.getGlobalVisibleRect(visible));assertEquals("竖幅焦点左侧不能裁切",position[0],visible.left,1);assertEquals("竖幅焦点宽度不能裁切",Math.round(art.getWidth()*first.getScaleX()),visible.width(),2);});
        }finally{closePosterFixture();}
    }

    @Test public void librarySortingPersistsIndependentlyAndBackRestoresTheLibraryCard()throws Exception{
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"sort-fixture","排序测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->library("library-2")!=null&&home.getCurrentFocus()!=null);
            instrumentation.runOnMainSync(()->library("library-2").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            call("setSort",new Class<?>[]{String.class,String.class},"DoubanRating","Descending");await(()->server.lastListingPath.contains("SortBy=DoubanRating"));
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-2")!=null&&home.getCurrentFocus()==library("library-2"));
            instrumentation.runOnMainSync(()->library("library-3").performClick());await(()->"library-3".equals(field(HomeActivity.class,"parent"))&&posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertEquals("SortName",field(HomeActivity.class,"sortBy"));call("setSort",new Class<?>[]{String.class,String.class},"ProductionYear","Ascending");
            await(()->server.lastListingPath.contains("SortBy=ProductionYear"));HomeActivity previous=home;instrumentation.runOnMainSync(previous::finish);await(previous::isDestroyed);LoginGate.satisfy();
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await(()->library("library-2")!=null);
            instrumentation.runOnMainSync(()->library("library-2").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);assertEquals("DoubanRating",field(HomeActivity.class,"sortBy"));assertEquals("Descending",field(HomeActivity.class,"sortOrder"));
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-2")!=null);call("loadLibraries",new Class<?>[]{});await(()->"library".equals(field(HomeActivity.class,"mode"))&&library("library-3")!=null);
            instrumentation.runOnMainSync(()->library("library-3").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);assertEquals("ProductionYear",field(HomeActivity.class,"sortBy"));
            key(KeyEvent.KEYCODE_BACK);await(()->"library".equals(field(HomeActivity.class,"mode"))&&home.getCurrentFocus()==library("library-3"));
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void focusedArtworkRemainsVisibleAcrossShelvesAndRowEdges() throws Exception {
        try(Fixture server=new Fixture()) {
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"focus-regression");
            assertTrue(evidence.isDirectory()||evidence.mkdirs());
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"focus-fixture","焦点回归测试","synthetic-test-session");
            Intent intent=new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            home=(HomeActivity)instrumentation.startActivitySync(intent);
            long deadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<deadline){
                boolean[] ready={false};
                instrumentation.runOnMainSync(()->ready[0]=cards().size()==90&&home.getCurrentFocus()!=null);
                if(ready[0])break;
                Thread.sleep(100);
            }
            assertEquals("合成首页应加载完整的继续观看、媒体库及六个最新片架",90,cards().size());
            settle();check("initial");
            for(int i=0;i<7;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);check("down-"+i);}
            for(int i=0;i<11;i++){key(KeyEvent.KEYCODE_DPAD_RIGHT);check("right-"+i);}
            for(int i=0;i<11;i++){key(KeyEvent.KEYCODE_DPAD_LEFT);check("left-"+i);}
            for(int i=0;i<7;i++){key(KeyEvent.KEYCODE_DPAD_UP);check("up-"+i);}
            assertNull("首页请求应全部使用独立 TV API",server.unexpectedPath);
        } finally {
            if(home!=null)instrumentation.runOnMainSync(()->home.finish());
            new SessionStore(instrumentation.getTargetContext()).clear();
        }
    }

    @Test public void libraryPosterWallSortsAndLoadsMoreFromTheTop() throws Exception {
        try(Fixture server=new Fixture()) {
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"poster-wall-regression");
            assertTrue(evidence.isDirectory()||evidence.mkdirs());
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"poster-fixture","海报墙测试","synthetic-test-session");
            Intent intent=new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            home=(HomeActivity)instrumentation.startActivitySync(intent);
            long deadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<deadline&&cards().size()<90)Thread.sleep(100);
            assertEquals(90,cards().size());
            View library=null;
            for(View card:cards())if(((android.widget.TextView)((ViewGroup)card).getChildAt(1)).getText().toString().startsWith("媒体库 ")){library=card;break;}
            assertNotNull("首页应提供媒体库卡片",library);
            View selected=library;
            ScrollView scroll=(ScrollView)field(HomeActivity.class,"homeScroll");
            instrumentation.runOnMainSync(()->{scroll.scrollTo(0,360);selected.performClick();});
            long itemsDeadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<itemsDeadline&&posterGrid().getChildCount()<60)Thread.sleep(100);
            assertEquals("首批海报应懒加载 60 项",60,posterGrid().getChildCount());
            assertEquals("进入海报墙应回到顶部",0,scroll.getScrollY());
            String meta=((android.widget.TextView)field(HomeActivity.class,"libraryMeta")).getText().toString();
            assertTrue(meta.contains("120 项")&&meta.contains("混合")&&meta.contains("实体库"));
            Rect header=new Rect();assertTrue(((View)field(HomeActivity.class,"libraryMeta")).getGlobalVisibleRect(header));
            saveScreenshot("poster-wall-entry.png");
            View sortButton=findSort((View)field(TvActivity.class,"content"));assertNotNull(sortButton);
            instrumentation.runOnMainSync(sortButton::performClick);settle();
            for(int i=0;i<14;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);
            long sortDeadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<sortDeadline&&!server.lastListingPath.contains("SortBy=DoubanRating"))Thread.sleep(100);
            assertTrue("豆瓣评分排序须走 TV 专属参数",server.lastListingPath.contains("SortBy=DoubanRating"));
            for(int i=0;i<5&&posterGrid().getChildCount()<120;i++){
                instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));Thread.sleep(500);
            }
            long moreDeadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<moreDeadline&&posterGrid().getChildCount()<120)Thread.sleep(100);
            assertEquals("滚动到底部应追加下一批而不移除首批",120,posterGrid().getChildCount());
            int completedCalls=server.listingCalls;
            instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_UP));settle();
            instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));settle();
            assertEquals("全部加载后滚动不能重复请求",completedCalls,server.listingCalls);
            View first=posterGrid().getChildAt(0);instrumentation.runOnMainSync(()->{first.requestFocus();scroll.scrollTo(0,0);});settle();
            Rect visible=new Rect();((ViewGroup)first).getChildAt(0).getGlobalVisibleRect(visible);
            assertFalse("海报焦点图应完整显示",visible.isEmpty());
            View art=((ViewGroup)first).getChildAt(0);int[] location=new int[2];art.getLocationOnScreen(location);
            assertEquals("第一行聚焦海报顶部不能被裁切",location[1],visible.top,1);
            assertEquals("第一行聚焦海报高度必须完整",Math.round(art.getHeight()*first.getScaleY()),visible.height(),2);
            Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            try(FileOutputStream file=new FileOutputStream(new File(evidence,"poster-wall.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
        } finally {
            if(home!=null)instrumentation.runOnMainSync(()->home.finish());
            new SessionStore(instrumentation.getTargetContext()).clear();
        }
    }

    @Test public void deletionResultReloadsTheCurrentLibraryWithoutResettingNavigation()throws Exception{
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"delete-result-fixture","删除返回测试","loopback-only");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->library("library-0")!=null);instrumentation.runOnMainSync(()->library("library-0").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            call("setSort",new Class<?>[]{String.class,String.class},"DoubanRating","Descending");await(()->server.lastListingPath.contains("SortBy=DoubanRating")&&posterGrid().getChildCount()==60);
            int before=server.listingCalls;instrumentation.runOnMainSync(()->home.onActivityResult(1001,android.app.Activity.RESULT_OK,null));await(()->server.listingCalls>before&&posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertEquals("items",field(HomeActivity.class,"mode"));assertEquals("library-0",field(HomeActivity.class,"parent"));assertTrue(server.lastListingPath.contains("SortBy=DoubanRating"));assertFalse(home.isDestroyed());
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    private void saveScreenshot(String name)throws Exception{
        Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        try(FileOutputStream file=new FileOutputStream(new File(evidence,name))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
    }
    @SuppressWarnings("unchecked") private List<View> cards(){
        try{java.lang.reflect.Field field=HomeActivity.class.getDeclaredField("homeCards");field.setAccessible(true);return (List<View>)field.get(home);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private Object field(Class<?> owner,String name){try{java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(home);}catch(Exception error){throw new AssertionError(error);}}
    private GridLayout posterGrid(){return (GridLayout)field(HomeActivity.class,"posterGrid");}
    private View findSort(View view){if(view instanceof android.widget.Button&&((android.widget.Button)view).getText().toString().startsWith("排序 ·"))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findSort(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void key(int key){instrumentation.sendKeyDownUpSync(key);settle();}
    private void settle(){instrumentation.waitForIdleSync();android.os.SystemClock.sleep(400);instrumentation.waitForIdleSync();}
    private void check(String step)throws Exception{
        String[] error={null};
        instrumentation.runOnMainSync(()->{
            View card=home.getCurrentFocus();
            if(!cards().contains(card)){error[0]="焦点离开片架："+card;return;}
            View artwork=((ViewGroup)card).getChildAt(0);
            int[] location=new int[2];artwork.getLocationOnScreen(location);
            Rect visible=new Rect();artwork.getGlobalVisibleRect(visible);
            int width=Math.round(artwork.getWidth()*card.getScaleX()),height=Math.round(artwork.getHeight()*card.getScaleY());
            String geometry=step+" card="+cards().indexOf(card)+" expected="+location[0]+","+location[1]+","+width+","+height+" visible="+visible;
            android.util.Log.i("HomeFocusTest",geometry);
            if(visible.top>location[1]+1||visible.left>location[0]+1||visible.width()<width-2||visible.height()<height-2){
                error[0]=geometry;
                View ancestor=artwork;
                while(ancestor!=null){
                    Rect bounds=new Rect();ancestor.getGlobalVisibleRect(bounds);
                    android.util.Log.i("HomeFocusTest","ancestor="+ancestor.getClass().getSimpleName()+" visible="+bounds+" size="+ancestor.getWidth()+"x"+ancestor.getHeight()+" padding="+ancestor.getPaddingLeft()+","+ancestor.getPaddingTop()+" scroll="+ancestor.getScrollX()+","+ancestor.getScrollY()+" clip="+(ancestor instanceof ViewGroup?((ViewGroup)ancestor).getClipChildren()+","+((ViewGroup)ancestor).getClipToPadding():"n/a"));
                    ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null;
                }
            }
        });
        if(error[0]!=null||step.equals("initial")||step.equals("down-2")||step.equals("up-3")){
            Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            try(FileOutputStream file=new FileOutputStream(new File(evidence,step+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}
            screenshot.recycle();
        }
        assertNull("聚焦图片被裁切："+step+" "+error[0],error[0]);
    }

    private android.app.AlertDialog itemMenu(){return (android.app.AlertDialog)field(HomeActivity.class,"itemMenu");}
    private java.util.List<View> descendants(View root){java.util.List<View> result=new java.util.ArrayList<>();result.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result.addAll(descendants(((ViewGroup)root).getChildAt(i)));return result;}
    private void chooseItemAction(int index){instrumentation.runOnMainSync(()->{android.widget.ListView list=itemMenu().getListView();list.performItemClick(list.getChildAt(index),index,list.getAdapter().getItemId(index));});settle();}
    private void openPosterFixture(Fixture server)throws Exception{
        openMenuHomeFixture(server);
        await(()->library("library-0")!=null);instrumentation.runOnMainSync(()->library("library-0").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
    }
    private void openMenuHomeFixture(Fixture server)throws Exception{
        evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"list-actions-regression");assertTrue(evidence.isDirectory()||evidence.mkdirs());
        new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"list-actions-fixture","列表菜单测试","loopback-only");
        home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->library("library-0")!=null&&home.getCurrentFocus()!=null);
    }
    private void closePosterFixture()throws Exception{if(home!=null){HomeActivity closing=home;instrumentation.runOnMainSync(closing::finish);await(closing::isDestroyed);home=null;}new SessionStore(instrumentation.getTargetContext()).clear();}
    @Test public void homeLibraryMenuHonorsPermissionsAndSupportsAllThreeRefreshModes()throws Exception{
        try(Fixture server=new Fixture()){
            server.canRefresh=true;openMenuHomeFixture(server);
            for(String action:new String[]{"replace_metadata","replace_images","replace_metadata_images"}){
                instrumentation.runOnMainSync(()->library("library-0").requestFocus());key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());assertEquals(1,itemMenu().getListView().getCount());chooseItemAction(0);
                java.util.List<android.widget.Switch> switches=new java.util.ArrayList<>();for(View view:descendants(itemMenu().getWindow().getDecorView()))if(view instanceof android.widget.Switch)switches.add((android.widget.Switch)view);
                instrumentation.runOnMainSync(()->{switches.get(0).setChecked(!action.equals("replace_images"));switches.get(1).setChecked(!action.equals("replace_metadata"));itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();});
                await(()->server.mutations.stream().anyMatch(value->value.endsWith("library-0/Refresh?Action="+action))&&!Boolean.TRUE.equals(field(HomeActivity.class,"mediaActionBusy")));
            }
            server.admin=true;instrumentation.runOnMainSync(()->library("library-0").requestFocus());key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());assertEquals(2,itemMenu().getListView().getCount());saveScreenshot("home-library-menu.png");chooseItemAction(1);await(()->server.mutations.stream().anyMatch(value->value.endsWith("library-0/Scan")));
        }finally{closePosterFixture();}
    }
    @Test public void homeResumeCanBeRemovedAndLatestHasFavoritePlayRefreshDelete()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;openMenuHomeFixture(server);key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());assertEquals(home.dp(320),itemMenu().getWindow().getDecorView().getWidth());assertEquals("从继续观看中移除",itemMenu().getListView().getAdapter().getItem(2));saveScreenshot("home-resume-compact-menu.png");chooseItemAction(2);await(()->server.hiddenResume);
            await(()->((java.util.List<?>)field(HomeActivity.class,"resumeCards")).size()==11);assertFalse(server.deleted);
            await(()->{java.util.List<View> cards=(java.util.List<View>)field(HomeActivity.class,"homeCards");return cards.size()>20;});
            instrumentation.runOnMainSync(()->{java.util.List<View> cards=(java.util.List<View>)field(HomeActivity.class,"homeCards");java.util.List<?> resume=(java.util.List<?>)field(HomeActivity.class,"resumeCards");for(View card:cards)if(!resume.contains(card)&&String.valueOf(card.getTag()).startsWith("item-")){card.requestFocus();break;}});
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());assertEquals(home.dp(320),itemMenu().getWindow().getDecorView().getWidth());assertEquals(4,itemMenu().getListView().getCount());assertEquals("收藏",itemMenu().getListView().getAdapter().getItem(0));assertEquals("播放",itemMenu().getListView().getAdapter().getItem(1));assertEquals("刷新",itemMenu().getListView().getAdapter().getItem(2));assertEquals("删除",itemMenu().getListView().getAdapter().getItem(3));saveScreenshot("home-latest-menu.png");chooseItemAction(0);await(()->server.favorite);
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);chooseItemAction(1);assertEquals(1,monitor.getHits());instrumentation.removeMonitor(monitor);
        }finally{closePosterFixture();}
    }
    @Test public void libraryFunctionMenuTogglesStateAndUsesDelegatedRefresh()throws Exception{
        try(Fixture server=new Fixture()){
            server.canRefresh=true;openPosterFixture(server);key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());
            assertEquals(3,itemMenu().getListView().getCount());assertTrue(itemMenu().getListView().getAdapter().getItem(0).toString().contains("收藏"));
            chooseItemAction(0);await(()->server.favorite&&posterGrid().getChildAt(0).getTag() instanceof JSONObject&&((JSONObject)posterGrid().getChildAt(0).getTag()).optJSONObject("UserData").optBoolean("IsFavorite"));
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());assertEquals("取消收藏",itemMenu().getListView().getAdapter().getItem(0));chooseItemAction(1);await(()->server.played&&((JSONObject)posterGrid().getChildAt(0).getTag()).optJSONObject("UserData").optBoolean("Played"));
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());assertEquals("标记未播放",itemMenu().getListView().getAdapter().getItem(1));chooseItemAction(2);
            assertNotNull(itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE));
            instrumentation.runOnMainSync(()->itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());await(()->server.mutations.stream().anyMatch(value->value.endsWith("Refresh?Action=replace_metadata")));
            assertFalse("普通账号只获得刷新能力",server.mutations.stream().anyMatch(value->value.startsWith("DELETE /api/tv/Items/")));
        }finally{closePosterFixture();}
    }
    @Test public void libraryDeleteDefaultsToCancelAndServerDenialAllowsRetry()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;openPosterFixture(server);key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());assertEquals(4,itemMenu().getListView().getCount());
            chooseItemAction(3);assertSame(itemMenu().getButton(android.app.AlertDialog.BUTTON_NEGATIVE),itemMenu().getCurrentFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);assertTrue(server.mutations.isEmpty());
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());chooseItemAction(3);server.denyMutation=true;
            instrumentation.runOnMainSync(()->itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());await(()->!server.mutations.isEmpty()&&!Boolean.TRUE.equals(field(HomeActivity.class,"mediaActionBusy")));assertFalse(home.isFinishing());
            server.denyMutation=false;key(KeyEvent.KEYCODE_MENU);await(()->itemMenu().isShowing());chooseItemAction(3);instrumentation.runOnMainSync(()->itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());await(()->server.deleted&&posterGrid().getChildCount()==59);
            assertEquals("library-0",field(HomeActivity.class,"parent"));assertEquals("items",field(HomeActivity.class,"mode"));
        }finally{closePosterFixture();}
    }
    @Test public void serverImageEventUpdatesLoadedPosterPagesWithoutLosingPosition()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;openPosterFixture(server);assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));call("loadPosterPage",new Class<?>[]{boolean.class},false);await(()->posterGrid().getChildCount()==120);
            instrumentation.runOnMainSync(()->posterGrid().getChildAt(75).requestFocus());settle();ScrollView scroll=(ScrollView)field(HomeActivity.class,"homeScroll");int before=scroll.getScrollY();
            key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());chooseItemAction(2);instrumentation.runOnMainSync(()->itemMenu().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());await(()->"元数据刷新任务已提交".contentEquals(home.status.getText()));
            server.changed=true;server.updateGate.countDown();await(()->posterGrid().getChildCount()==120&&((JSONObject)posterGrid().getChildAt(75).getTag()).optJSONObject("ImageTags").optString("Primary").equals("poster2"));
            await(()->{android.widget.ImageView image=(android.widget.ImageView)((ViewGroup)((ViewGroup)posterGrid().getChildAt(75)).getChildAt(0)).getChildAt(0);return image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap().getPixel(10,10)==Color.MAGENTA;});
            assertTrue(descendants(posterGrid().getChildAt(75)).stream().anyMatch(view->view instanceof android.widget.TextView&&"9.4".equals(((android.widget.TextView)view).getText().toString())));
            assertTrue(descendants(posterGrid().getChildAt(75)).stream().anyMatch(view->view instanceof android.widget.TextView&&"9.0".equals(((android.widget.TextView)view).getText().toString())));
            await(()->server.refreshStatusRequests>0);assertEquals("元数据刷新任务已提交",home.status.getText().toString());server.taskStatus="succeeded";
            await(()->"元数据/图片已刷新".contentEquals(home.status.getText()));assertEquals(1,field(HomeActivity.class,"homeGeneration"));
            assertEquals("item-75",((JSONObject)home.getCurrentFocus().getTag()).optString("Id"));assertEquals(before,scroll.getScrollY());assertEquals(120,field(HomeActivity.class,"offset"));settle();saveScreenshot("live-poster-images.png");
        }finally{closePosterFixture();}
    }
    @Test public void scheduledHomeUpdateRefreshesSilentlyWithoutSubmittingTask()throws Exception{
        try(Fixture server=new Fixture()){
            openMenuHomeFixture(server);assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));
            server.changed=true;server.updateGate.countDown();
            await(()->descendants(home.content).stream().anyMatch(view->view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().startsWith("更新后的影片")));
            assertTrue(server.mutations.isEmpty());assertFalse("元数据/图片已刷新".contentEquals(home.status.getText()));assertEquals(0,server.refreshStatusRequests);
        }finally{closePosterFixture();}
    }
    @Test public void libraryUpMenuKeepsFocusDuringRepeatedKeys()throws Exception{
        try(Fixture server=new Fixture()){
            openPosterFixture(server);View filter=findSort(home.content);assertNotNull(filter);instrumentation.runOnMainSync(filter::requestFocus);
            key(KeyEvent.KEYCODE_DPAD_UP);HorizontalScrollView menu=(HorizontalScrollView)field(HomeActivity.class,"menu");assertEquals(View.VISIBLE,menu.getVisibility());View selected=home.getCurrentFocus();
            for(int i=0;i<4;i++){key(KeyEvent.KEYCODE_DPAD_UP);assertSame(selected,home.getCurrentFocus());assertEquals(View.VISIBLE,menu.getVisibility());}
            key(KeyEvent.KEYCODE_DPAD_RIGHT);assertTrue(home.nav.hasFocus());View horizontal=home.getCurrentFocus();
            long now=android.os.SystemClock.uptimeMillis();instrumentation.runOnMainSync(()->{home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));for(int n=1;n<6;n++)home.dispatchKeyEvent(new KeyEvent(now,now+n*100,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,n));home.dispatchKeyEvent(new KeyEvent(now,now+600,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP,0));});settle();
            assertSame(horizontal,home.getCurrentFocus());saveScreenshot("library-main-menu-stable.png");key(KeyEvent.KEYCODE_DPAD_DOWN);assertSame(filter,home.getCurrentFocus());assertEquals(View.GONE,menu.getVisibility());
            instrumentation.runOnMainSync(()->{home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));for(int n=1;n<6;n++)home.dispatchKeyEvent(new KeyEvent(now,now+n*100,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,n));home.dispatchKeyEvent(new KeyEvent(now,now+600,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP,0));});settle();assertTrue(home.nav.hasFocus());
            call("loadPosterPage",new Class<?>[]{boolean.class},true);await(()->posterGrid().getChildCount()==60);assertTrue("迟到的首屏加载不能从主菜单抢走焦点",home.nav.hasFocus());
        }finally{closePosterFixture();}
    }
    @Test public void sparseFolderMediaKeepsFullGridCardWidthAcrossAllSizes()throws Exception{
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;openPosterFixture(server);
            TvPreferences preferences=new TvPreferences(home,home.api);
            int[][] fullWidths=new int[2][5];
            for(int mode=0;mode<2;mode++){
                if(mode==1){call("toggleView",new Class<?>[]{});await(()->"landscape".equals(field(HomeActivity.class,"viewMode"))&&posterGrid().getChildCount()==60);}
                for(int size=0;size<5;size++){
                    final int level=size,cardWidth=mode==0?132:192,gap=mode==0?6:12;
                    instrumentation.runOnMainSync(()->{preferences.setImageSize(level);home.onResume();});
                    await(()->posterGrid().getColumnCount()==expectedAdaptiveColumns(posterGrid(),cardWidth,gap,level)&&!Boolean.TRUE.equals(field(HomeActivity.class,"refreshingPosters")));
                    fullWidths[mode][size]=posterGrid().getChildAt(0).getWidth();
                }
            }
            call("toggleView",new Class<?>[]{});await(()->"portrait".equals(field(HomeActivity.class,"viewMode"))&&posterGrid().getChildCount()==60);
            call("toggleFolders",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&"Folder".equals(((JSONObject)posterGrid().getChildAt(0).getTag()).optString("Type")));
            server.folderMediaCount=1;instrumentation.runOnMainSync(()->posterGrid().getChildAt(0).performClick());await(()->posterGrid().getChildCount()==1);
            for(int count=1;count<=2;count++){
                final int items=count;
                if(count==2){server.folderMediaCount=2;call("loadPosterPage",new Class<?>[]{boolean.class},true);await(()->posterGrid().getChildCount()==items);}
                for(int mode=0;mode<2;mode++){
                    if(!((mode==0?"portrait":"landscape").equals(field(HomeActivity.class,"viewMode")))){call("toggleView",new Class<?>[]{});await(()->posterGrid().getChildCount()==items);}
                    for(int size=0;size<5;size++){
                        final int level=size,cardWidth=mode==0?132:192,gap=mode==0?6:12;
                        instrumentation.runOnMainSync(()->{preferences.setImageSize(level);home.onResume();});
                        await(()->posterGrid().getColumnCount()==expectedAdaptiveColumns(posterGrid(),cardWidth,gap,level)&&!Boolean.TRUE.equals(field(HomeActivity.class,"refreshingPosters")));
                        saveScreenshot("folder-"+count+"-"+mode+"-size-"+size+".png");
                        for(int index=0;index<items;index++){
                            ViewGroup card=(ViewGroup)posterGrid().getChildAt(index);View art=card.getChildAt(0);
                            assertEquals("目录内 "+items+" 项应保持满行卡片列宽 mode="+mode+" size="+size,fullWidths[mode][size],card.getWidth(),1);
                            assertEquals(Math.round(card.getWidth()*(mode==0?1.5f:9f/16f)),art.getHeight());
                            if(index>0)assertEquals("两项仍在同一行",posterGrid().getChildAt(0).getTop(),card.getTop());
                        }
                    }
                }
            }
            assertTrue(server.mutations.isEmpty());assertNull(server.unexpectedPath);
        }finally{closePosterFixture();}
    }
    private int expectedAdaptiveCardWidth(GridLayout grid,int gapDp){return (grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight())/grid.getColumnCount()-2*home.dp(gapDp);}
    @Test public void folderBrowseRestoresPaginationAndGeneratesCoverOnlyByMenu()throws Exception{
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;server.canRefresh=true;openPosterFixture(server);
            assertTrue(descendants(home.content).stream().anyMatch(v->v instanceof android.widget.Button&&"文件夹".equals(((android.widget.Button)v).getText().toString())));
            call("toggleFolders",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&"Folder".equals(((JSONObject)posterGrid().getChildAt(0).getTag()).optString("Type")));
            assertEquals(expectedAdaptiveCardWidth(posterGrid(),12),posterGrid().getChildAt(0).getLayoutParams().width);assertEquals(android.graphics.Color.TRANSPARENT,((android.graphics.drawable.ColorDrawable)posterGrid().getChildAt(0).getBackground()).getColor());assertEquals(View.GONE,((View)field(HomeActivity.class,"viewModeButton")).getVisibility());
            instrumentation.runOnMainSync(()->{
                View first=posterGrid().getChildAt(0),second=posterGrid().getChildAt(1);assertEquals(expectedAdaptiveCardWidth(posterGrid(),12),first.getWidth());assertEquals(Math.round(first.getWidth()*9f/16f)+home.imageDp(42),first.getHeight());
                assertEquals(Math.round(first.getWidth()*9f/16f),((ViewGroup)first).getChildAt(0).getHeight());assertEquals(home.dp(24),second.getLeft()-first.getRight());
                int[] location=new int[2];first.getLocationOnScreen(location);assertTrue("首张目录应预留首页同等左侧空间",location[0]>=home.dp(40));
                Rect visible=new Rect();assertTrue(first.getGlobalVisibleRect(visible));assertEquals("聚焦放大不能裁切目录宽度",Math.round(first.getWidth()*first.getScaleX()),visible.width(),1);
            });saveScreenshot("folder-compact-first-focus.png");
            call("loadPosterPage",new Class<?>[]{boolean.class},false);await(()->posterGrid().getChildCount()==70);
            instrumentation.runOnMainSync(()->{posterGrid().getChildAt(65).requestFocus();posterGrid().getChildAt(65).performClick();});await(()->posterGrid().getChildCount()==2);
            assertEquals("portrait",field(HomeActivity.class,"viewMode"));assertEquals(expectedAdaptiveCardWidth(posterGrid(),6),posterGrid().getChildAt(0).getLayoutParams().width);assertEquals(View.VISIBLE,((View)field(HomeActivity.class,"viewModeButton")).getVisibility());saveScreenshot("folder-media-portrait.png");
            call("toggleView",new Class<?>[]{});await(()->posterGrid().getChildCount()==2&&"landscape".equals(field(HomeActivity.class,"viewMode")));assertEquals(expectedAdaptiveCardWidth(posterGrid(),12),posterGrid().getChildAt(0).getLayoutParams().width);
            assertTrue(server.mutations.isEmpty());key(KeyEvent.KEYCODE_BACK);await(()->posterGrid().getChildCount()==70&&home.getCurrentFocus()!=null&&home.getCurrentFocus().getTag() instanceof JSONObject&&"tv-folder-category65".equals(((JSONObject)home.getCurrentFocus().getTag()).optString("Id")));
            assertEquals(expectedAdaptiveCardWidth(posterGrid(),12),posterGrid().getChildAt(0).getLayoutParams().width);assertEquals("landscape",field(HomeActivity.class,"viewMode"));assertEquals(View.GONE,((View)field(HomeActivity.class,"viewModeButton")).getVisibility());saveScreenshot("folder-page-restored.png");key(KeyEvent.KEYCODE_MENU);await(()->itemMenu()!=null&&itemMenu().isShowing());assertEquals("生成封面",itemMenu().getListView().getAdapter().getItem(0));chooseItemAction(0);
            await(()->server.mutations.stream().anyMatch(x->x.contains("/FolderCover?FolderId=tv-folder-category65"))&&!Boolean.TRUE.equals(field(HomeActivity.class,"mediaActionBusy")));
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-1")!=null);instrumentation.runOnMainSync(()->library("library-1").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertFalse(descendants(home.content).stream().anyMatch(v->v instanceof android.widget.Button&&((android.widget.Button)v).getText().toString().startsWith("文件夹")));assertNull(server.unexpectedPath);
        }finally{closePosterFixture();}
    }
    @Test public void folderModePersistsAcrossActivityReopenAndCanBeTurnedOff()throws Exception{
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;openPosterFixture(server);assertEquals(false,field(HomeActivity.class,"folderMode"));
            call("toggleFolders",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&Boolean.TRUE.equals(field(HomeActivity.class,"folderMode")));closePosterFixture();
            openPosterFixture(server);assertEquals(true,field(HomeActivity.class,"folderMode"));assertEquals("Folder",((JSONObject)posterGrid().getChildAt(0).getTag()).optString("Type"));
            call("toggleFolders",new Class<?>[]{});await(()->posterGrid().getChildCount()==60&&Boolean.FALSE.equals(field(HomeActivity.class,"folderMode")));closePosterFixture();
            openPosterFixture(server);assertEquals(false,field(HomeActivity.class,"folderMode"));assertFalse("Folder".equals(((JSONObject)posterGrid().getChildAt(0).getTag()).optString("Type")));
        }finally{closePosterFixture();}
    }
    @Test public void collectionLibraryIsLastSkipsLatestAndRestoresMemberReturnFocus()throws Exception{
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;openMenuHomeFixture(server);assertNotNull(library("library-5"));
            assertEquals("library-5",library("library-5").getTag());
            assertFalse(server.latestPaths.stream().anyMatch(x->x.contains("ParentId=library-5")));
            instrumentation.runOnMainSync(()->library("library-5").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertEquals("BoxSet",((JSONObject)posterGrid().getChildAt(0).getTag()).optString("Type"));
            call("loadPosterPage",new Class<?>[]{boolean.class},false);await(()->posterGrid().getChildCount()==70);
            instrumentation.runOnMainSync(()->{posterGrid().getChildAt(66).requestFocus();posterGrid().getChildAt(66).performClick();});await(()->posterGrid().getChildCount()==60&&"collection-66".equals(field(HomeActivity.class,"parent")));
            key(KeyEvent.KEYCODE_BACK);await(()->posterGrid().getChildCount()==70&&home.getCurrentFocus()!=null&&home.getCurrentFocus().getTag() instanceof JSONObject&&"collection-66".equals(((JSONObject)home.getCurrentFocus().getTag()).optString("Id")));
            saveScreenshot("collection-page-restored.png");assertNull(server.unexpectedPath);
        }finally{closePosterFixture();}
    }
    private android.widget.Button namedButton(View root,String name){for(View view:descendants(root))if(view instanceof android.widget.Button&&name.contentEquals(((android.widget.Button)view).getText()))return (android.widget.Button)view;return null;}
    private Object objectField(Object owner,String name){try{java.lang.reflect.Field member=owner.getClass().getDeclaredField(name);member.setAccessible(true);return member.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    @Test public void favoritesAndFacetCardsUseHomeMenuAndExplicitArtworkActions()throws Exception{
        FacetActivity facets=null;HomeActivity browser=null;
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;server.admin=true;openMenuHomeFixture(server);
            assertNotNull(namedButton(home.nav,"收藏"));assertNull(namedButton(home.nav,"继续观看"));
            call("openFavorites",new Class<?>[]{});await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);assertTrue(server.lastListingPath.contains("Filters=IsFavorite"));key(KeyEvent.KEYCODE_BACK);await(()->library("library-0")!=null);
            instrumentation.runOnMainSync(()->library("library-0").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertNotNull(namedButton(home.content,"出品公司"));assertNull(namedButton(home.content,"收藏"));assertNotNull(namedButton(home.content,"播放 ▾"));assertFalse(descendants(home.content).stream().anyMatch(v->v instanceof android.widget.Button&&((android.widget.Button)v).getText().toString().startsWith("模式 ·")));
            instrumentation.runOnMainSync(()->namedButton(home.content,"播放 ▾").requestFocus());settle();call("choosePlayMode",new Class<?>[]{});await(()->instrumentation.getUiAutomation().getRootInActiveWindow()!=null);settle();assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("顺序播放").isEmpty());assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("随机播放").isEmpty());key(KeyEvent.KEYCODE_BACK);
            for(String menu:new String[]{"类型","出品公司"}){
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(FacetActivity.class.getName(),null,false);instrumentation.runOnMainSync(()->namedButton(home.content,menu).performClick());facets=(FacetActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(facets);
                FacetActivity active=facets;await(()->((GridLayout)objectField(active,"cards")).getChildCount()==2&&Boolean.TRUE.equals(objectField(active,"canArtwork")));GridLayout cards=(GridLayout)objectField(active,"cards");
                assertEquals((cards.getWidth()-cards.getPaddingLeft()-cards.getPaddingRight())/cards.getColumnCount()-2*active.dp(12),cards.getChildAt(0).getWidth());assertFalse(((JSONObject)cards.getChildAt(0).getTag()).optString("Name").contains("标签"));assertTrue(server.mutations.stream().noneMatch(value->value.contains("/FacetArtwork?"))||menu.equals("出品公司"));
                key(KeyEvent.KEYCODE_MENU);await(()->objectField(active,"actionMenu")!=null&&((android.app.AlertDialog)objectField(active,"actionMenu")).isShowing());android.app.AlertDialog actions=(android.app.AlertDialog)objectField(active,"actionMenu");assertEquals(menu.equals("类型")?"生成封面":"拉取图标",actions.getListView().getAdapter().getItem(0));
                instrumentation.runOnMainSync(()->actions.getListView().performItemClick(null,0,0));await(()->!Boolean.TRUE.equals(objectField(active,"busy"))&&cards.getChildAt(0).getTag() instanceof JSONObject&&((JSONObject)cards.getChildAt(0).getTag()).has("ImageTags"));
                assertTrue(server.mutations.stream().anyMatch(value->value.contains("/FacetArtwork?")&&(menu.equals("类型")||value.contains("Action=icon"))));assertEquals("facet-0",((JSONObject)active.getCurrentFocus().getTag()).optString("Id"));saveScreenshot(menu.equals("类型")?"type-facets.png":"company-facets.png");
                monitor=instrumentation.addMonitor(HomeActivity.class.getName(),null,false);instrumentation.runOnMainSync(()->cards.getChildAt(0).performClick());browser=(HomeActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(browser);HomeActivity listing=browser;await(()->objectField(listing,"posterGrid") instanceof GridLayout&&((GridLayout)objectField(listing,"posterGrid")).getChildCount()>0);
                assertTrue(server.lastListingPath.contains("ParentId=library-0"));assertTrue(server.lastListingPath.contains(menu.equals("类型")?"Genres=":"Companies="));key(KeyEvent.KEYCODE_BACK);await(()->listing.isDestroyed()&&active.hasWindowFocus());browser=null;key(KeyEvent.KEYCODE_BACK);await(active::isDestroyed);facets=null;
            }
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-1")!=null);instrumentation.runOnMainSync(()->library("library-1").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()>0);assertNotNull(namedButton(home.content,"播出平台"));assertNull(server.unexpectedPath);
        }finally{if(browser!=null){HomeActivity active=browser;instrumentation.runOnMainSync(active::finish);}if(facets!=null){FacetActivity active=facets;instrumentation.runOnMainSync(active::finish);}closePosterFixture();}
    }
    @Test public void manyFacetIconsKeepFocusResponsive()throws Exception{
        FacetActivity facets=null;
        try(Fixture server=new Fixture()){
            server.admin=true;server.browseFeatures=true;server.facetArt=true;server.facetCount=60;openMenuHomeFixture(server);Images.clear();
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(FacetActivity.class.getName(),null,false);
            instrumentation.runOnMainSync(()->home.startActivity(new Intent(home,FacetActivity.class).putExtra("parent","library-0").putExtra("kind","Companies").putExtra("title","出品公司")));
            facets=(FacetActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(facets);FacetActivity active=facets;
            GridLayout cards=(GridLayout)objectField(active,"cards");await(()->cards.getChildCount()==60);
            // Visit every row so both eager and viewport loaders draw the same images.
            for(int i=0;i<60;i+=cards.getColumnCount()){int target=i;instrumentation.runOnMainSync(()->cards.getChildAt(target).requestFocus());Thread.sleep(150);}
            await(()->{for(int i=0;i<60;i++){ViewGroup art=(ViewGroup)((ViewGroup)cards.getChildAt(i)).getChildAt(0);if(!(((android.widget.ImageView)art.getChildAt(1)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable))return false;}return true;});
            long[] bitmapBytes={0};instrumentation.runOnMainSync(()->{for(int i=0;i<60;i++){ViewGroup art=(ViewGroup)((ViewGroup)cards.getChildAt(i)).getChildAt(0);bitmapBytes[0]+=((android.graphics.drawable.BitmapDrawable)((android.widget.ImageView)art.getChildAt(1)).getDrawable()).getBitmap().getAllocationByteCount();}cards.getChildAt(0).requestFocus();});settle();
            java.util.List<Long> frames=java.util.Collections.synchronizedList(new java.util.ArrayList<>()),keys=new java.util.ArrayList<>();
            android.os.HandlerThread collector=new android.os.HandlerThread("facet-frames");collector.start();
            android.view.Window.OnFrameMetricsAvailableListener listener=(window,frame,dropped)->frames.add(frame.getMetric(android.view.FrameMetrics.TOTAL_DURATION));
            instrumentation.runOnMainSync(()->active.getWindow().addOnFrameMetricsAvailableListener(listener,new android.os.Handler(collector.getLooper())));
            try{for(int pass=0;pass<4;pass++)for(int i=0;i<9;i++){long start=System.nanoTime();instrumentation.sendKeyDownUpSync(pass%2==0?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);instrumentation.runOnMainSync(()->assertNotNull(active.getCurrentFocus()));keys.add(System.nanoTime()-start);Thread.sleep(65);}}
            finally{instrumentation.runOnMainSync(()->active.getWindow().removeOnFrameMetricsAvailableListener(listener));collector.quitSafely();collector.join();}
            java.util.Collections.sort(frames);java.util.Collections.sort(keys);assertFalse(frames.isEmpty());
            assertTrue("Category thumbnails must stay below 40 MiB for 60 cards",bitmapBytes[0]<40L*1024*1024);
            JSONObject metrics=new JSONObject().put("cards",60).put("bitmapBytes",bitmapBytes[0]).put("frames",frames.size()).put("frameP50Ms",frames.get(frames.size()/2)/1e6).put("frameP95Ms",frames.get((frames.size()-1)*95/100)/1e6).put("keyP95Ms",keys.get((keys.size()-1)*95/100)/1e6);
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"facet-performance");assertTrue(evidence.isDirectory()||evidence.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(evidence,"metrics.json"))){out.write(metrics.toString(2).getBytes(StandardCharsets.UTF_8));}saveScreenshot("many-company-icons.png");android.util.Log.i("FacetPerformance",metrics.toString());assertNull(server.unexpectedPath);
        }finally{if(facets!=null){FacetActivity active=facets;instrumentation.runOnMainSync(active::finish);}closePosterFixture();}
    }
    @Test public void facetBatchArtworkContinuesAfterFailureAndStopsAfterLeaving()throws Exception{
        FacetActivity facets=null;
        try(Fixture server=new Fixture()){
            server.admin=true;server.browseFeatures=true;openMenuHomeFixture(server);
            for(String kind:new String[]{"Types","Companies"}){
                server.mutations.clear();server.failFirstFacet=true;server.holdFacet=kind.equals("Types");
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(FacetActivity.class.getName(),null,false);
                instrumentation.runOnMainSync(()->home.startActivity(new Intent(home,FacetActivity.class).putExtra("parent","library-0").putExtra("kind",kind).putExtra("title",kind.equals("Types")?"类型":"出品公司")));
                facets=(FacetActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(facets);FacetActivity active=facets;
                await(()->((GridLayout)objectField(active,"cards")).getChildCount()==2&&((android.widget.Button)objectField(active,"batchArtwork")).isEnabled());
                android.widget.Button batch=(android.widget.Button)objectField(active,"batchArtwork");assertEquals(kind.equals("Types")?"生成封面":"拉取图标",batch.getText().toString());assertEquals(0,server.mutations.size());
                instrumentation.runOnMainSync(()->{batch.requestFocus();batch.performClick();batch.performClick();});
                if(kind.equals("Types")){await(()->server.mutations.size()==1);assertFalse(batch.isEnabled());server.facetGate.countDown();}
                await(()->!Boolean.TRUE.equals(objectField(active,"busy"))&&batch.isEnabled()&&active.status.getText().toString().contains("成功 1 项，失败 1 项"));
                assertEquals(2,server.mutations.size());assertTrue(batch.hasFocus());assertTrue(server.mutations.stream().allMatch(path->path.contains("Action=icon")==kind.equals("Companies")));saveScreenshot(kind.equals("Types")?"type-batch-covers.png":"company-batch-icons.png");
                key(KeyEvent.KEYCODE_BACK);await(active::isDestroyed);facets=null;
            }
        }finally{if(facets!=null){FacetActivity active=facets;instrumentation.runOnMainSync(active::finish);}closePosterFixture();}
        try(Fixture server=new Fixture()){
            server.admin=true;server.browseFeatures=true;server.holdFacet=true;openMenuHomeFixture(server);
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(FacetActivity.class.getName(),null,false);instrumentation.runOnMainSync(()->home.startActivity(new Intent(home,FacetActivity.class).putExtra("parent","library-0").putExtra("kind","Types").putExtra("title","类型")));facets=(FacetActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(facets);FacetActivity active=facets;
            await(()->((android.widget.Button)objectField(active,"batchArtwork")).isEnabled());instrumentation.runOnMainSync(()->((android.widget.Button)objectField(active,"batchArtwork")).performClick());await(()->server.mutations.size()==1);key(KeyEvent.KEYCODE_BACK);await(active::isDestroyed);facets=null;server.facetGate.countDown();settle();assertEquals(1,server.mutations.size());
        }finally{if(facets!=null){FacetActivity active=facets;instrumentation.runOnMainSync(active::finish);}closePosterFixture();}
    }
    private static final class Fixture implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        final byte[] image;
        final byte[] updatedImage;
        final byte[][] logos=new byte[3][];
        volatile boolean admin,canRefresh,favorite,played,deleted,denyMutation,hiddenResume,browseFeatures,facetArt,failFirstFacet,holdFacet,respectLimits;
        volatile String lastResumePath="";
        volatile boolean missingFirstLogo,styledBannerTitles;
        final java.util.concurrent.CountDownLatch facetGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.List<String> latestPaths=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.List<String> imagePaths=new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.List<String> listingPaths=new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.List<String> mutations=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        volatile boolean closed;
        volatile String unexpectedPath;
        volatile String lastListingPath="";
        volatile int listingCalls;
        volatile int listingPageCap=Integer.MAX_VALUE;
        volatile boolean holdNextListing;
        final java.util.concurrent.CountDownLatch listingStarted=new java.util.concurrent.CountDownLatch(1),listingGate=new java.util.concurrent.CountDownLatch(1);
        volatile int refreshStatusRequests;
        volatile String taskStatus="running";
        volatile boolean holdFirstLatest,holdResume,holdCardImages,changed;
        final java.util.concurrent.CountDownLatch cardGate=new java.util.concurrent.CountDownLatch(1),heroLoaded=new java.util.concurrent.CountDownLatch(1),updateGate=new java.util.concurrent.CountDownLatch(1),eventsConnected=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch latestGate=new java.util.concurrent.CountDownLatch(1),resumeGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger views=new java.util.concurrent.atomic.AtomicInteger();
        volatile int viewCalls;
        volatile int folderMediaCount=2;
        volatile int facetCount=2;
        byte[] facetImage;
        Fixture()throws Exception{
            Bitmap bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(45,80,110));
            Paint paint=new Paint();paint.setColor(Color.rgb(225,150,70));canvas.drawRect(0,0,640,18,paint);canvas.drawRect(0,342,640,360,paint);
            for(int y=36;y<324;y+=18)for(int x=0;x<640;x+=18){paint.setColor(((x/18+y/18)%2==0)?Color.rgb(45,80,110):Color.rgb(145,180,210));canvas.drawRect(x,y,x+18,y+18,paint);}
            paint.setColor(Color.WHITE);paint.setTextSize(40);canvas.drawText("TV Focus Fixture",100,190,paint);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes);image=bytes.toByteArray();Bitmap large=Bitmap.createScaledBitmap(bitmap,1066,600,true);ByteArrayOutputStream largeBytes=new ByteArrayOutputStream();large.compress(Bitmap.CompressFormat.JPEG,90,largeBytes);facetImage=largeBytes.toByteArray();large.recycle();bitmap.recycle();
            Bitmap updated=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);updated.eraseColor(Color.MAGENTA);ByteArrayOutputStream newer=new ByteArrayOutputStream();updated.compress(Bitmap.CompressFormat.PNG,100,newer);updated.recycle();updatedImage=newer.toByteArray();
            for(int i=0;i<logos.length;i++){
                String label=i==0?"WIDE TITLE":i==1?"TITLE":"TV";Paint lettering=new Paint(Paint.ANTI_ALIAS_FLAG);lettering.setColor(Color.WHITE);lettering.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);lettering.setTextSize(100);Rect bounds=new Rect();lettering.getTextBounds(label,0,label.length(),bounds);Bitmap logo=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888);new Canvas(logo).drawText(label,-bounds.left,-bounds.top,lettering);ByteArrayOutputStream png=new ByteArrayOutputStream();logo.compress(Bitmap.CompressFormat.PNG,100,png);logo.recycle();logos[i]=png.toByteArray();
            }
            Thread thread=new Thread(()->{while(!closed){try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception ignored){}},"focus-request");worker.setDaemon(true);worker.start();}catch(Exception ignored){if(closed)return;}}},"focus-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void serve(Socket client)throws Exception{
            try(client){
                BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                String request=reader.readLine();if(request==null)return;String method=request.split(" ")[0],path=request.split(" ")[1];while(true){String line=reader.readLine();if(line==null||line.isEmpty())break;}
                if(path.contains("/Images/"))imagePaths.add(path);
                if(!method.equals("GET")){
                    if(path.contains("/FacetArtwork?")){
                        mutations.add(method+" "+path);if(holdFacet)facetGate.await(15,java.util.concurrent.TimeUnit.SECONDS);
                        boolean failed=failFirstFacet&&mutations.stream().filter(value->value.contains("/FacetArtwork?")).count()==1;
                        if(failed){byte[] failure="{}".getBytes(StandardCharsets.US_ASCII);client.getOutputStream().write(("HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));client.getOutputStream().write(failure);return;}
                    }else mutations.add(method+" "+path);if(!denyMutation){if(path.contains("/FacetArtwork?"))facetArt=true;else if(path.contains("/FavoriteItems/"))favorite=method.equals("POST");else if(path.contains("/PlayedItems/"))played=method.equals("POST");else if(path.contains("/HideFromResume"))hiddenResume=true;else if(method.equals("DELETE"))deleted=true;}
                    byte[] result=(denyMutation?new JSONObject().put("detail","权限已撤销"):new JSONObject().put("IsFavorite",favorite).put("Played",played).put("submitted",true).put("RefreshRunId",123)).toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 "+(denyMutation?"403 Forbidden":"200 OK")+"\r\nContent-Type: application/json\r\nContent-Length: "+result.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(result);return;
                }
                if(path.endsWith("/Home/Events")){
                    OutputStream output=client.getOutputStream();output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: home\ndata: {\"revision\":\"v1\"}\n\n".getBytes(StandardCharsets.UTF_8));output.flush();eventsConnected.countDown();
                    updateGate.await(30,java.util.concurrent.TimeUnit.SECONDS);if(!closed){output.write("event: home\ndata: {\"revision\":\"v2\"}\n\nevent: home\ndata: {\"revision\":\"v2\"}\n\n".getBytes(StandardCharsets.UTF_8));output.flush();while(!closed){Thread.sleep(1000);output.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));output.flush();}}return;
                }
                if(path.contains("maxWidth=1280"))heroLoaded.countDown();
                if(path.contains("/Refresh/"))refreshStatusRequests++;
                if(holdCardImages&&path.contains("/Images/")&&!path.contains("maxWidth=1280"))cardGate.await(20,java.util.concurrent.TimeUnit.SECONDS);
                if(path.endsWith("/Views"))viewCalls=views.incrementAndGet();
                if(holdFirstLatest&&path.contains("/Items/Latest?ParentId=library-0&"))latestGate.await(25,java.util.concurrent.TimeUnit.SECONDS);
                if(holdResume&&path.contains("/Items/Resume?"))resumeGate.await(25,java.util.concurrent.TimeUnit.SECONDS);
                if(!path.startsWith("/api/tv/"))unexpectedPath=path; if(path.contains("/Users/")&&(path.contains("/Items?")||path.contains("/Browse?"))){lastListingPath=path;listingCalls++;listingPaths.add(path);}
                if(holdNextListing&&(path.contains("/Items?")||path.contains("/Browse?"))){listingStarted.countDown();listingGate.await(20,java.util.concurrent.TimeUnit.SECONDS);}
                if(path.contains("/Items/Latest?"))latestPaths.add(path);
                if(path.contains("/Items/Resume?"))lastResumePath=path;
                if(missingFirstLogo&&path.contains("/Items/item-0/Images/Logo")){client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;}
                boolean picture=path.contains("/Images/")||path.contains("/FolderCover?")||path.contains("/FacetArtwork?");
                boolean isLogo=path.contains("/Images/Logo");java.util.regex.Matcher logoId=java.util.regex.Pattern.compile("/Items/item-(\\d+)/").matcher(path);int logoIndex=logoId.find()?Integer.parseInt(logoId.group(1))%logos.length:0;
                byte[] body=picture?(isLogo?logos[logoIndex]:path.contains("/FacetArtwork?")&&facetCount>2?facetImage:path.contains("tag=poster2")?updatedImage:image):response(path).toString().getBytes(StandardCharsets.UTF_8);
                OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+(picture?(isLogo?"image/png":"image/jpeg"):"application/json")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
            }
        }
        JSONObject response(String path)throws Exception{
            String limitText=android.net.Uri.parse(path).getQueryParameter("Limit");int limit=respectLimits&&limitText!=null?Integer.parseInt(limitText):60;
            if(path.contains("/Facets?")){
                boolean types=path.contains("Kind=Types");JSONArray rows=new JSONArray();
                for(int i=0;i<facetCount;i++){JSONObject row=new JSONObject().put("Id","facet-"+i).put("Name",types?(i==0?"科幻":"经典"):"中文公司"+i).put("Kind",types?(i==0?"Genres":"Tags"):"Companies").put("Value",types?(i==0?"科幻":"经典"):"中文公司"+i);if(facetArt)row.put("ImageTags",new JSONObject().put("Primary","facet-art"));rows.put(row);}
                return new JSONObject().put("Items",rows).put("TotalRecordCount",facetCount);
            }
            if(browseFeatures&&path.contains("/Folders?")){
                android.net.Uri uri=android.net.Uri.parse(path);String token=uri.getQueryParameter("FolderId"),startText=uri.getQueryParameter("StartIndex");int start=startText==null?0:Integer.parseInt(startText);boolean root=token==null||token.isEmpty();JSONArray rows=new JSONArray();
                for(int i=start;i<Math.min(start+limit,root?70:folderMediaCount);i++){JSONObject row=new JSONObject().put("Id",root?"tv-folder-category"+i:"item-"+i).put("Name",root?"分类目录 "+i:"目录影片 "+i).put("Type",root?"Folder":"Movie").put("IsFolder",root).put("ImageTags",new JSONObject().put("Primary",root?"folder-cover":"poster"));rows.put(row);}
                return new JSONObject().put("Items",rows).put("TotalRecordCount",root?70:folderMediaCount);
            }
            if(browseFeatures&&path.contains("ParentId=library-5")){
                String value=android.net.Uri.parse(path).getQueryParameter("StartIndex");int start=value==null?0:Integer.parseInt(value);JSONArray rows=new JSONArray();for(int i=start;i<Math.min(start+limit,70);i++)rows.put(new JSONObject().put("Id","collection-"+i).put("Name","电影合集 "+i).put("Type","BoxSet"));return new JSONObject().put("Items",rows).put("TotalRecordCount",70);
            }
            if(path.contains("/Refresh/"))return new JSONObject().put("RefreshRunId",123).put("status",taskStatus).put("finished",!taskStatus.equals("running"));
            if(path.contains("check_status"))return new JSONObject().put("user",new JSONObject().put("can_access_discovery",false));
            if(path.contains("/Users/Me"))return new JSONObject().put("Policy",new JSONObject().put("IsAdministrator",admin)).put("TvPermissions",new JSONObject().put("CanRefreshMetadata",canRefresh));
            if(path.contains("/Users/")&&(path.contains("/Items?")||path.contains("/Browse?"))){
                limit=Math.min(limit,listingPageCap);
                int start=0;java.util.regex.Matcher match=java.util.regex.Pattern.compile("[?&]StartIndex=(\\d+)").matcher(path);if(match.find())start=Integer.parseInt(match.group(1));
                JSONArray page=new JSONArray();for(int i=start;i<Math.min(start+limit,120);i++)page.put(new JSONObject().put("Id","item-"+i).put("Name",styledBannerTitles?new String[]{"长安三万里","The Grand Budapest Hotel","这个杀手不太冷：导演剪辑特别版","宇宙探索编辑部"}[i%4]:"海报影片 "+i).put("Type",i%2==0?"Series":"Movie").put("ProductionYear",2020+i%5).put("RecursiveItemCount",12).put("TMDbRating",7.1+(i%10)/10.0).put("DoubanRating",8.1+(i%10)/10.0).put("UserData",new JSONObject().put("UnplayedItemCount",3)).put("ImageTags",new JSONObject().put("Primary","poster")));
                for(int i=0;i<page.length();i++){JSONObject row=page.getJSONObject(i);if(changed)row.put("DoubanRating",9.4).put("TMDbRating",9.0);row.getJSONObject("ImageTags").put("Primary",changed?"poster2":"poster");row.getJSONObject("UserData").put("IsFavorite",favorite).put("Played",played);}
                if(deleted&&start==0)page.remove(0);
                return new JSONObject().put("Items",page).put("TotalRecordCount",deleted?119:120).put("EpisodeCountMode","unplayed");
            }
            boolean libraries=path.endsWith("/Views");JSONArray items=new JSONArray();
            for(int i=0;i<(libraries?6:Math.min(12,limit));i++)items.put(new JSONObject().put("Id",(libraries?"library-":"item-")+i).put("Name",(libraries?"媒体库 ":changed?"更新后的影片 ":"测试影片 ")+(i+1)).put("Type",libraries?"CollectionFolder":"Movie").put("Overview","背景简介").put("ProductionYear",2026).put("CollectionType",libraries?"mixed":"").put("SourceType",libraries?"path":"").put("RunTimeTicks",1000).put("UserData",new JSONObject().put("PlaybackPositionTicks",300)));
            if(browseFeatures&&libraries){items.getJSONObject(0).put("CollectionType","movies");items.getJSONObject(1).put("SourceType","filter").put("CollectionType","tvshows");items.getJSONObject(5).put("SourceType","collection").put("CollectionType","boxsets").put("Name","合集");}
            if(hiddenResume&&path.contains("/Items/Resume?"))items.remove(0);
            for(int i=0;i<items.length();i++)items.getJSONObject(i).getJSONObject("UserData").put("IsFavorite",favorite).put("Played",played);
            return new JSONObject().put("Items",items);
        }
        @Override public void close()throws IOException{closed=true;latestGate.countDown();resumeGate.countDown();cardGate.countDown();updateGate.countDown();listingGate.countDown();socket.close();}
    }
}
