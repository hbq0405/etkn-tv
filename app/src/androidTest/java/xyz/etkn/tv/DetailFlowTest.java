package xyz.etkn.tv;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Only the in-emulator synthetic service is used, including real Media3 track selection. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class DetailFlowTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    @org.junit.Before public void clearPreviousLoopbackFixture(){if(new SessionStore(instrumentation.getTargetContext()).server().startsWith("http://127.0.0.1:")){instrumentation.getTargetContext().getSharedPreferences("session",0).edit().clear().commit();SessionStore.endTemporarySession();}}
    @Test public void genresTagsAndCollectionsOpenFocusableLibraryPages()throws Exception{
        HomeActivity browser=null;
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;open(server,"movie-single");await(()->button("系列")!=null&&button("系列").isShown());
            ViewGroup facets=(ViewGroup)field(detail,"facetButtons");assertSame(facets,button("系列").getParent());assertEquals(facets.getChildCount()-1,facets.indexOfChild(button("系列")));assertEquals(facets.indexOfChild(button("标签 · 经典"))+1,facets.indexOfChild(button("系列")));assertNull(button("合成合集"));
            for(String title:new String[]{"题材 · 剧情","标签 · 经典","系列"}){
                Button target=button(title);assertNotNull(target);if(!title.equals("系列")){onMain(()->assertEquals(105,Color.alpha(((android.graphics.drawable.GradientDrawable)target.getBackground().getCurrent()).getColor().getDefaultColor())));}onMain(target::requestFocus);settle();assertTrue(target.hasFocus());
                if(title.startsWith("标签"))screenshot("detail-facet-focus.png");if(title.equals("系列"))screenshot("detail-series-inline.png");
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(HomeActivity.class.getName(),null,false);onMain(target::performClick);browser=(HomeActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(browser);
                HomeActivity active=browser;await(()->field(active,"posterGrid") instanceof GridLayout&&((GridLayout)field(active,"posterGrid")).getChildCount()>0);
                if(title.startsWith("题材"))assertTrue(server.lastBrowsePath.contains("Genres="));else if(title.startsWith("标签"))assertTrue(server.lastBrowsePath.contains("Tags="));else assertTrue(server.lastBrowsePath.contains("ParentId=collection-1"));
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(()->active.isDestroyed()&&detail.hasWindowFocus());browser=null;
            }
            assertNull(server.unexpectedPath);
        }finally{if(browser!=null){HomeActivity active=browser;onMain(active::finish);}cleanup();}
    }
    private DetailActivity detail;
    @Test public void backdropAndLogoLoadTogetherWithoutFlashingTheFallbackTitle()throws Exception{
        try(Fixture server=new Fixture()){
            server.holdIdentity=true;open(server,"movie-single");
            assertTrue("背景请求已开始",server.backgroundRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue("Logo请求不能等待背景响应",server.logoRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));
            await(()->((View)field(detail,"detailLogo")).isShown());
            onMain(()->{assertEquals(View.GONE,((View)field(detail,"detailTitle")).getVisibility());assertEquals("背景响应仍被阻塞",1L,server.backdropGate.getCount());});
            screenshot("detail-logo-with-slow-backdrop.png");server.backdropGate.countDown();
            open(server,"movie-multi");await(()->((View)field(detail,"detailTitle")).isShown());
            onMain(()->assertEquals(View.GONE,((View)field(detail,"detailLogo")).getVisibility()));
        }finally{cleanup();}
    }
    private PlayerActivity playback;
    private void onMain(Runnable action){instrumentation.runOnMainSync(action);}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){boolean[] value={false};onMain(()->value[0]=condition.getAsBoolean());if(value[0])return;Thread.sleep(100);}fail("Timed out waiting for detail/playback state");}
    private void settle(){instrumentation.waitForIdleSync();SystemClock.sleep(450);instrumentation.waitForIdleSync();}
    private Object field(Object owner,String name){try{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    private List<View> descendants(View root){List<View> views=new ArrayList<>();views.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)views.addAll(descendants(((ViewGroup)root).getChildAt(i)));return views;}
    private Button button(String prefix){for(View view:descendants(detail.content))if(view instanceof Button&&(((Button)view).getText().toString().startsWith(prefix)||view.getContentDescription()!=null&&view.getContentDescription().toString().equals(prefix)))return (Button)view;return null;}
    private View contentDescription(String value){for(View view:descendants(detail.content))if(value.equals(view.getContentDescription()))return view;return null;}
    private Button findButton(View root,String title){for(View view:descendants(root))if(view instanceof Button&&title.equals(((Button)view).getText().toString()))return (Button)view;return null;}
    private void clickTrack(boolean subtitles,int index)throws Exception{onMain(()->((Button)field(playback,subtitles?"subtitleButton":"audioButton")).performClick());settle();onMain(()->{ListView list=((AlertDialog)field(playback,"trackDialog")).getListView();list.performItemClick(null,index,list.getAdapter().getItemId(index));});settle();}
    private void open(Fixture server,String id)throws Exception{
        new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"detail-fixture","详情测试","synthetic-test-session");
        detail=(DetailActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),DetailActivity.class).putExtra("id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->button("播放")!=null&&button("播放").isEnabled()||button("继续播放")!=null);settle();
        assertTrue("初始焦点应为播放/继续播放",detail.getCurrentFocus()==field(detail,"playButton"));
        assertEquals(0,((ScrollView)field(detail,"scroll")).getScrollY());
        assertNull(button("返回"));assertNull(button("媒体详情"));
    }
    private void choose(String prefix,int down){Button button=button(prefix);assertNotNull(button);onMain(button::performClick);settle();for(int i=0;i<down;i++)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();}
    private void play()throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        onMain(()->((Button)field(detail,"playButton")).performClick());playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
        await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
        onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.pause();for(Tracks.Group group:player.getCurrentTracks().getGroups())for(int i=0;i<group.length;i++){Format format=group.getTrackFormat(i);android.util.Log.i("DetailTracks","type="+group.getType()+" id="+format.id+" label="+format.label+" selected="+group.isTrackSelected(i)+" supported="+group.isTrackSupported(i));}});settle();
    }
    private String selected(int type){ExoPlayer player=(ExoPlayer)field(playback,"player");for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i))return PlaybackSelection.localTrackId(group.getTrackFormat(i).id);return "";}
    private void closePlayer(){if(playback!=null){onMain(()->playback.finish());settle();playback=null;}}
    private void cleanup(){closePlayer();if(detail!=null){onMain(()->detail.finish());detail=null;}new SessionStore(instrumentation.getTargetContext()).clear();}
    private void screenshot(String name)throws Exception{File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"detail-regression");assertTrue(dir.isDirectory()||dir.mkdirs());Bitmap image=instrumentation.getUiAutomation().takeScreenshot();try(FileOutputStream stream=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}image.recycle();}

    @Test public void translatedStudiosSitBelowOverviewAndOpenTheirOwnMedia()throws Exception{
        HomeActivity browser=null;
        try(Fixture server=new Fixture()){
            server.browseFeatures=true;server.studioIcons=true;
            for(String id:new String[]{"movie-single","series-one"}){
                open(server,id);ViewGroup studios=(ViewGroup)field(detail,"studioButtons");assertEquals(2,studios.getChildCount());
                assertEquals(id.startsWith("movie")?"出品：":"播出：",((TextView)studios.getChildAt(0)).getText().toString());
                assertEquals(24f,((TextView)studios.getChildAt(0)).getTextSize()/detail.getResources().getDisplayMetrics().scaledDensity,0.01f);
                View overview=(View)field(detail,"detailOverview");ViewGroup parent=(ViewGroup)overview.getParent();assertEquals(parent.indexOfChild(overview)+1,parent.indexOfChild((View)studios.getParent()));
                Button name=(Button)((ViewGroup)studios.getChildAt(1)).getChildAt(0);if(id.startsWith("movie")){await(()->name.getText().length()==0&&((ViewGroup)studios.getChildAt(1)).getChildAt(1).getVisibility()==View.VISIBLE);assertEquals("中文公司",name.getContentDescription().toString());assertEquals(detail.dp(104),studios.getChildAt(1).getWidth());assertEquals(name.getWidth(),((ViewGroup)studios.getChildAt(1)).getChildAt(1).getWidth());android.widget.ImageView logo=(android.widget.ImageView)((ViewGroup)studios.getChildAt(1)).getChildAt(1);assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP,logo.getScaleType());assertEquals(0,logo.getPaddingLeft()+logo.getPaddingRight()+logo.getPaddingTop()+logo.getPaddingBottom());assertTrue(logo.getClipToOutline());assertTrue(studios.getChildAt(1).getClipToOutline());assertNull(name.getBackground());
                Bitmap pixels=Bitmap.createBitmap(logo.getWidth(),logo.getHeight(),Bitmap.Config.ARGB_8888);onMain(()->logo.draw(new Canvas(pixels)));assertEquals("Artwork must fill the middle of the left edge",255,Color.alpha(pixels.getPixel(0,pixels.getHeight()/2)));assertEquals(255,Color.alpha(pixels.getPixel(pixels.getWidth()-1,pixels.getHeight()/2)));pixels.recycle();}else{await(()->server.imagePaths.stream().anyMatch(path->path.contains("/FacetArtwork?")));assertEquals("中文平台",name.getText().toString());}
                onMain(name::requestFocus);settle();assertTrue(name.hasFocus());screenshot(id+"-studios-below-overview.png");if(id.startsWith("movie")){assertNotNull(studios.getChildAt(1).getForeground());android.graphics.Outline outline=new android.graphics.Outline();studios.getChildAt(1).getOutlineProvider().getOutline(studios.getChildAt(1),outline);assertEquals(detail.dp(9),outline.getRadius(),0.01f);
                android.widget.ImageView logo=(android.widget.ImageView)((ViewGroup)studios.getChildAt(1)).getChildAt(1);int[] location=new int[2];onMain(()->logo.getLocationOnScreen(location));Bitmap source=Bitmap.createBitmap(logo.getWidth(),logo.getHeight(),Bitmap.Config.ARGB_8888);onMain(()->logo.draw(new Canvas(source)));Bitmap screen=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(screen);assertTrue("Hardware rounded corner must reveal the page behind the image",source.getPixel(0,0)!=screen.getPixel(location[0],location[1]));screen.recycle();source.recycle();}
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(HomeActivity.class.getName(),null,false);onMain(name::performClick);browser=(HomeActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(browser);
                HomeActivity active=browser;await(()->field(active,"posterGrid") instanceof GridLayout&&((GridLayout)field(active,"posterGrid")).getChildCount()>0);
                assertTrue(server.lastBrowsePath.contains(id.startsWith("movie")?"Companies=":"Networks="));assertFalse(server.lastBrowsePath.contains("null"));
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(()->active.isDestroyed()&&detail.hasWindowFocus());browser=null;assertTrue(name.hasFocus());
                onMain(detail::finish);detail=null;
            }
        }finally{if(browser!=null){HomeActivity active=browser;onMain(active::finish);}cleanup();}
    }

    @Test public void playbackUsesEntireViewportHasNoCenterControlsAndConfirmTogglesPlayback()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-single");play();
            onMain(()->{
                View video=(View)field(playback,"video"),decor=playback.getWindow().getDecorView();
                assertEquals("视频必须占满视口宽度",decor.getWidth(),video.getWidth());
                assertEquals("视频必须占满视口高度",decor.getHeight(),video.getHeight());
                assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());
            });
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            onMain(()->{assertTrue(((ExoPlayer)field(playback,"player")).getPlayWhenReady());assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            onMain(()->{assertFalse(((ExoPlayer)field(playback,"player")).getPlayWhenReady());assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
            onMain(()->assertEquals(View.VISIBLE,((View)field(playback,"controls")).getVisibility()));
            onMain(()->{
                View video=(View)field(playback,"video");
                for(int id:new int[]{androidx.media3.ui.R.id.exo_center_controls,androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_prev,androidx.media3.ui.R.id.exo_next,androidx.media3.ui.R.id.exo_rew_with_amount,androidx.media3.ui.R.id.exo_ffwd_with_amount})assertNull("整组中间控件必须从视图中移除",video.findViewById(id));
                assertEquals(View.GONE,((View)field(playback,"nextButton")).getVisibility());
            });
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
            assertFalse("返回先收起控制栏",playback.isFinishing());
            assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());assertEquals(0,server.stopped.size());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();assertTrue(playback.isFinishing());
            await(()->field(playback,"player")==null&&server.stopped.size()==1&&detail.hasWindowFocus());
        }finally{cleanup();}
    }

    @Test public void backHidesSeekBeforeExitingAndReportsCurrentPosition()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);
            for(boolean seeking:new boolean[]{false,true}){
                int stoppedBefore=server.stopped.size();play();
                onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).isCurrentMediaItemSeekable()));
                if(seeking){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();}
                else onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(4000);player.play();});
                await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.getCurrentPosition()>=1000&&(seeking||player.isPlaying());});
                long[] position={0};onMain(()->{
                    assertEquals(seeking,((View)field(playback,"seekProgress")).isShown());
                    assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());
                    position[0]=((ExoPlayer)field(playback,"player")).getCurrentPosition();
                });
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
                if(seeking){assertFalse("快进后返回先收起提示",playback.isFinishing());assertEquals(stoppedBefore,server.stopped.size());assertFalse(((View)field(playback,"seekProgress")).isShown());instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();}
                assertTrue("无浮层时返回退出",playback.isFinishing());
                await(()->field(playback,"player")==null&&server.stopped.size()==stoppedBefore+1&&detail.hasWindowFocus());
                JSONObject stopped=server.stopped.get(stoppedBefore);
                assertEquals("movie-single",stopped.getString("ItemId"));assertTrue(stopped.getLong("PositionTicks")>=position[0]*10000);
                if(seeking)assertEquals(position[0]*10000,stopped.getLong("PositionTicks"));
                closePlayer();
            }
        }finally{cleanup();}
    }
    @Test public void endOfMovieOrLastEpisodeReturnsToDetailsAndReportsFinalPositionOnce()throws Exception{
        try(Fixture server=new Fixture(true)){
            for(String id:new String[]{"movie-single","episode-1-24"}){
                if(detail!=null)onMain(()->detail.finish());open(server,id);new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);
                int before=server.stopped.size();play();long[] duration={0};
                onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");duration[0]=player.getDuration();player.seekTo(duration[0]-300);player.play();});
                await(()->playback.isFinishing()&&field(playback,"player")==null&&detail.hasWindowFocus()&&server.stopped.size()>before);settle();
                assertEquals("结束退出只应回报一次停止",before+1,server.stopped.size());
                assertTrue("必须保存最终进度",server.stopped.get(before).getLong("PositionTicks")>=(duration[0]-300)*10000);
                closePlayer();
            }
        }finally{cleanup();}
    }

    private void endAndPlayNext()throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        PlayerActivity previous=playback;
        try{
            onMain(()->{ExoPlayer player=(ExoPlayer)field(previous,"player");if(player.isCurrentMediaItemSeekable())player.seekTo(player.getDuration()-300);player.play();});
            PlayerActivity next=(PlayerActivity)monitor.waitForActivityWithTimeout(30000);
            String[] state={""};onMain(()->{ExoPlayer player=(ExoPlayer)field(previous,"player");state[0]=" finishing="+previous.isFinishing()+" nextLoaded="+field(previous,"nextLoaded")+" next="+field(previous,"nextEpisode")+(player==null?" released":" state="+player.getPlaybackState()+" position="+player.getCurrentPosition()+" duration="+player.getDuration()+" seekable="+player.isCurrentMediaItemSeekable());});
            assertNotNull("有后续可播放内容时应自动继续播放"+state[0],next);playback=next;
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
            onMain(()->{assertTrue("下一集应自动开始播放",((ExoPlayer)field(playback,"player")).getPlayWhenReady());((ExoPlayer)field(playback,"player")).pause();});
            assertTrue(previous.isFinishing());assertNull(field(previous,"player"));
        }finally{instrumentation.removeMonitor(monitor);}
    }
    @Test public void endedEpisodeAutomaticallyContinuesAndPreservesSeasonAudio()throws Exception{
        try(Fixture server=new Fixture()){
            server.ordinalFixture=true;open(server,"episode-1-2");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);
            play();clickTrack(false,2);await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));
            await(()->Boolean.TRUE.equals(field(playback,"nextLoaded")));server.audioOffset=20;
            long[] duration={0};onMain(()->duration[0]=((ExoPlayer)field(playback,"player")).getDuration());endAndPlayNext();
            assertEquals("episode-1-3",playback.getIntent().getStringExtra("id"));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(24,field(playback,"audioIndex"));
            await(()->server.stopped.size()==1);settle();assertEquals(1,server.stopped.size());
            assertEquals("episode-1-2",server.stopped.get(0).getString("ItemId"));
            assertTrue(server.stopped.get(0).getLong("PositionTicks")>=(duration[0]-300)*10000);
        }finally{cleanup();}
    }
    @Test public void endedEpisodeWaitsForNextLookupThenContinuesOrExitsAtLastEpisode()throws Exception{
        try(Fixture server=new Fixture(true)){
            for(String id:new String[]{"episode-1-2","episode-1-24"}){
                if(detail!=null)onMain(()->detail.finish());open(server,id);new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);
                server.nextRequested=new java.util.concurrent.CountDownLatch(1);server.nextGate=new java.util.concurrent.CountDownLatch(1);
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
                try{
                    play();assertTrue(server.nextRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));monitor.waitForActivityWithTimeout(1000);
                    PlayerActivity previous=playback;int before=server.stopped.size();
                    onMain(()->{ExoPlayer player=(ExoPlayer)field(previous,"player");player.seekTo(player.getDuration()-300);player.play();});
                    await(()->previous.isFinishing()||((ExoPlayer)field(previous,"player")).getPlaybackState()==Player.STATE_ENDED);
                    assertFalse("查询尚未完成时不能误判为末集退出",previous.isFinishing());
                    assertEquals(true,field(previous,"pendingAdvance"));server.nextGate.countDown();
                    if(id.equals("episode-1-2")){
                        PlayerActivity next=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull(next);playback=next;
                        assertEquals("episode-1-3",playback.getIntent().getStringExtra("id"));
                        await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
                        onMain(()->((ExoPlayer)field(playback,"player")).pause());
                    }else await(()->previous.isFinishing()&&detail.hasWindowFocus());
                    await(()->field(previous,"player")==null&&server.stopped.size()==before+1);closePlayer();
                }finally{server.nextGate.countDown();server.nextGate=null;instrumentation.removeMonitor(monitor);}
            }
        }finally{cleanup();}
    }
    @Test public void queueStillAutomaticallyPlaysNextItemThenExits()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);
            playback=(PlayerActivity)instrumentation.startActivitySync(new Intent(detail,PlayerActivity.class).putExtra("id","movie-single").putExtra("resume",true).putExtra("resolved",true).putExtra("queue",new String[]{"movie-single","movie-next"}).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
            onMain(()->((ExoPlayer)field(playback,"player")).pause());endAndPlayNext();
            assertEquals("movie-next",playback.getIntent().getStringExtra("id"));assertEquals(1,playback.getIntent().getIntExtra("queueIndex",-1));
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(player.getDuration()-300);player.play();});
            await(()->playback.isFinishing()&&field(playback,"player")==null&&detail.hasWindowFocus()&&server.stopped.size()==2);
            assertEquals("movie-single",server.stopped.get(0).getString("ItemId"));assertEquals("movie-next",server.stopped.get(1).getString("ItemId"));
        }finally{cleanup();}
    }

    private void nextByRemote()throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        PlayerActivity previous=playback;
        onMain(()->((androidx.media3.ui.PlayerView)field(playback,"video")).hideController());settle();
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
        assertSame(field(playback,"nextButton"),playback.getCurrentFocus());
        ViewGroup actions=(ViewGroup)((View)field(playback,"nextButton")).getParent();assertEquals(3,actions.indexOfChild((View)field(playback,"nextButton")));assertEquals(4,actions.indexOfChild((View)field(playback,"speedButton")));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);assertSame(field(playback,"subtitleButton"),playback.getCurrentFocus());instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);assertSame(field(playback,"speedButton"),playback.getCurrentFocus());instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);screenshot("next-episode-controls.png");
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
        await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
        onMain(()->((ExoPlayer)field(playback,"player")).pause());settle();assertTrue(previous.isFinishing());
    }
    @Test public void nextEpisodeStopsOldPlaybackAndRestoresSeasonAudioByOrdinal()throws Exception{
        try(Fixture server=new Fixture()){
            server.ordinalFixture=true;open(server,"episode-1-2");play();clickTrack(false,2);await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));
            onMain(()->((ExoPlayer)field(playback,"player")).play());await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=1000);onMain(()->((ExoPlayer)field(playback,"player")).pause());
            await(()->((View)field(playback,"nextButton")).getVisibility()==View.VISIBLE);server.audioOffset=20;nextByRemote();
            assertEquals("episode-1-3",playback.getIntent().getStringExtra("id"));assertEquals(0,playback.getIntent().getLongExtra("position",0));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(24,field(playback,"audioIndex"));
            await(()->server.stopped.stream().anyMatch(event->"episode-1-2".equals(event.optString("ItemId"))));
            JSONObject stopped=server.stopped.stream().filter(event->"episode-1-2".equals(event.optString("ItemId"))).findFirst().get();assertTrue("切集必须保存旧集播放进度",stopped.optLong("PositionTicks")>=10000000);
            assertEquals(1,new TvPreferences(playback,playback.api).seasonAudio("season-1"));
        }finally{cleanup();}
    }
    @Test public void delayedNextEpisodeDefaultsFocusUnlessUserAlreadyNavigated()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"episode-1-2");
            for(boolean navigate:new boolean[]{false,true}){
                server.nextRequested=new java.util.concurrent.CountDownLatch(1);server.nextGate=new java.util.concurrent.CountDownLatch(1);play();
                assertTrue(server.nextRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertSame(field(playback,"audioButton"),playback.getCurrentFocus());
                if(navigate){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);settle();}
                View selected=playback.getCurrentFocus();server.nextGate.countDown();
                await(()->((View)field(playback,"nextButton")).getVisibility()==View.VISIBLE);settle();
                assertSame(navigate?selected:field(playback,"nextButton"),playback.getCurrentFocus());closePlayer();
            }
        }finally{cleanup();}
    }
    @Test public void endedEpisodeCrossesSeasonAndUsesTargetSeasonLockThenHidesAtSeriesEnd()throws Exception{
        try(Fixture server=new Fixture()){
            server.ordinalFixture=true;open(server,"series-many");
            new TvPreferences(detail,detail.api).saveSeasonAudio("season-1",1);
            new TvPreferences(detail,detail.api).saveSeasonAudio("season-2",0);
            onMain(()->button("第 1 季").performClick());settle();
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
            onMain(()->button("第 24 集").performClick());playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);onMain(()->((ExoPlayer)field(playback,"player")).pause());
            await(()->((View)field(playback,"nextButton")).getVisibility()==View.VISIBLE);endAndPlayNext();
            assertEquals("episode-2-1",playback.getIntent().getStringExtra("id"));assertEquals("season-2",playback.getIntent().getStringExtra("seasonKey"));
            await(()->"1".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(0,field(playback,"audioIndex"));
            closePlayer();onMain(()->detail.finish());open(server,"episode-1-24");play();settle();assertEquals(View.GONE,((View)field(playback,"nextButton")).getVisibility());
        }finally{cleanup();}
    }
    @Test public void resumeNextEpisodeLookupFailureDoesNotInterruptPlayback()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"episode-1-2");server.failNext=true;
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
            onMain(()->detail.resumePlayback((JSONObject)field(detail,"playable")));
            playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);settle();
            assertEquals("series-one",playback.getIntent().getStringExtra("seriesId"));
            onMain(()->{assertEquals(View.GONE,((View)field(playback,"nextButton")).getVisibility());assertEquals(View.GONE,playback.status.getVisibility());assertNull(((ExoPlayer)field(playback,"player")).getPlayerError());});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertSame(field(playback,"audioButton"),playback.getCurrentFocus());
        }finally{cleanup();}
    }

    @Test public void directResumeStartsDefaultVersionAtSavedPositionWithTransparentTextCaptions()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
            onMain(()->{try{detail.resumePlayback(new JSONObject().put("Id","movie-resume").put("Type","Movie").put("UserData",new JSONObject().put("PlaybackPositionTicks",40000000)));}catch(Exception error){throw new AssertionError(error);}});
            playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.pause();assertTrue(player.getCurrentPosition()>=4000);assertEquals(0,field(playback,"selected"));
                androidx.media3.ui.SubtitleView subtitles=((androidx.media3.ui.PlayerView)field(playback,"video")).getSubtitleView();
                try{java.lang.reflect.Field style=androidx.media3.ui.SubtitleView.class.getDeclaredField("style");style.setAccessible(true);androidx.media3.ui.CaptionStyleCompat caption=(androidx.media3.ui.CaptionStyleCompat)style.get(subtitles);assertEquals(Color.TRANSPARENT,caption.backgroundColor);assertEquals(Color.TRANSPARENT,caption.windowColor);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
        }finally{cleanup();}
    }

    @Test public void playerUsesSubtitleTitlesExactDefaultsAndPreservesUnavailableTracks()throws Exception{
        try(Fixture server=new Fixture()){
            server.preferSecond=true;open(server,"movie-single");assertTrue(button("音轨").getText().toString().contains("音轨二"));assertTrue(button("字幕").getText().toString().contains("内封二"));play();
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO))&&"4".equals(selected(C.TRACK_TYPE_TEXT)));
            assertTrue(((Button)field(playback,"audioButton")).getText().toString().contains("音轨二"));clickTrack(false,1);await(()->"1".equals(selected(C.TRACK_TYPE_AUDIO)));
            assertTrue(((Button)field(playback,"audioButton")).getText().toString().contains("音轨一"));
            onMain(()->((Button)field(playback,"subtitleButton")).performClick());settle();
            screenshot("player-subtitle-dialog.png");
            onMain(()->{ListView list=((AlertDialog)field(playback,"trackDialog")).getListView();assertEquals(5,list.getCount());assertEquals("内封二 · 副标题",list.getAdapter().getItem(0));assertEquals("内封一 · 副标题",list.getAdapter().getItem(2));((AlertDialog)field(playback,"trackDialog")).dismiss();});closePlayer();
            server.extraTrack=true;play();onMain(()->((Button)field(playback,"audioButton")).performClick());settle();
            onMain(()->{AlertDialog dialog=(AlertDialog)field(playback,"trackDialog");ListView list=dialog.getListView();assertEquals("全部服务端音轨必须保留",4,list.getCount());assertTrue(list.getAdapter().getItem(3).toString().contains("未解析音轨"));list.performItemClick(null,3,3);assertTrue("无法可靠对应的轨道不可静默选错",dialog.isShowing());assertEquals(PlaybackSelection.AUTO,field(playback,"audioIndex"));dialog.dismiss();});
        }finally{cleanup();}
    }

    @Test public void continueWatchingEpisodeIncludesTheOtherEpisodes()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"episode-1-2");assertNotNull(button("第 1 集"));assertNotNull(button("第 24 集"));assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 2"));assertEquals("episode-1-2",((JSONObject)field(detail,"playable")).getString("Id"));screenshot("resume-episode-selection.png");
        }finally{cleanup();}
    }

    @Test public void matrixEntryUsesFirstOrResumeEpisodeAndMarksWatchedEpisodes()throws Exception{
        try(Fixture server=new Fixture()){
            server.noProgress=true;open(server,"series-one");assertEquals("episode-1-1",((JSONObject)field(detail,"playable")).optString("Id"));
            assertTrue(button("第 1 集").getText().toString().contains("✓"));assertTrue(button("第 1 集").getContentDescription().toString().contains("已观看"));
            onMain(()->{GridLayout grid=(GridLayout)field(detail,"episodeGrid");assertTrue("最后一列应铺满可用宽度",Math.abs(grid.getWidth()-grid.getChildAt(grid.getColumnCount()-1).getRight())<=2);});
            onMain(()->button("音轨").requestFocus());for(int i=0;i<8&&detail.getCurrentFocus()!=field(detail,"episodeEntry");i++){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();}assertSame(button("第 1 集"),detail.getCurrentFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();assertSame(button("第 2 集"),detail.getCurrentFocus());screenshot("watched-and-filled-matrix.png");
            onMain(()->detail.finish());server.noProgress=false;open(server,"series-one");
            onMain(()->{android.graphics.drawable.LayerDrawable background=(android.graphics.drawable.LayerDrawable)button("第 3 集").getBackground();assertEquals(3000,background.getDrawable(1).getLevel());assertTrue(button("第 3 集").getContentDescription().toString().contains("30%"));});
            onMain(()->button("音轨").requestFocus());for(int i=0;i<8&&detail.getCurrentFocus()!=field(detail,"episodeEntry");i++){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();}assertSame(button("第 3 集"),detail.getCurrentFocus());screenshot("resume-progress-matrix.png");android.os.SystemClock.sleep(1200);screenshot("focus-pulse-matrix.png");
        }finally{cleanup();}
    }

    @Test public void seasonAudioOrdinalSurvivesRecreationAndMissingTrackWithoutTrustingTitles()throws Exception{
        try(Fixture server=new Fixture()){
            server.ordinalFixture=true;open(server,"episode-1-2");play();clickTrack(false,2);
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));
            TvPreferences preferences=new TvPreferences(detail,detail.api);assertEquals(1,preferences.seasonAudio("season-1"));assertEquals(-1,preferences.seasonAudio("season-2"));
            closePlayer();onMain(()->detail.finish());server.audioOffset=20;
            open(server,"episode-1-3");assertTrue(button("音轨").getText().toString().contains("本季第 2 音轨"));play();
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(24,field(playback,"audioIndex"));closePlayer();
            server.missingAudio=true;play();assertEquals(PlaybackSelection.AUTO,field(playback,"audioIndex"));assertEquals(1,field(playback,"missingSeasonAudio"));assertEquals(1,new TvPreferences(playback,playback.api).seasonAudio("season-1"));closePlayer();
            server.missingAudio=false;play();await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));clickTrack(false,0);await(()->"1".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(-1,preferences.seasonAudio("season-1"));
        }finally{cleanup();}
    }

    @Test public void settingsSyncPersonalPrioritiesAndCanReturnToGlobal()throws Exception{
        SettingsActivity[] settings={null};
        try(Fixture server=new Fixture()){
            open(server,"movie-single");settings[0]=(SettingsActivity)instrumentation.startActivitySync(new Intent(detail,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->((Button)field(settings[0],"defaultAudio")).isEnabled());assertTrue(((Button)field(settings[0],"defaultAudio")).getText().toString().contains("国语 → 原语言 → 英语（全局）"));
            onMain(()->((Button)field(settings[0],"defaultAudio")).performClick());settle();AlertDialog editor=(AlertDialog)field(settings[0],"preferenceDialog");
            onMain(()->findButton(editor.getWindow().getDecorView(),"添加优先项").performClick());settle();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            onMain(()->findButton(editor.getWindow().getDecorView(),"添加优先项").performClick());settle();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            onMain(()->findButton(editor.getWindow().getDecorView(),"上移").performClick());settle();onMain(()->editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(()->((Button)field(settings[0],"defaultAudio")).getText().toString().contains("英语 → 国语（个人）"));assertEquals("[\"eng\",\"chi\"]",server.personal.optJSONArray("audio_lang_priority").toString());assertEquals("[\"chs\"]",server.personal.optJSONArray("subtitle_priority").toString());
            onMain(()->((Button)field(settings[0],"skipButton")).performClick());settle();onMain(()->{ListView choices=((AlertDialog)field(settings[0],"preferenceDialog")).getListView();choices.performItemClick(null,TvPreferences.SKIP_AUTO,TvPreferences.SKIP_AUTO);});onMain(()->settings[0].finish());settle();
            settings[0]=(SettingsActivity)instrumentation.startActivitySync(new Intent(detail,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->((Button)field(settings[0],"defaultAudio")).isEnabled());assertTrue(((Button)field(settings[0],"defaultAudio")).getText().toString().contains("英语 → 国语（个人）"));assertTrue(((Button)field(settings[0],"skipButton")).getText().toString().contains("自动跳过"));
            onMain(()->((Button)field(settings[0],"defaultAudio")).performClick());settle();onMain(()->((AlertDialog)field(settings[0],"preferenceDialog")).getButton(AlertDialog.BUTTON_NEUTRAL).performClick());await(()->((Button)field(settings[0],"defaultAudio")).getText().toString().contains("全局"));assertTrue(server.personal.isNull("audio_lang_priority"));
        }finally{if(settings[0]!=null)onMain(()->settings[0].finish());cleanup();}
    }

    @Test public void actorAndSimilarCardsOpenTheirDetailsAndAssociatedMedia()throws Exception{
        PersonActivity person=null;DetailActivity linked=null;
        try(Fixture server=new Fixture()){
            open(server,"movie-single");await(()->descendants(detail.content).stream().anyMatch(view->view instanceof GridLayout&&((GridLayout)view).getChildCount()>0));
            View actor=descendants(detail.content).stream().filter(view->view instanceof TextView&&"合成演员".equals(((TextView)view).getText().toString())).findFirst().get();
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PersonActivity.class.getName(),null,false);onMain(()->((View)actor.getParent()).performClick());person=(PersonActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(person);PersonActivity opened=person;
            await(()->descendants(opened.content).stream().anyMatch(view->view instanceof TextView&&"合成人物简介".equals(((TextView)view).getText().toString()))&&((GridLayout)field(opened,"works")).getChildCount()>0);
            Instrumentation.ActivityMonitor media=instrumentation.addMonitor(DetailActivity.class.getName(),null,false);onMain(()->((GridLayout)field(opened,"works")).getChildAt(0).performClick());linked=(DetailActivity)media.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(media);assertNotNull(linked);assertEquals("movie-person",linked.getIntent().getStringExtra("id"));DetailActivity target=linked;onMain(target::finish);linked=null;onMain(opened::finish);person=null;settle();
            View similar=descendants(detail.content).stream().filter(view->view instanceof GridLayout).findFirst().get();Instrumentation.ActivityMonitor more=instrumentation.addMonitor(DetailActivity.class.getName(),null,false);onMain(()->((GridLayout)similar).getChildAt(0).performClick());linked=(DetailActivity)more.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(more);assertNotNull(linked);assertEquals("movie-similar",linked.getIntent().getStringExtra("id"));
        }finally{if(linked!=null){DetailActivity target=linked;onMain(target::finish);}if(person!=null){PersonActivity target=person;onMain(target::finish);}cleanup();}
    }

    @Test public void hevcAndAudioCodecsActuallyDecode()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");play();
            assertTrue(io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.isAvailable());
            for(String mime:new String[]{MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_TRUEHD,MimeTypes.VIDEO_H265})assertTrue(mime,io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.supportsFormat(mime));
            String[] mimes={MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_TRUEHD};
            for(int i=0;i<mimes.length;i++){
                onMain(()->((ExoPlayer)field(playback,"player")).pause());
                clickTrack(false,i+1);String mime=mimes[i];onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(1000);player.play();});
                try{await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.getPlayerError()==null&&player.getAudioFormat()!=null&&mime.equals(player.getAudioFormat().sampleMimeType)&&player.getAudioDecoderCounters()!=null&&player.getAudioDecoderCounters().renderedOutputBufferCount>0;});}
                catch(AssertionError error){onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");android.util.Log.e("CodecValidation","wanted="+mime+" format="+player.getAudioFormat()+" state="+player.getPlaybackState()+" position="+player.getCurrentPosition()+" error="+player.getPlayerError()+" buffers="+(player.getAudioDecoderCounters()==null?-1:player.getAudioDecoderCounters().renderedOutputBufferCount));});throw error;}
                onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");boolean nativeAudio=false;for(int r=0;r<player.getRendererCount();r++)if(player.getRenderer(r).getState()!=0&&player.getRenderer(r).getName().contains("FfmpegAudio"))nativeAudio=true;assertTrue("音频必须实际由原生FFmpeg解码："+mime,nativeAudio);assertTrue(player.getVideoDecoderCounters().renderedOutputBufferCount>0);});
            }
        }finally{cleanup();}
    }

    @Test public void assProducesVisiblePixels()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");play();
            for(int i=0;i<3;i++){onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(1000);player.play();});await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>1300);}
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(4000);player.play();});
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.isPlaying()&&player.getCurrentPosition()>4300;});
            await(()->{for(View view:descendants((View)field(playback,"video")))if(view.getClass().getSimpleName().equals("AssSubtitleCanvasView")&&view.getWidth()>0&&view.getHeight()>0){Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));boolean pixels=false;for(int y=0;y<image.getHeight()&&!pixels;y+=3)for(int x=0;x<image.getWidth();x+=3)if(Color.alpha(image.getPixel(x,y))>100){pixels=true;break;}image.recycle();return pixels;}return false;});screenshot("hevc-native-audio-ass.png");
            assertVisibleVideoPixels();
        }finally{cleanup();}
    }

    @Test public void hdrSourceChoosesSurfaceBeforeDecodingAndDecoderSwitchesStaySafe()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.hdrVideo=true;open(server,"movie-single");play();
            for(boolean software:new boolean[]{false,true,false}){
                if(software!=Boolean.TRUE.equals(field(playback,"softwareVideo"))){
                    onMain(()->((Button)field(playback,"decoderButton")).performClick());settle();
                    instrumentation.sendKeyDownUpSync(software?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                }
                await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player!=null&&player.getPlaybackState()==Player.STATE_READY&&Boolean.TRUE.equals(field(playback,"videoFrameSeen"))&&(!software||((String)field(playback,"videoDecoder")).startsWith("ffmpeg"));});
                onMain(()->{
                    ExoPlayer player=(ExoPlayer)field(playback,"player");View surface=((androidx.media3.ui.PlayerView)field(playback,"video")).getVideoSurfaceView();
                    assertEquals(software,surface instanceof TextureView);assertEquals(!software,surface instanceof SurfaceView);
                    assertNull(player.getPlayerError());assertTrue(player.getVideoDecoderCounters().renderedOutputBufferCount>0);
                });
            }
        }finally{cleanup();}
    }
    @Test public void softwareVideoActuallyOutputsHevcFrames()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");play();onMain(()->((ExoPlayer)field(playback,"player")).play());
            onMain(()->((Button)field(playback,"decoderButton")).performClick());settle();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return Boolean.TRUE.equals(field(playback,"softwareVideo"))&&player!=null&&player.getVideoDecoderCounters()!=null&&player.getVideoDecoderCounters().renderedOutputBufferCount>0;});
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");boolean nativeVideo=false;for(int r=0;r<player.getRendererCount();r++)if(player.getRenderer(r).getState()!=0&&player.getRenderer(r).getName().contains("FfmpegVideo"))nativeVideo=true;assertTrue("软件兼容模式必须实际输出HEVC帧",nativeVideo);});
        }finally{cleanup();}
    }

    @Test public void pgsObjectsSurviveTheRealPlayerPipelineSeekAndSubtitleFallback()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.pgsSubtitle=true;open(server,"movie-single");play();clickTrack(true,3);
            onMain(()->assertTrue("恢复位置验证需要有跳转索引的媒体",((ExoPlayer)field(playback,"player")).isCurrentMediaItemSeekable()));
            await(()->((ExoPlayer)field(playback,"player")).getCurrentCues().cues.size()==2);
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(5000);player.play();});
            await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>5300);
            injectTextFailure(MimeTypes.APPLICATION_PGS);
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return !Boolean.TRUE.equals(field(playback,"enhancedSubtitles"))&&player.getPlaybackState()==Player.STATE_READY&&player.getCurrentCues().cues.size()==2;});
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.isPlaying()&&player.getCurrentPosition()>5700&&player.getCurrentCues().cues.size()==2;});
            onMain(()->{assertEquals(12,field(playback,"subtitleIndex"));assertFalse(Boolean.TRUE.equals(field(playback,"softwareVideo")));long resumed=((ExoPlayer)field(playback,"player")).getCurrentPosition();assertTrue("恢复位置="+resumed+"，保存位置="+field(playback,"position"),resumed>=5000);});screenshot("pgs-multiple-objects.png");
        }finally{cleanup();}
    }
    private void injectTextFailure(String mime){onMain(()->{try{
        java.lang.reflect.Method method=PlayerActivity.class.getDeclaredMethod("handlePlayerError",PlaybackException.class);method.setAccessible(true);
        method.invoke(playback,androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(new IllegalStateException("synthetic subtitle failure"),"TextRenderer",0,new Format.Builder().setSampleMimeType(mime).build(),C.FORMAT_HANDLED,false,PlaybackException.ERROR_CODE_DECODING_FAILED));
    }catch(ReflectiveOperationException error){throw new AssertionError(error);}});}
    @Test public void assRuntimeFailureFallsBackToTextWithoutChangingVideoDecoder()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");play();onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(4000);player.play();});
            await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>4300);injectTextFailure(MimeTypes.TEXT_SSA);
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return !Boolean.TRUE.equals(field(playback,"enhancedSubtitles"))&&player.getPlaybackState()==Player.STATE_READY&&!player.getCurrentCues().cues.isEmpty();});
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");assertFalse(Boolean.TRUE.equals(field(playback,"softwareVideo")));assertTrue(player.getCurrentPosition()>=4000);assertNotNull(player.getCurrentCues().cues.get(0).text);assertNull(player.getPlayerError());});
        }finally{cleanup();}
    }

    private void assertVisibleVideoPixels() throws Exception{
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot();int colored=0,total=0,greenDominant=0;
        java.util.HashSet<Integer> colors=new java.util.HashSet<>();
        int minRed=255,maxRed=0,minGreen=255,maxGreen=0,minBlue=255,maxBlue=0;
        // Sample across the color bars; the middle third can contain no red in a valid frame.
        for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y+=10)for(int x=image.getWidth()/8;x<image.getWidth()*7/8;x+=10){
            int color=image.getPixel(x,y),red=Color.red(color),green=Color.green(color),blue=Color.blue(color);total++;
            if(Math.max(red,Math.max(green,blue))>35)colored++;
            if(green>red*1.25f&&green>blue*1.25f)greenDominant++;
            colors.add((red>>4)<<8|(green>>4)<<4|(blue>>4));
            minRed=Math.min(minRed,red);maxRed=Math.max(maxRed,red);minGreen=Math.min(minGreen,green);maxGreen=Math.max(maxGreen,green);minBlue=Math.min(minBlue,blue);maxBlue=Math.max(maxBlue,blue);
        }
        image.recycle();
        assertTrue("字幕显示时，中央视频区域必须有实际画面像素",colored>total/5);
        assertTrue("视频画面不能退化成单色平面：colors="+colors.size()+" RGB ranges="+(maxRed-minRed)+","+(maxGreen-minGreen)+","+(maxBlue-minBlue),colors.size()>=8&&maxRed-minRed>=24&&maxGreen-minGreen>=24&&maxBlue-minBlue>=24);
        assertTrue("视频画面不能是纯绿色错误输出",greenDominant<total*0.9f);
    }
    @Test public void compatibleDolbyProfilesUseHevcAndProfileFiveRemainsDolby()throws Exception{
        ColorInfo hdr10=new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
        assertTrue(TvVideoRenderer.hasHevcBaseLayer(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.07.06").setColorInfo(hdr10).build()));
        assertFalse(TvVideoRenderer.hasHevcBaseLayer(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.07.06").build()));
        assertTrue(TvVideoRenderer.hasHevcBaseLayer(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.08.06").build()));
        assertFalse(TvVideoRenderer.hasHevcBaseLayer(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.05.06").build()));
        assertFalse(TvVideoRenderer.hasHevcBaseLayer(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()));
    }
    @Test public void softwareVideoOutputsFourKTenBitWithAss()throws Exception{verifyVideoFixture("player-hevc10.mkv",MimeTypes.VIDEO_H265);}
    @Test public void portraitPhoneSamplePlaysInBothDecoderModes()throws Exception{
        File file=new File(instrumentation.getTargetContext().getFilesDir(),"codec-fixtures/phone-portrait.mp4");
        org.junit.Assume.assumeTrue("手机竖屏样本只保留在本地设备，不随仓库分发",file.isFile());
        verifyRotationFixture("phone-portrait.mp4",0);
    }
    @Test public void rotationMetadataKeepsOrientationAndShapeInBothDecoderModes()throws Exception{
        verifyRotationFixture("rotation-90.mp4",90);
        verifyRotationFixture("rotation-270.mp4",270);
    }
    @Test public void hardwareRotationMetadataKeepsOrientationAndShape()throws Exception{
        verifyRotationFixture("rotation-270.mp4",270,new boolean[]{false});
        verifyRotationFixture("rotation-90.mp4",90,new boolean[]{false});
    }
    @Test public void hardwarePortraitWithoutRotationKeepsShape()throws Exception{
        File file=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"codec-fixtures/phone-portrait.mp4");
        org.junit.Assume.assumeTrue("手机样本只保留在独立诊断设备，不随仓库分发",file.isFile());
        verifyRotationFixture("phone-portrait.mp4",0,new boolean[]{false});
    }
    private void verifyRotationFixture(String file,int rotation)throws Exception{
        verifyRotationFixture(file,rotation,new boolean[]{false,true,false});
    }
    private void verifyRotationFixture(String file,int rotation,boolean[] modes)throws Exception{
        List<String> errors=new ArrayList<>();
        try(Fixture server=new Fixture(true,file)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            int pass=0;
            for(boolean software:modes){
                if(software!=Boolean.TRUE.equals(field(playback,"softwareVideo"))){
                    onMain(()->((Button)field(playback,"decoderButton")).performClick());settle();
                    instrumentation.sendKeyDownUpSync(software?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                }
                await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player!=null&&player.getPlaybackState()==Player.STATE_READY&&Boolean.TRUE.equals(field(playback,"videoFrameSeen"))&&(!software||((String)field(playback,"videoDecoder")).startsWith("ffmpeg"));});
                onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.pause();player.seekTo(2000);((androidx.media3.ui.PlayerView)field(playback,"video")).hideController();});
                await(()->((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);settle();
                String name=file+(software?"-software":"-system")+(pass++==2?"-restored":"");screenshot(name+".png");
                onMain(()->{
                    ExoPlayer player=(ExoPlayer)field(playback,"player");View surface=((androidx.media3.ui.PlayerView)field(playback,"video")).getVideoSurfaceView();
                    android.util.Log.i("RotationFixture",name+" decoder="+field(playback,"videoDecoder")+" format="+player.getVideoFormat()+" size="+player.getVideoSize().width+"x"+player.getVideoSize().height+" surface="+surface.getWidth()+"x"+surface.getHeight()+" rotation="+surface.getRotation()+" scale="+surface.getScaleX()+","+surface.getScaleY());
                    assertNull(player.getPlayerError());
                    assertEquals("必须保持选择的解码模式",software,((String)field(playback,"videoDecoder")).startsWith("ffmpeg"));
                    assertEquals("竖屏输出必须保留原始比例",360f/640f,surface.getWidth()/(float)surface.getHeight(),.003f);
                });
                if(rotation!=0){
                    Bitmap image=instrumentation.getUiAutomation().takeScreenshot();
                    int h=image.getHeight(),w=image.getWidth();float pictureW=h*360f/640f;
                    int[] expected=rotation==90?new int[]{Color.BLUE,Color.RED,Color.YELLOW,Color.GREEN}:new int[]{Color.GREEN,Color.YELLOW,Color.RED,Color.BLUE};int index=0;
                    for(float y:new float[]{.25f,.75f})for(float x:new float[]{.25f,.75f}){
                        int color=image.getPixel(Math.round((w-pictureW)/2+pictureW*x),Math.round(h*y));int wanted=expected[index++];
                        boolean matches=wanted==Color.BLUE?Color.blue(color)>150&&Color.red(color)<100:wanted==Color.RED?Color.red(color)>150&&Color.green(color)<100:wanted==Color.YELLOW?Color.red(color)>150&&Color.green(color)>150:Color.green(color)>130&&Color.red(color)<100;
                        if(!matches)errors.add(name+" quadrant="+(index-1)+" color="+Integer.toHexString(color));
                    }
                    assertTrue("横向黑边必须保留",Color.red(image.getPixel(w/8,h/2))<20);image.recycle();
                }
            }
            assertTrue("旋转后的实际输出像素必须保持方向和比例："+errors,errors.isEmpty());
        }finally{cleanup();}
    }
    @Test public void vc1ActuallyOutputsVideoWithAss()throws Exception{verifyVideoFixture("player-vc1.mkv",MimeTypes.VIDEO_VC1);}
    @Test public void firstFrameRecoveryWaitsForActivePlaybackAndPreservesSelections()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();clickTrack(false,3);clickTrack(true,2);
            final int[] selectedBefore={0,0};onMain(()->{selectedBefore[0]=(Integer)field(playback,"audioIndex");selectedBefore[1]=(Integer)field(playback,"subtitleIndex");});
            onMain(()->{try{
                java.lang.reflect.Field seen=PlayerActivity.class.getDeclaredField("videoFrameSeen"),since=PlayerActivity.class.getDeclaredField("videoReadySince");seen.setAccessible(true);since.setAccessible(true);
                seen.setBoolean(playback,false);since.setLong(playback,android.os.SystemClock.elapsedRealtime()-7000);((Runnable)field(playback,"videoCheck")).run();
                assertFalse("暂停不能触发回退",Boolean.TRUE.equals(field(playback,"softwareVideo")));assertEquals(0L,field(playback,"videoReadySince"));
                ExoPlayer player=(ExoPlayer)field(playback,"player");player.play();since.setLong(playback,android.os.SystemClock.elapsedRealtime()-7000);((Runnable)field(playback,"videoCheck")).run();
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            await(()->Boolean.TRUE.equals(field(playback,"softwareVideo"))&&Boolean.TRUE.equals(field(playback,"videoFrameSeen")));
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");assertEquals(selectedBefore[0],field(playback,"audioIndex"));assertEquals(selectedBefore[1],field(playback,"subtitleIndex"));assertTrue(!selected(C.TRACK_TYPE_AUDIO).isEmpty());assertTrue(player.getPlayWhenReady());});
        }finally{cleanup();}
    }
    private void verifyVideoFixture(String file,String mime)throws Exception{
        try(Fixture server=new Fixture(true,file)){
            open(server,"movie-single");play();onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.play();((Button)field(playback,"decoderButton")).performClick();});settle();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player!=null&&player.getVideoFormat()!=null&&mime.equals(player.getVideoFormat().sampleMimeType)&&player.getVideoDecoderCounters()!=null&&player.getVideoDecoderCounters().renderedOutputBufferCount>2&&Boolean.TRUE.equals(field(playback,"videoFrameSeen"));});
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");assertNull(player.getPlayerError());assertTrue(Boolean.TRUE.equals(field(playback,"softwareVideo")));player.seekTo(4000);});
            await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.getPlaybackState()==Player.STATE_READY&&player.getCurrentPosition()>4300;});
            onMain(()->((androidx.media3.ui.PlayerView)field(playback,"video")).hideController());settle();screenshot(file+".png");assertVisibleVideoPixels();
        }finally{cleanup();}
    }

    @Test public void heldSeekKeysPreviewLocallyAndCommitOnceAfterRelease()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            java.util.concurrent.atomic.AtomicInteger seeks=new java.util.concurrent.atomic.AtomicInteger();
            onMain(()->((ExoPlayer)field(playback,"player")).addListener(new Player.Listener(){
                @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks.incrementAndGet();}
            }));
            for(int key:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_REWIND}){
                boolean forward=key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
                long start=forward?1000:11000;
                onMain(()->((ExoPlayer)field(playback,"player")).seekTo(start));
                await(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");return player.getPlaybackState()==Player.STATE_READY&&player.getCurrentPosition()==start;});
                onMain(()->{((androidx.media3.ui.PlayerView)field(playback,"video")).hideController();seeks.set(0);});long pressed=SystemClock.uptimeMillis();
                for(int repeat=0;repeat<6;repeat++)instrumentation.sendKeySync(new KeyEvent(pressed,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,key,repeat));
                settle();
                onMain(()->{
                    ExoPlayer player=(ExoPlayer)field(playback,"player");assertEquals("长按期间不得实际跳转",0,seeks.get());assertEquals(start,player.getCurrentPosition());
                    assertTrue(((View)field(playback,"seekProgress")).isShown());assertEquals(forward?player.getDuration():0L,field(field(playback,"seekProgress"),"position"));
                });
                instrumentation.sendKeySync(new KeyEvent(pressed,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,key,0));
                await(()->seeks.get()==1);settle();assertEquals("松手后只执行一次跳转",1,seeks.get());
            }
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(1000);});
            await(()->((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
            onMain(()->{
                ((androidx.media3.ui.PlayerView)field(playback,"video")).hideController();seeks.set(0);long now=SystemClock.uptimeMillis();
                for(int tap=0;tap<3;tap++){playback.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0));playback.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0));}
                assertEquals("连续短按也应合并",0,seeks.get());
            });
            await(()->seeks.get()==1);settle();assertEquals(1,seeks.get());
            onMain(()->{((androidx.media3.ui.PlayerView)field(playback,"video")).hideController();seeks.set(0);long now=SystemClock.uptimeMillis();playback.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,0));playback.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT,0));});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
            assertFalse(playback.isFinishing());assertEquals("收起必须取消待执行跳转",0,seeks.get());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
            await(()->playback.isFinishing()&&field(playback,"player")==null&&detail.hasWindowFocus());assertEquals("退出必须取消待执行跳转",0,seeks.get());
        }finally{cleanup();}
    }

    @Test public void remoteSeekingOnlyShowsTimelineAndKeepsControlsNavigable()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();
            onMain(()->{androidx.media3.ui.PlayerView view=(androidx.media3.ui.PlayerView)field(playback,"video");assertFalse("快进时不显示播放器控制栏",view.isControllerFullyVisible());assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());assertTrue(((View)field(playback,"seekProgress")).isShown());assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()>=10000);});screenshot("seek-progress-only.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);settle();onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()<3000));
            await(()->!((View)field(playback,"seekProgress")).isShown());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);settle();onMain(()->{assertFalse(((androidx.media3.ui.PlayerView)field(playback,"video")).isControllerFullyVisible());assertTrue(((View)field(playback,"seekProgress")).isShown());});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_REWIND);settle();onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()<3000));
            await(()->!((View)field(playback,"seekProgress")).isShown());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertEquals(field(playback,"audioButton"),playback.getCurrentFocus());onMain(()->{assertFalse(((View)field(playback,"seekProgress")).isShown());assertEquals(View.VISIBLE,((View)field(playback,"controls")).getVisibility());});
            onMain(()->assertTrue("松手后立即打开控制栏仍须执行目标跳转",((ExoPlayer)field(playback,"player")).getCurrentPosition()>=10000));
            onMain(()->((androidx.media3.ui.PlayerView)field(playback,"video")).hideController());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getPlayWhenReady()));
        }finally{cleanup();}
    }

    private Button panelButton(String title){return findButton((View)field(playback,"episodePanel"),title);}
    private void resumeEpisode(Fixture server,String id)throws Exception{
        open(server,"series-many");JSONObject ep=EtknApi.rows(server.response("/api/tv/Shows/series-many/Episodes")).stream().filter(e->id.equals(e.optString("Id"))).findFirst().get();
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        onMain(()->detail.resumePlayback(ep));playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
        await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
        onMain(()->((ExoPlayer)field(playback,"player")).pause());await(()->Boolean.TRUE.equals(field(playback,"nextLoaded")));settle();
    }
    @Test public void upOpensDetailStyleEpisodesFromResumeAndSwitchesWithTargetSeasonLock()throws Exception{
        try(Fixture server=new Fixture()){
            server.ordinalFixture=true;resumeEpisode(server,"episode-2-3");
            new TvPreferences(playback,playback.api).saveSeasonAudio("season-1",1);server.audioOffset=20;
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();
            View panel=(View)field(playback,"episodePanel");assertTrue(panel.isShown());Button current=panelButton("第 3 集 ▶");assertSame(current,playback.getCurrentFocus());
            assertTrue(current.getContentDescription().toString().contains("正在播放"));Button watched=panelButton("第 1 集 ✓");assertNotNull(watched);
            assertEquals(10000,((android.graphics.drawable.LayerDrawable)watched.getBackground()).getDrawable(1).getLevel());
            onMain(()->((ExoPlayer)field(playback,"player")).play());await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=1000);onMain(()->((ExoPlayer)field(playback,"player")).pause());settle();
            await(()->((android.graphics.drawable.LayerDrawable)current.getBackground()).getDrawable(1).getLevel()>0);
            screenshot("player-current-episode-progress.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();assertFalse(panel.isShown());assertFalse(playback.isFinishing());assertEquals(0,server.stopped.size());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();onMain(()->panelButton("第 1 季").performClick());settle();
            Button resumed=panelButton("第 2 集");assertNotNull(resumed);assertNull(panelButton("第 3 集 ▶"));screenshot("player-episode-matrix.png");
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);PlayerActivity previous=playback;
            onMain(()->{resumed.requestFocus();resumed.performClick();});playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
            await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);onMain(()->((ExoPlayer)field(playback,"player")).pause());
            assertTrue(previous.isFinishing());assertEquals("episode-1-2",playback.getIntent().getStringExtra("id"));assertEquals("season-1",playback.getIntent().getStringExtra("seasonKey"));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO)));assertEquals(24,field(playback,"audioIndex"));await(()->server.stopped.size()==1);assertEquals("episode-2-3",server.stopped.get(0).optString("ItemId"));
        }finally{cleanup();}
    }
    @Test public void episodePanelRetryLateResultsAndBackKeepCurrentPlayback()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"episode-1-2");server.failNext=true;play();await(()->Boolean.TRUE.equals(field(playback,"episodesFailed")));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();assertNotNull(panelButton("重试"));assertFalse(playback.isFinishing());
            server.failNext=false;server.nextRequested=new java.util.concurrent.CountDownLatch(1);server.nextGate=new java.util.concurrent.CountDownLatch(1);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);assertTrue(server.nextRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();assertFalse(((View)field(playback,"episodePanel")).isShown());assertFalse(playback.isFinishing());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);settle();View focused=playback.getCurrentFocus();
            server.nextGate.countDown();await(()->!Boolean.TRUE.equals(field(playback,"episodesLoading")));settle();
            assertFalse(((View)field(playback,"episodePanel")).isShown());assertSame(focused,playback.getCurrentFocus());assertEquals(0,server.stopped.size());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();assertNotNull(panelButton("第 2 集 ▶"));assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();assertFalse(playback.isFinishing());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);settle();assertTrue(((AlertDialog)field(playback,"trackDialog")).isShowing());onMain(()->((AlertDialog)field(playback,"trackDialog")).dismiss());
        }finally{cleanup();}
    }
    @Test public void timelineIsAtBottomAndSpeedUsesOwnButton()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
            onMain(()->{
                View video=(View)field(playback,"video"),bar=video.findViewById(androidx.media3.ui.R.id.exo_progress),controls=(View)field(playback,"controls");Rect screen=new Rect(),timeline=new Rect(),bottom=new Rect();
                assertTrue(video.getGlobalVisibleRect(screen));assertTrue(bar.getGlobalVisibleRect(timeline));assertTrue(controls.getGlobalVisibleRect(bottom));
                assertEquals("进度条必须贴底",screen.bottom,timeline.bottom);assertTrue("按钮不能遮挡进度条",bottom.bottom<=timeline.top);assertTrue(bar instanceof PixelTimeBar);
                assertFalse("底部原生设置不能留在中央",video.findViewById(androidx.media3.ui.R.id.exo_settings)!=null);
                ((Button)field(playback,"speedButton")).performClick();
            });settle();
            onMain(()->assertTrue("长选择列表必须给屏幕上下留出边距",((AlertDialog)field(playback,"speedDialog")).getWindow().getDecorView().getHeight()<=playback.getResources().getDisplayMetrics().heightPixels-playback.dp(48)));
            screenshot("player-speed-dialog.png");
            onMain(()->{ListView list=((AlertDialog)field(playback,"speedDialog")).getListView();list.performItemClick(null,5,list.getAdapter().getItemId(5));});settle();
            onMain(()->{assertEquals(1.5f,((ExoPlayer)field(playback,"player")).getPlaybackParameters().speed,0.001f);assertEquals("倍速 · 1.5×",((Button)field(playback,"speedButton")).getText().toString());assertSame(field(playback,"speedButton"),playback.getCurrentFocus());});screenshot("player-bottom-pixel-timeline.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();assertFalse(playback.isFinishing());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();assertFalse(((View)field(playback,"episodePanel")).isShown());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();onMain(()->{Rect screen=new Rect(),timeline=new Rect();assertTrue(((View)field(playback,"video")).getGlobalVisibleRect(screen));assertTrue(((View)field(playback,"seekProgress")).getGlobalVisibleRect(timeline));assertEquals(screen.bottom,timeline.bottom);});
        }finally{cleanup();}
    }
    @Test public void timelineShowsIntroAndCreditsBoundariesOnBothBars()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
            onMain(()->{PixelTimeBar bar=((View)field(playback,"video")).findViewById(androidx.media3.ui.R.id.exo_progress);assertEquals(3,((JSONArray)field(bar,"chapters")).length());bar.setDuration(12000);bar.setPosition(4000);bar.setBufferedPosition(8000);
                Bitmap image=Bitmap.createBitmap(bar.getWidth(),bar.getHeight(),Bitmap.Config.ARGB_8888);bar.draw(new Canvas(image));
                try(FileOutputStream output=new FileOutputStream(new File(instrumentation.getTargetContext().getExternalFilesDir(null),"pixel-timeline-three-states.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}catch(IOException error){throw new AssertionError(error);}finally{image.recycle();}
            });screenshot("timeline-intro-credits-markers.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();
            onMain(()->{Object bar=field(playback,"seekProgress");assertEquals(3,((JSONArray)field(bar,"chapters")).length());assertEquals(View.VISIBLE,((View)bar).getVisibility());});screenshot("seek-intro-credits-markers.png");
        }finally{cleanup();}
    }

    @Test public void creditsSkipAdvancesWithoutSeekingInAutomaticAndButtonModes()throws Exception{
        try(Fixture server=new Fixture(true)){
            for(int mode:new int[]{TvPreferences.SKIP_AUTO,TvPreferences.SKIP_BUTTON}){
                if(detail!=null)onMain(()->detail.finish());open(server,"episode-1-2");new TvPreferences(detail,detail.api).setSkipMode(mode);play();
                PlayerActivity previous=playback;int[] seeks={0};long[] duration={0};int before=server.stopped.size();
                onMain(()->{ExoPlayer player=(ExoPlayer)field(previous,"player");duration[0]=player.getDuration();player.seekTo(10000);});
                await(()->((ExoPlayer)field(previous,"player")).getPlaybackState()==Player.STATE_READY&&((Long)field(previous,"skipTarget"))==duration[0]);
                onMain(()->((ExoPlayer)field(previous,"player")).addListener(new Player.Listener(){@Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks[0]++;}}));
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
                try{
                    if(mode==TvPreferences.SKIP_AUTO){onMain(()->{ExoPlayer player=(ExoPlayer)field(previous,"player");player.setPlaybackSpeed(0.1f);player.play();});await(()->((TextView)field(previous,"skipNotice")).getText().toString().startsWith("即将跳过片尾"));}
                    else onMain(()->((Button)field(previous,"skipButton")).performClick());
                    PlayerActivity next=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull("片尾应直接切下一集",next);playback=next;
                    await(()->field(next,"player")!=null&&((ExoPlayer)field(next,"player")).getPlaybackState()==Player.STATE_READY);onMain(()->((ExoPlayer)field(next,"player")).pause());
                    assertEquals("episode-1-3",next.getIntent().getStringExtra("id"));assertTrue(previous.isFinishing());assertNull(field(previous,"player"));
                    assertEquals("跳片尾不得先seek到文件末尾",0,seeks[0]);await(()->server.stopped.size()==before+1);
                    JSONObject stopped=server.stopped.get(before);assertEquals("episode-1-2",stopped.getString("ItemId"));assertTrue("跳片尾仍应完成本集",stopped.optBoolean("PlayedToCompletion"));
                    assertTrue("保留实际停止位置，不伪造末尾进度",stopped.getLong("PositionTicks")<duration[0]*10000);closePlayer();
                }finally{instrumentation.removeMonitor(monitor);}
            }
        }finally{cleanup();}
    }

    @Test public void backCancelsAutomaticCreditsCountdownWithoutExitingPlayback()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"episode-1-2");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_AUTO);play();PlayerActivity active=playback;int stoppedBefore=server.stopped.size();long[] duration={0};
            onMain(()->{ExoPlayer player=(ExoPlayer)field(active,"player");duration[0]=player.getDuration();player.seekTo(10000);});
            await(()->((Long)field(active,"skipTarget"))==duration[0]);
            onMain(()->{ExoPlayer player=(ExoPlayer)field(active,"player");player.setPlaybackSpeed(0.1f);player.play();});
            await(()->((TextView)field(active,"skipNotice")).getText().toString().startsWith("即将跳过片尾"));long[] position={0};
            onMain(()->position[0]=((ExoPlayer)field(active,"player")).getCurrentPosition());instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
            assertFalse("返回应取消片尾跳过而不是退出",active.isFinishing());assertEquals(Boolean.TRUE,field(active,"creditsSkipCancelled"));assertEquals(View.GONE,((View)field(active,"skipNotice")).getVisibility());
            SystemClock.sleep(5500);assertFalse("取消后不应重新启动倒计时",active.isFinishing());assertNotNull(field(active,"player"));assertEquals(stoppedBefore,server.stopped.size());long[] after={0};onMain(()->after[0]=((ExoPlayer)field(active,"player")).getCurrentPosition());
            assertTrue("取消跳过后继续播放",after[0]>position[0]);
        }finally{cleanup();}
    }

    @Test public void resumingInsideIntroShowsAutomaticSkipNotice()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"episode-1-2");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_AUTO);
            playback=(PlayerActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),PlayerActivity.class).putExtra("id","episode-1-2").putExtra("title","合成剧集").putExtra("seriesId","series-one").putExtra("position",2000L).putExtra("resume",true).putExtra("resolved",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PlayerActivity active=playback;await(()->field(active,"player")!=null&&((ExoPlayer)field(active,"player")).getPlaybackState()==Player.STATE_READY&&((View)field(active,"skipNotice")).getVisibility()==View.VISIBLE);
            assertEquals("已跳过片头",((TextView)field(active,"skipNotice")).getText().toString());long[] position={0};onMain(()->position[0]=((ExoPlayer)field(active,"player")).getCurrentPosition());assertTrue(position[0]>=3000);
        }finally{cleanup();}
    }

    @Test public void creditsSkipStopsWhileNextLookupIsPendingThenContinuesOrExits()throws Exception{
        try(Fixture server=new Fixture(true)){
            for(String id:new String[]{"episode-1-2","episode-1-24"}){
                if(detail!=null)onMain(()->detail.finish());open(server,id);new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_BUTTON);
                server.nextRequested=new java.util.concurrent.CountDownLatch(1);server.nextGate=new java.util.concurrent.CountDownLatch(1);
                Instrumentation.ActivityMonitor monitor=null;
                try{
                    play();assertTrue(server.nextRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));PlayerActivity previous=playback;int before=server.stopped.size();
                    onMain(()->((ExoPlayer)field(previous,"player")).seekTo(10000));await(()->((View)field(previous,"skipButton")).getVisibility()==View.VISIBLE);
                    monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
                    onMain(()->{((Button)field(previous,"skipButton")).performClick();assertNull("等待下一集时应释放旧播放器",field(previous,"player"));});
                    assertFalse("查询完成前不能误判为末集",previous.isFinishing());assertEquals(true,field(previous,"pendingAdvance"));await(()->server.stopped.size()==before+1);
                    assertTrue(server.stopped.get(before).optBoolean("PlayedToCompletion"));server.nextGate.countDown();
                    if(id.equals("episode-1-2")){
                        PlayerActivity next=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull(next);playback=next;assertEquals("episode-1-3",next.getIntent().getStringExtra("id"));
                        await(()->field(next,"player")!=null&&((ExoPlayer)field(next,"player")).getPlaybackState()==Player.STATE_READY);onMain(()->((ExoPlayer)field(next,"player")).pause());
                    }else await(()->previous.isFinishing()&&detail.hasWindowFocus());
                    settle();assertEquals("本集只回报一次停止",before+1,server.stopped.size());closePlayer();
                }finally{server.nextGate.countDown();server.nextGate=null;if(monitor!=null)instrumentation.removeMonitor(monitor);}
            }
        }finally{cleanup();}
    }

    @Test public void skipModesHonorExplicitMarkersAndPersistAcrossSettingsReopen()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");TvPreferences preferences=new TvPreferences(detail,detail.api);preferences.setSkipMode(TvPreferences.SKIP_BUTTON);play();
            onMain(()->{((ExoPlayer)field(playback,"player")).seekTo(1200);((ExoPlayer)field(playback,"player")).play();});await(()->((View)field(playback,"skipButton")).getVisibility()==View.VISIBLE);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=3000);onMain(()->assertTrue("确认跳过不能暂停播放",((ExoPlayer)field(playback,"player")).getPlayWhenReady()));closePlayer();
            preferences.setSkipMode(TvPreferences.SKIP_AUTO);play();onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.seekTo(1200);player.play();});await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=3000);assertEquals(View.GONE,((View)field(playback,"skipButton")).getVisibility());closePlayer();
            preferences.setSkipMode(TvPreferences.SKIP_OFF);play();onMain(()->((ExoPlayer)field(playback,"player")).seekTo(1200));settle();assertEquals(View.GONE,((View)field(playback,"skipButton")).getVisibility());onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()<3000));closePlayer();
            assertEquals(TvPreferences.SKIP_OFF,new TvPreferences(detail,detail.api).skipMode());
        }finally{cleanup();}
    }

    @Test public void movieChoicesReachRealPlayerAndSurviveSourceReordering()throws Exception{
        try(Fixture server=new Fixture()){
            server.slotLabels=true;open(server,"movie");assertEquals(View.VISIBLE,button("版本").getVisibility());assertEquals("版本 · 原盘",button("版本").getText().toString());
            assertTrue(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&view.isShown()&&((TextView)view).getText().toString().equals("合成电影")));
            screenshot("movie.png");onMain(()->button("版本").performClick());settle();
            assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("压制 · 4K HEVC HDR10").isEmpty());screenshot("detail-version-parameters.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();assertEquals("版本 · 压制",button("版本").getText().toString());
            onMain(()->button("音轨").performClick());settle();
            assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("自动选择 · 音轨一 · 主标题").isEmpty());
            assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("第 2 音轨 · 音轨二 · 主标题").isEmpty());screenshot("detail-audio-parameters.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();choose("字幕",3);
            assertEquals("音轨 · 音轨二 · 副标题",button("音轨").getText().toString());assertTrue(button("字幕").getText().toString().contains("内封二"));screenshot("movie-choices.png");
            play();assertEquals("source-b",playback.getIntent().getStringExtra("sourceId"));assertEquals(4,playback.getIntent().getIntExtra("audioIndex",-1));assertEquals(8,playback.getIntent().getIntExtra("subtitleIndex",-1));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO))&&"4".equals(selected(C.TRACK_TYPE_TEXT)));
            assertEquals("source-b",((JSONObject)field(playback,"source")).getString("Id"));assertEquals("版本 · 压制",((Button)field(playback,"versionButton")).getText().toString());
            assertEquals("音轨 · 音轨二 · 副标题",((Button)field(playback,"audioButton")).getText().toString());onMain(()->((Button)field(playback,"audioButton")).performClick());settle();
            onMain(()->{ListView list=((AlertDialog)field(playback,"trackDialog")).getListView();assertEquals("自动选择 · 音轨一 · 主标题",list.getAdapter().getItem(0));assertEquals("第 2 音轨 · 音轨二 · 主标题 ✓",list.getAdapter().getItem(2));});screenshot("player-audio-parameters.png");
            onMain(()->((AlertDialog)field(playback,"trackDialog")).dismiss());settle();
            onMain(()->((Button)field(playback,"versionButton")).performClick());settle();assertFalse(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("原盘 · 4K HEVC HDR10").isEmpty());screenshot("player-version-parameters.png");instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->"source-a".equals(((JSONObject)field(playback,"source")).optString("Id")));await(()->"版本 · 原盘".contentEquals(((Button)field(playback,"versionButton")).getText()));await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);settle();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();screenshot("player-current-version-slot.png");closePlayer();
            assertNull("客户端只能访问 TV 接口",server.unexpectedPath);assertTrue(server.reports>0);
        }finally{cleanup();}
    }
    @Test public void newMovieVersionUpdatesInPlaceAndKeepsSelectedSourceAndTracks()throws Exception{
        try(Fixture server=new Fixture()){
            server.liveArtwork=true;server.versionUpdates=true;open(server,"movie");choose("版本",1);choose("音轨",2);choose("字幕",3);
            Button version=button("版本");onMain(version::requestFocus);Object actions=version.getParent();onMain(version::performClick);settle();
            assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));server.changed=true;server.updateGate.countDown();
            await(()->((JSONArray)field(detail,"sources")).length()==3);
            JSONArray sources=(JSONArray)field(detail,"sources");assertEquals("source-b",sources.getJSONObject((Integer)field(detail,"sourceIndex")).getString("Id"));
            assertEquals(4,field(detail,"audio"));assertEquals(8,field(detail,"subtitle"));assertSame(version,detail.getCurrentFocus());assertSame(actions,version.getParent());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            assertEquals("source-a",((JSONArray)field(detail,"sources")).getJSONObject((Integer)field(detail,"sourceIndex")).getString("Id"));
            onMain(version::performClick);settle();assertTrue(instrumentation.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("新增版本").size()>0);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();screenshot("detail-new-version-in-place.png");
        }finally{cleanup();}
    }
    @Test public void newEpisodeVersionKeepsMatrixAndIgnoresLateOtherEpisodeSources()throws Exception{
        try(Fixture server=new Fixture()){
            server.liveArtwork=true;server.versionUpdates=true;open(server,"series-one");Button cell=button("第 3 集");onMain(cell::requestFocus);settle();Object grid=field(detail,"episodeGrid");
            assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));server.changed=true;server.updateGate.countDown();await(()->((JSONArray)field(detail,"sources")).length()==2);
            assertSame(grid,field(detail,"episodeGrid"));assertSame(cell,detail.getCurrentFocus());assertEquals(24,((GridLayout)grid).getChildCount());
            Object sources=field(detail,"sources");JSONObject late=new JSONObject().put("Id","episode-1-2").put("MediaSources",new JSONArray());
            onMain(()->{try{java.lang.reflect.Method merge=DetailActivity.class.getDeclaredMethod("mergeSources",JSONObject.class);merge.setAccessible(true);merge.invoke(detail,late);}catch(Exception error){throw new AssertionError(error);}});
            assertSame(sources,field(detail,"sources"));assertSame(cell,detail.getCurrentFocus());screenshot("detail-new-episode-version-matrix.png");
        }finally{cleanup();}
    }
    @Test public void chapterArtworkAppearsOnlyWhenDetailProvidesChapterImages()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-chapters");
            await(()->descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&"章节".equals(((TextView)view).getText().toString())));
            assertTrue(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&((TextView)view).getText().toString().contains("开场")));
            await(()->server.imagePaths.stream().anyMatch(path->path.contains("/Items/movie-chapters/Images/Chapter/0")));
            assertTrue(server.imagePaths.stream().anyMatch(path->path.contains("/Items/movie-chapters/Images/Chapter/1")));
            onMain(()->{
                View heading=descendants(detail.content).stream().filter(view->view instanceof TextView&&"章节".equals(((TextView)view).getText().toString())).findFirst().orElse(null);
                View first=contentDescription("开场");assertNotNull("章节卡片必须存在",first);assertNotNull("章节标题必须存在",heading);
                assertEquals("章节卡片宽度应缩小",detail.dp(180),first.getWidth());assertEquals("章节卡片高度应缩小",detail.dp(140),first.getHeight());
                View overview=descendants(detail.content).stream().filter(view->view instanceof TextView&&((TextView)view).getText().toString().startsWith("这是用于详情页验收")).findFirst().orElse(null);assertNotNull("有简介时简介文本必须存在",overview);assertTrue("有简介时简介可以聚焦",overview.isFocusable());
                assertFalse("章节标题不能成为焦点",heading.isFocusable());assertFalse("章节卡片父级滚动容器不能成为焦点",((View)first.getParent().getParent()).isFocusable());
                button("音轨").requestFocus();
            });
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
            assertTrue("有简介时向下应聚焦简介",detail.getCurrentFocus() instanceof TextView&&((TextView)detail.getCurrentFocus()).getText().toString().startsWith("这是用于详情页验收"));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            assertFalse("确认应打开简介弹窗并交出页面焦点",detail.hasWindowFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();assertTrue(detail.hasWindowFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertTrue("简介下方应直接进入章节",detail.getCurrentFocus()==contentDescription("开场")||detail.getCurrentFocus()==contentDescription("转折"));
            onMain(()->contentDescription("开场").requestFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();assertSame(contentDescription("转折"),detail.getCurrentFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();
            assertTrue("章节上方应回到有内容的简介",detail.getCurrentFocus() instanceof TextView&&((TextView)detail.getCurrentFocus()).getText().toString().startsWith("这是用于详情页验收"));
            screenshot("compact-chapters-overview.png");
        }finally{cleanup();}
    }
    @Test public void missingOverviewIsSkippedByRemoteFocus()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-no-overview");
            View overview=descendants(detail.content).stream().filter(view->view instanceof TextView&&"暂无简介".equals(((TextView)view).getText().toString())).findFirst().orElse(null);
            assertNotNull(overview);assertFalse("没有简介时占位文本不能成为焦点",overview.isFocusable());assertFalse("占位简介不能打开空弹窗",overview.isClickable());
            onMain(()->button("音轨").requestFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertTrue("无简介时从音轨向下直接到章节",detail.getCurrentFocus()==contentDescription("开场")||detail.getCurrentFocus()==contentDescription("转折"));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();assertTrue("向上应跳过占位简介回到选择行",detail.getCurrentFocus() instanceof Button);
            onMain(()->button("字幕").requestFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertTrue("无简介时从字幕向下直接到章节",detail.getCurrentFocus()==contentDescription("开场")||detail.getCurrentFocus()==contentDescription("转折"));
            screenshot("compact-chapters-no-overview.png");
        }finally{cleanup();}
    }
    @Test public void singleVersionHidesSelectorAndExternalSubtitleOrOffTakeEffect()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-single");assertEquals(View.GONE,button("版本").getVisibility());
            await(()->descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&((TextView)view).getText().toString().equals("合成电影")&&view.getVisibility()==View.GONE));
            choose("字幕",4);play();await(()->PlaybackSelection.externalId(12).equals(selected(C.TRACK_TYPE_TEXT)));closePlayer();
            // The checked external subtitle is fourth; move up to the off entry.
            onMain(()->button("字幕").performClick());settle();for(int i=0;i<3;i++)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            play();onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)));
        }finally{cleanup();}
    }
    @Test public void singleSeasonUsesNumberMatrixAndKeepsFocusedEpisodePreviewVisible()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"series-one");assertNull(button("第 1 季"));assertTrue("分集必须显式请求版本和轨道字段",((JSONArray)field(detail,"sources")).length()>0);assertEquals("继续播放",((Button)field(detail,"playButton")).getContentDescription().toString());screenshot("single-season-entry.png");
            Button episode=button("第 2 集");assertNotNull(episode);onMain(episode::requestFocus);settle();
            assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 2"));assertEquals("第 2 集的合成简介",((TextView)field(detail,"episodeOverview")).getText().toString());
            Rect preview=new Rect();assertTrue(((View)field(detail,"preview")).getGlobalVisibleRect(preview));assertTrue(preview.height()>=detail.dp(140));
            assertTrue(server.imagePaths.stream().anyMatch(path->path.contains("/Items/episode-1-2/Images/Primary")));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();assertEquals(button("第 3 集"),detail.getCurrentFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();int columns=Math.max(3,(detail.getResources().getConfiguration().screenWidthDp-84)/112);assertEquals(button("第 "+(3+columns)+" 集"),detail.getCurrentFocus());
            Button last=button("第 24 集");onMain(last::requestFocus);settle();assertTrue(last.getGlobalVisibleRect(new Rect()));assertTrue(((View)field(detail,"preview")).getGlobalVisibleRect(preview));assertTrue(preview.height()>=detail.dp(140));screenshot("single-season-matrix.png");
        }finally{cleanup();}
    }
    @Test public void multipleSeasonsRemainSelectableAndRefreshMatrix()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"series-many");assertNotNull(button("第 1 季"));assertNotNull(button("第 2 季"));assertNull(button("第 24 集"));
            onMain(()->button("第 1 季").performClick());settle();assertNotNull(button("第 24 集"));onMain(()->button("第 1 集").requestFocus());settle();screenshot("multi-season.png");
            onMain(()->button("第 2 季").performClick());settle();assertNull(button("第 24 集"));
            assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 3"));
        }finally{cleanup();}
    }

    private String placeholderDate(int days){java.util.Calendar date=java.util.Calendar.getInstance();date.add(java.util.Calendar.DAY_OF_MONTH,days);return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(date.getTime());}
    private void assertPlaceholderLabels(View root){
        String[] labels={"今天播出","明天播出",placeholderDate(2),"播出时间待定"};
        for(int i=0;i<labels.length;i++){String prefix="第 "+(i+4)+" 集";Button cell=null;for(View view:descendants(root))if(view instanceof Button&&((Button)view).getText().toString().startsWith(prefix))cell=(Button)view;assertNotNull(cell);assertTrue(cell.getText().toString(),cell.getText().toString().contains(labels[i]));}
    }
    private void assertUniformDatedMatrix(GridLayout grid){
        assertTrue(grid.getChildCount()>5);TextView first=(TextView)grid.getChildAt(0);int columns=grid.getColumnCount();
        for(int i=0;i<grid.getChildCount();i++){TextView cell=(TextView)grid.getChildAt(i);assertEquals(first.getWidth(),cell.getWidth());assertEquals(first.getHeight(),cell.getHeight());assertEquals(first.getBaseline(),cell.getBaseline());assertEquals(2,cell.getLineCount());assertEquals(grid.getChildAt(i/columns*columns).getTop(),cell.getTop());}
    }
    @Test public void detailActionsAreOneIconRowAndFavoriteBecomesSolidRedHeart()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;open(server,"item-1");await(()->((Button)field(detail,"deleteButton")).isShown());
            onMain(()->{Button play=(Button)field(detail,"playButton");ViewGroup row=(ViewGroup)play.getParent();for(int i=0;i<row.getChildCount();i++){Button action=(Button)row.getChildAt(i);if(!action.isShown())continue;assertEquals("",action.getText().toString());assertNotNull(action.getCompoundDrawables()[0]);assertEquals(play.getTop(),action.getTop());assertEquals(play.getWidth(),action.getWidth());assertEquals(play.getHeight(),action.getHeight());android.graphics.drawable.StateListDrawable fill=(android.graphics.drawable.StateListDrawable)action.getBackground();android.graphics.drawable.GradientDrawable idleFill=(android.graphics.drawable.GradientDrawable)fill.getStateDrawable(fill.getStateCount()-1);assertEquals(Color.argb(105,0,0,0),idleFill.getColor().getDefaultColor());android.graphics.drawable.LayerDrawable focusedFill=(android.graphics.drawable.LayerDrawable)fill.getStateDrawable(0);assertEquals(Color.argb(150,0,0,0),((android.graphics.drawable.GradientDrawable)focusedFill.getDrawable(0)).getColor().getDefaultColor());Rect visible=new Rect();assertTrue(action.getGlobalVisibleRect(visible));assertEquals(action.getWidth(),visible.width());}
                assertEquals(0,Color.alpha(iconCenter((Button)field(detail,"favoriteButton"))));
                android.graphics.drawable.StateListDrawable background=(android.graphics.drawable.StateListDrawable)((Button)field(detail,"audioButton")).getBackground();android.graphics.drawable.GradientDrawable idle=(android.graphics.drawable.GradientDrawable)background.getStateDrawable(background.getStateCount()-1);assertTrue(Color.alpha(idle.getColor().getDefaultColor())<255);
                ((Button)field(detail,"favoriteButton")).performClick();});
            await(()->server.favorite&&"取消收藏".equals(((Button)field(detail,"favoriteButton")).getContentDescription()));
            onMain(()->{int color=iconCenter((Button)field(detail,"favoriteButton"));assertTrue(Color.red(color)>220&&Color.green(color)<100&&Color.alpha(color)==255);});screenshot("detail-icon-actions-red-heart.png");
            onMain(()->((Button)field(detail,"playedButton")).performClick());await(()->server.watched&&"标记未播放".equals(((Button)field(detail,"playedButton")).getContentDescription()));
        }finally{cleanup();}
    }
    private int iconCenter(Button button){android.graphics.drawable.Drawable icon=button.getCompoundDrawables()[0];Bitmap bitmap=Bitmap.createBitmap(icon.getBounds().width(),icon.getBounds().height(),Bitmap.Config.ARGB_8888);icon.draw(new Canvas(bitmap));int pixel=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);bitmap.recycle();return pixel;}
    @Test public void detailServerEventRefreshesMetadataAndArtworkWithoutRebuildingChoices()throws Exception{
        try(Fixture server=new Fixture()){
            server.liveArtwork=true;open(server,"movie-single");await(()->server.eventsConnected.getCount()==0);choose("音轨",2);choose("字幕",4);Button audio=(Button)field(detail,"audioButton");onMain(audio::requestFocus);ScrollView scroll=(ScrollView)field(detail,"scroll");int y=scroll.getScrollY();Object playable=field(detail,"playable"),sources=field(detail,"sources");Object audioChoice=field(detail,"audio"),subtitleChoice=field(detail,"subtitle");server.changed=true;server.updateGate.countDown();
            await(()->{ImageView background=(ImageView)field(detail,"backdrop");return background.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)background.getDrawable()).getBitmap().getPixel(10,10)==Color.MAGENTA;});
            await(()->{boolean douban=false,tmdb=false,overview=false;for(View view:descendants(detail.content))if(view instanceof TextView){String text=((TextView)view).getText().toString();douban|=text.trim().equals("9.1");tmdb|=text.trim().equals("8.2");overview|=text.equals("服务器已更新的简介");}return douban&&tmdb&&overview;});
            assertEquals("服务器更新后的片名",((TextView)field(detail,"detailTitle")).getText().toString());
            assertTrue(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&((TextView)view).getText().toString().contains("自定义 16+")));
            assertFalse(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&((TextView)view).getText().toString().contains("PG-13")));
            assertSame(sources,field(detail,"sources"));assertEquals(audioChoice,field(detail,"audio"));assertEquals(subtitleChoice,field(detail,"subtitle"));
            assertFalse("元数据/图片已刷新".contentEquals(detail.status.getText()));assertEquals(0,server.refreshStatusRequests);
            assertSame(audio,field(detail,"audioButton"));assertSame(audio,detail.getCurrentFocus());assertSame(playable,field(detail,"playable"));assertEquals(y,scroll.getScrollY());settle();screenshot("detail-live-artwork.png");
        }finally{cleanup();}
    }
    @Test public void manualRefreshWaitsForNotifiedDataAndIgnoresOlderRequest()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;server.liveArtwork=true;open(server,"item-refresh");await(()->server.eventsConnected.getCount()==0&&!Boolean.TRUE.equals(field(detail,"artworkLoading")));
            onMain(()->((Button)field(detail,"refreshButton")).performClick());settle();onMain(()->((AlertDialog)field(detail,"managementDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(()->"元数据刷新任务已提交".contentEquals(detail.status.getText())&&detail.getCurrentFocus()==field(detail,"playButton"));server.holdMetadata=true;
            onMain(()->{try{java.lang.reflect.Method method=DetailActivity.class.getDeclaredMethod("refreshArtwork");method.setAccessible(true);method.invoke(detail);}catch(Exception error){throw new AssertionError(error);}});
            assertTrue(server.metadataRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));server.changed=true;server.updateGate.countDown();await(()->"v2".equals(field(detail,"artworkRevision")));
            assertEquals("元数据刷新任务已提交",detail.status.getText().toString());server.metadataGate.countDown();
            await(()->"服务器更新后的片名".contentEquals(((TextView)field(detail,"detailTitle")).getText()));await(()->server.refreshStatusRequests>0);
            assertEquals("元数据刷新任务已提交",detail.status.getText().toString());onMain(()->((Button)field(detail,"audioButton")).requestFocus());server.taskStatus="succeeded";await(()->"元数据/图片已刷新".contentEquals(detail.status.getText())&&detail.getCurrentFocus()==field(detail,"playButton"));
            int checked=server.refreshStatusRequests;Thread.sleep(3300);assertEquals(checked,server.refreshStatusRequests);
        }finally{cleanup();}
    }
    @Test public void scheduledEpisodeUpdateRefreshesPreviewAndKeepsMatrix()throws Exception{
        try(Fixture server=new Fixture()){
            server.liveArtwork=true;open(server,"series-one");Button cell=button("第 3 集");onMain(cell::requestFocus);settle();Object grid=field(detail,"episodeGrid"),sources=field(detail,"sources");
            assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));server.changed=true;server.updateGate.countDown();
            await(()->"服务器已更新的简介".contentEquals(((TextView)field(detail,"episodeOverview")).getText()));assertFalse("元数据/图片已刷新".contentEquals(detail.status.getText()));
            assertSame(grid,field(detail,"episodeGrid"));assertSame(cell,detail.getCurrentFocus());assertSame(sources,field(detail,"sources"));assertEquals(24,((GridLayout)grid).getChildCount());assertEquals(0,server.refreshStatusRequests);
        }finally{cleanup();}
    }
    @Test public void failedManualRefreshDoesNotReportSuccess()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;server.taskStatus="failed";open(server,"item-refresh");await(()->((View)field(detail,"refreshButton")).isShown());
            onMain(()->((Button)field(detail,"refreshButton")).performClick());onMain(()->((AlertDialog)field(detail,"managementDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(()->"元数据/图片刷新失败".contentEquals(detail.status.getText()));int checked=server.refreshStatusRequests;Thread.sleep(3300);assertEquals(checked,server.refreshStatusRequests);
        }finally{cleanup();}
    }
    @Test public void manualRefreshCompletionWaitsUntilReturningFromPlayer()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.admin=true;open(server,"item-refresh");await(()->((View)field(detail,"refreshButton")).isShown());onMain(()->((Button)field(detail,"refreshButton")).performClick());onMain(()->((AlertDialog)field(detail,"managementDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(()->"元数据刷新任务已提交".contentEquals(detail.status.getText()));play();int checked=server.refreshStatusRequests;server.taskStatus="succeeded";Thread.sleep(3500);
            assertEquals(checked,server.refreshStatusRequests);assertEquals("元数据刷新任务已提交",detail.status.getText().toString());closePlayer();await(()->"元数据/图片已刷新".contentEquals(detail.status.getText())&&detail.getCurrentFocus()==field(detail,"playButton"));
        }finally{cleanup();}
    }
    @Test public void detailCloudSearchOpensCurrentMediaForDiscoveryUsers()throws Exception{
        DiscoveryActivity cloud=null;
        try(Fixture server=new Fixture()){
            server.discoveryAllowed=true;open(server,"movie-single");await(()->((View)field(detail,"cloudButton")).isShown());assertFalse(server.admin);
            Button search=(Button)field(detail,"cloudButton");onMain(search::requestFocus);screenshot("detail-cloud-search-icon.png");
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(DiscoveryActivity.class.getName(),null,false);onMain(search::performClick);cloud=(DiscoveryActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(cloud);
            await(()->server.cloudPaths.size()==3);for(String path:server.cloudPaths){assertTrue(path.contains("tmdb_id=123"));assertTrue(path.contains("movie"));}assertEquals("123",new JSONObject(cloud.getIntent().getStringExtra("cloudItem")).getString("id"));
            DiscoveryActivity opened=cloud;await(()->"搜索完成，选择资源版本并确认获取。".contentEquals(opened.status.getText()));screenshot("detail-direct-cloud-results.png");onMain(opened::onBackPressed);await(opened::isDestroyed);cloud=null;await(search::isShown);assertSame(search,detail.getCurrentFocus());
        }finally{if(cloud!=null){DiscoveryActivity opened=cloud;onMain(opened::finish);}cleanup();}
    }
    @Test public void administratorWithoutDiscoveryPermissionDoesNotSeeCloudIcon()throws Exception{
        try(Fixture server=new Fixture()){
            server.admin=true;open(server,"item-refresh");await(()->server.discoveryChecks>0);settle();assertEquals(View.GONE,((View)field(detail,"cloudButton")).getVisibility());assertTrue(server.cloudPaths.isEmpty());
        }finally{cleanup();}
    }
    @Test public void episodeCloudSearchUsesSeriesIdentityAndCurrentSeason()throws Exception{
        DiscoveryActivity cloud=null;
        try(Fixture server=new Fixture()){
            server.discoveryAllowed=true;open(server,"episode-1-3");await(()->((View)field(detail,"cloudButton")).isShown());JSONObject item=(JSONObject)field(detail,"item");onMain(()->{try{item.put("ParentIndexNumber",1);item.getJSONObject("ProviderIds").put("Tmdb","999");}catch(Exception error){throw new AssertionError(error);}});
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(DiscoveryActivity.class.getName(),null,false);onMain(()->((Button)field(detail,"cloudButton")).performClick());cloud=(DiscoveryActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(cloud);
            await(()->server.cloudPaths.size()==3);for(String path:server.cloudPaths)assertTrue(path.contains("tmdb_id=123"));assertTrue(server.cloudPaths.stream().anyMatch(path->path.contains("media_type=tv")&&path.contains("season=1")));assertEquals(1,cloud.getIntent().getIntExtra("cloudSeason",-1));
        }finally{if(cloud!=null){DiscoveryActivity opened=cloud;onMain(opened::finish);}cleanup();}
    }
    @Test public void detailPlaceholderDatesKeepAllMatrixCellsAligned()throws Exception{
        try(Fixture server=new Fixture()){
            server.placeholderDates=true;open(server,"series-many");Button dated=button("第 4 集");assertPlaceholderLabels(detail.content);
            onMain(()->dated.requestFocus());settle();onMain(()->assertUniformDatedMatrix((GridLayout)field(detail,"episodeGrid")));screenshot("detail-placeholder-dates-aligned.png");
        }finally{cleanup();}
    }
    @Test public void playerListsPlaceholderDatesButAutomaticNextSkipsThem()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.placeholderDates=true;resumeEpisode(server,"episode-2-3");assertEquals("episode-2-8",((JSONObject)field(playback,"nextEpisode")).optString("Id"));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);settle();View panel=(View)field(playback,"episodePanel");Button dated=null,unknown=null;
            for(View view:descendants(panel))if(view instanceof Button){String text=((Button)view).getText().toString();if(text.startsWith("第 4 集"))dated=(Button)view;if(text.startsWith("第 5 集"))unknown=(Button)view;}
            assertNotNull(dated);assertPlaceholderLabels(panel);Button focus=dated;
            onMain(()->{focus.requestFocus();assertUniformDatedMatrix((GridLayout)focus.getParent());});settle();assertFalse(playback.isFinishing());screenshot("player-placeholder-dates-aligned.png");
        }finally{cleanup();}
    }
    @Test public void translationCapturesBufferedTextBeforeDisplayAndManualSelectionRestoresOriginal()throws Exception{
        try(Fixture server=new Fixture()){
            server.translateSubtitles=true;server.noProgress=true;open(server,"movie-single");play();
            await(()->server.translationCues.stream().anyMatch(c->c.optLong("start")>=10000));
            SubtitleTranslation helper=(SubtitleTranslation)field(playback,"translation");
            TextView overlay=(TextView)field(helper,"overlay");View original=(View)field(helper,"original");
            onMain(()->{assertEquals("3",selected(C.TRACK_TYPE_TEXT));assertEquals(View.INVISIBLE,original.getVisibility());assertEquals("",overlay.getText().toString());});
            synchronized(server.translationCues){for(JSONObject cue:server.translationCues){assertEquals("Synthetic subtitle 3",cue.getString("text"));assertEquals(1000,cue.getLong("end")-cue.getLong("start"));}}
            server.translationReady=true;await(()->overlay.getText().toString().contains("合成译文"));
            onMain(()->assertEquals(View.INVISIBLE,original.getVisibility()));screenshot("realtime-translated-subtitle.png");
            clickTrack(true,3);await(()->overlay.getVisibility()==View.GONE&&original.getVisibility()==View.VISIBLE);
            onMain(()->assertEquals("4",selected(C.TRACK_TYPE_TEXT)));
            clickTrack(true,0);await(()->overlay.getText().toString().contains("合成译文"));
            closePlayer();onMain(()->{assertFalse(helper.active());assertNull(overlay.getParent());assertEquals(View.VISIBLE,original.getVisibility());});
        }finally{cleanup();}
    }
    @Test public void translatedAssKeepsContainerTimestampsAcrossSeek()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.translateSubtitles=true;server.translationReady=true;server.noProgress=true;open(server,"movie-single");play();
            SubtitleTranslation helper=(SubtitleTranslation)field(playback,"translation");TextView overlay=(TextView)field(helper,"overlay");
            await(()->overlay.getText().toString().contains("ASS animation")&&overlay.getText().toString().contains("Native subtitle rendering"));
            synchronized(server.translationCues){assertEquals(2,server.translationCues.size());for(JSONObject cue:server.translationCues){assertEquals(0,cue.getLong("start"));assertEquals(12000,cue.getLong("end"));assertFalse(cue.getString("text").contains("{"));}}
            onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");assertTrue(player.isCurrentMediaItemSeekable());player.seekTo(7000);});
            settle();
            await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=6900&&overlay.getText().toString().contains("合成译文"));
            onMain(()->assertEquals(View.INVISIBLE,((View)field(helper,"original")).getVisibility()));screenshot("translated-ass-after-seek.png");
        }finally{cleanup();}
    }
    @Test public void backwardSeekAdmitsCurrentCuesWhenOldFutureQueueIsFull()throws Exception{
        try(Fixture server=new Fixture(true)){
            server.translateSubtitles=true;server.noProgress=true;open(server,"movie-single");play();
            SubtitleTranslation helper=(SubtitleTranslation)field(playback,"translation");
            onMain(()->((ExoPlayer)field(playback,"player")).seekTo(7000));
            await(()->(long)field(helper,"lastPosition")>=6900);
            onMain(()->{synchronized(helper){((Map<?,?>)field(helper,"pending")).clear();for(int i=0;i<512;i++)helper.cue(1000000+i*1000,1000500+i*1000,"Old future "+i);}((ExoPlayer)field(playback,"player")).seekTo(1000);});
            await(()->(long)field(helper,"lastPosition")<2000);
            onMain(()->helper.cue(1000,3000,"Current after backward seek"));
            await(()->server.translationCues.stream().anyMatch(c->c.optString("text").equals("Current after backward seek")));
            server.translationReady=true;
            TextView overlay=(TextView)field(helper,"overlay");
            await(()->overlay.getText().toString().contains("Current after backward seek"));
            onMain(()->assertEquals(View.INVISIBLE,((View)field(helper,"original")).getVisibility()));
        }finally{cleanup();}
    }
    @Test public void delayedTranslationSurvivesNormalPlaybackAdvance()throws Exception{
        try(Fixture server=new Fixture()){
            server.translateSubtitles=true;server.translationReady=true;server.noProgress=true;
            server.translationGate=new java.util.concurrent.CountDownLatch(1);server.translationRequested=new java.util.concurrent.CountDownLatch(1);
            server.translationCues.add(new JSONObject().put("start",0).put("end",12000).put("text","Delayed translation"));
            open(server,"movie-single");play();
            assertTrue(server.translationRequested.await(5,java.util.concurrent.TimeUnit.SECONDS));
            SubtitleTranslation helper=(SubtitleTranslation)field(playback,"translation");
            onMain(()->((ExoPlayer)field(playback,"player")).play());
            await(()->((ExoPlayer)field(playback,"player")).getCurrentPosition()>=6000);
            server.translationGate.countDown();
            TextView overlay=(TextView)field(helper,"overlay");
            await(()->overlay.getText().toString().contains("Delayed translation"));
        }finally{cleanup();}
    }
    private final class Fixture implements AutoCloseable{
        volatile boolean translateSubtitles,translationReady;
        volatile java.util.concurrent.CountDownLatch translationRequested,translationGate;
        final List<JSONObject> translationCues=Collections.synchronizedList(new ArrayList<>());
        volatile boolean placeholderDates,admin,favorite,watched,liveArtwork,changed,holdMetadata,slotLabels,versionUpdates;
        volatile boolean holdIdentity;
        volatile int refreshStatusRequests;
        volatile String taskStatus="running";
        volatile boolean discoveryAllowed;
        volatile int discoveryChecks;
        final List<String> cloudPaths=Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.CountDownLatch metadataRequested=new java.util.concurrent.CountDownLatch(1),metadataGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch backgroundRequested=new java.util.concurrent.CountDownLatch(1),logoRequested=new java.util.concurrent.CountDownLatch(1),backdropGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch updateGate=new java.util.concurrent.CountDownLatch(1),eventsConnected=new java.util.concurrent.CountDownLatch(1);
        final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        final byte[] image,media,updatedImage;
        final List<String> imagePaths=Collections.synchronizedList(new ArrayList<>());
        final List<JSONObject> stopped=Collections.synchronizedList(new ArrayList<>());
        volatile java.util.concurrent.CountDownLatch nextRequested,nextGate;
        volatile boolean closed,preferSecond,extraTrack,pgsSubtitle,ordinalFixture,missingAudio,noProgress,failNext,hdrVideo,browseFeatures,studioIcons;volatile int audioOffset;volatile String unexpectedPath,lastBrowsePath="";volatile int reports;
        final boolean codecMedia;
        final JSONObject personal=new JSONObject();
        Fixture()throws Exception{this(false);}
        Fixture(boolean codecs)throws Exception{this(codecs,codecs?"player-codecs.mkv":"detail-tracks.mkv");}
        Fixture(boolean codecs,String file)throws Exception{codecMedia=codecs;personal.put("audio_lang_priority",JSONObject.NULL).put("subtitle_priority",new JSONArray().put("chs"));
            Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(38,60,84));Paint paint=new Paint();paint.setColor(Color.rgb(95,140,155));canvas.drawCircle(750,180,220,paint);paint.setColor(Color.rgb(205,155,85));canvas.drawRect(620,300,900,500,paint);ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);bitmap.recycle();image=output.toByteArray();
            Bitmap updated=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);updated.eraseColor(Color.MAGENTA);ByteArrayOutputStream replacement=new ByteArrayOutputStream();updated.compress(Bitmap.CompressFormat.PNG,100,replacement);updated.recycle();updatedImage=replacement.toByteArray();
            File local=new File(instrumentation.getTargetContext().getFilesDir(),"codec-fixtures/"+file);
            File external=local.isFile()?local:new File(instrumentation.getTargetContext().getExternalFilesDir(null),"codec-fixtures/"+file);
            if("player-vc1.mkv".equals(file))org.junit.Assume.assumeTrue("请按构建规范放置 FFmpeg VC1 测试片段；不随公开仓库分发",external.isFile());
            try(InputStream stream=external.isFile()?new FileInputStream(external):instrumentation.getContext().getAssets().open(file)){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)data.write(buffer,0,n);media=data.toByteArray();}
            Thread thread=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DetailFixture","request",error);}});worker.setDaemon(true);worker.start();}catch(IOException ignored){if(closed)return;}},"detail-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject stream(String type,int index,String title)throws Exception{return new JSONObject().put("Type",type).put("Index",index).put("DisplayTitle",title+" · 主标题 (默认)").put("Title",title+" · 副标题").put("Language","chi").put("Codec",type.equals("Audio")?"pcm_s16le":"subrip");}
        JSONArray sources(boolean multi)throws Exception{
            if(codecMedia){JSONArray streams=new JSONArray();if(hdrVideo)streams.put(new JSONObject().put("Type","Video").put("Index",0).put("Codec","hevc").put("VideoRange","HDR10"));String[] codecs={"aac","ac3","eac3","dts","truehd"};for(int i=0;i<codecs.length;i++)streams.put(stream("Audio",i+1,"归一化 "+codecs[i]).put("Title","raw-"+codecs[i]).put("Codec",codecs[i]));streams.put(stream("Subtitle",6,"特效字幕 ASS").put("Title","raw-effect").put("Codec","ass"));
                if(pgsSubtitle)streams.put(stream("Subtitle",12,"外挂 PGS").put("IsExternal",true).put("Codec","pgs").put("DeliveryUrl","/api/tv/subtitle.vtt"));
                JSONArray chapters=new JSONArray();String[] types={"IntroStart","IntroEnd","CreditsStart"};long[] ticks={10000000,30000000,100000000};for(int i=0;i<types.length;i++)chapters.put(new JSONObject().put("MarkerType",types[i]).put("StartPositionTicks",ticks[i]));JSONObject source=new JSONObject().put("Id","source-a").put("Name","HEVC 多编码").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",1).put("DefaultSubtitleStreamIndex",translateSubtitles?-1:6).put("Chapters",chapters);if(translateSubtitles)source.put("SubtitleTranslation",new JSONObject().put("Key","fixture-key").put("SourceIndex",6).put("SourceOrdinal",0).put("Mode","translate").put("Target","chs"));return new JSONArray().put(source);}
            JSONArray streams=new JSONArray().put(stream("Audio",0,"音轨一").put("IsDefault",true)).put(stream("Audio",4,"音轨二")).put(stream("Subtitle",7,"内封一")).put(stream("Subtitle",8,"内封二")).put(stream("Subtitle",12,"外挂字幕").put("IsExternal",true).put("Codec","webvtt").put("DeliveryUrl","/api/tv/subtitle.vtt"));
            if(pgsSubtitle)streams.getJSONObject(4).put("Codec","pgs");
            if(extraTrack)streams.put(stream("Audio",100,"未解析音轨"));
            if(ordinalFixture){streams.getJSONObject(0).put("Index",audioOffset).put("Title","国语");streams.getJSONObject(1).put("Index",audioOffset+4).put("Title","国语");if(missingAudio)streams.remove(1);}
            JSONArray sources=new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","1080P").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",preferSecond?4:0).put("DefaultSubtitleStreamIndex",preferSecond?8:-1));
            if(ordinalFixture)sources.getJSONObject(0).put("DefaultAudioStreamIndex",audioOffset);
            if(translateSubtitles)sources.getJSONObject(0).put("SubtitleTranslation",new JSONObject().put("Key","fixture-key").put("SourceIndex",7).put("SourceOrdinal",0).put("Mode","translate").put("Target","chs"));
            if(multi)sources.put(new JSONObject(sources.getJSONObject(0).toString()).put("Id","source-b").put("Name","4K 版本"));
            if(slotLabels)for(int i=0;i<sources.length();i++){JSONObject source=sources.getJSONObject(i);source.put("Name",(i==0?"原盘":"压制")+" · 4K HEVC HDR10");source.getJSONArray("MediaStreams").put(new JSONObject().put("Type","Video").put("Index",99).put("DisplayTitle","4K HEVC HDR10"));}
            if(versionUpdates&&changed){sources.put(new JSONObject(sources.getJSONObject(0).toString()).put("Id","source-c").put("Name","新增版本"));JSONArray reordered=new JSONArray();for(int i=sources.length()-1;i>=0;i--)reordered.put(sources.get(i));sources=reordered;}return sources;
        }
        JSONObject base(String id,String type)throws Exception{return new JSONObject().put("ProviderIds",new JSONObject().put("Tmdb","123")).put("Id",id).put("Type",type).put("Name",type.equals("Movie")?"合成电影":"合成剧集").put("Overview","这是用于详情页验收的合成简介。遥控器可选择播放版本、音轨、字幕，也可浏览演员与分集预览。").put("ProductionYear",2026).put("TMDbRating",7.6).put("DoubanRating",8.7).put("OfficialRating","PG-13").put("RunTimeTicks",95L*600000000).put("Genres",new JSONArray().put("剧情").put("悬疑")).put("Tags",new JSONArray().put("悬疑").put("经典")).put("People",new JSONArray().put(new JSONObject().put("Id","person-1").put("Name","合成演员").put("Type","Actor").put("Role","主演"))).put("UserData",new JSONObject().put("IsFavorite",favorite).put("Played",watched)).put("BackdropImageTags",new JSONArray().put(changed?"art2":"art1")).put("ImageTags",new JSONObject().put("Logo",changed?"art2":"art1"));}
        JSONObject response(String path)throws Exception{
            if(path.endsWith("/SubtitleTranslation")){JSONArray result=new JSONArray();if(translationReady)synchronized(translationCues){for(JSONObject cue:translationCues)result.put(new JSONObject().put("start",cue.getLong("start")).put("end",cue.getLong("end")).put("source",cue.getString("text")).put("text","合成译文\n"+cue.getString("text")));}return new JSONObject().put("Enabled",true).put("Key","fixture-key").put("Complete",false).put("Cues",result);}
            if(browseFeatures&&path.contains("/Collections?"))return new JSONObject().put("Items",new JSONArray().put(new JSONObject().put("Id","collection-1").put("Name","合成合集").put("Type","BoxSet"))).put("TotalRecordCount",1);
            if(browseFeatures&&(path.contains("/Browse?")||path.contains("/Items?"))){lastBrowsePath=path;return new JSONObject().put("Items",new JSONArray().put(base("movie-browse","Movie"))).put("TotalRecordCount",1);}
            if(path.contains("check_status")){discoveryChecks++;return new JSONObject().put("user",new JSONObject().put("can_access_discovery",discoveryAllowed));}
            if(path.contains("-resources?")){cloudPaths.add(path);return new JSONObject().put("resources",new JSONArray());}
            if(path.contains("/Refresh?"))return new JSONObject().put("submitted",true).put("RefreshRunId",123);
            if(path.contains("/Refresh/")){refreshStatusRequests++;return new JSONObject().put("RefreshRunId",123).put("status",taskStatus).put("finished",!taskStatus.equals("running"));}
            if(path.contains("/Users/Me"))return new JSONObject().put("Policy",new JSONObject().put("IsAdministrator",admin));
            if(path.contains("/FavoriteItems/")||path.contains("/PlayedItems/"))return new JSONObject().put("IsFavorite",favorite).put("Played",watched);
            if(path.contains("/Episodes")&&!path.contains("Fields=")&&nextGate!=null){nextRequested.countDown();if(!nextGate.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Synthetic next-episode gate timed out");}
            if(path.contains("/Sessions/")){reports++;return new JSONObject();}
            if(path.contains("/PlaybackPreferences"))return new JSONObject().put("preferences",new JSONObject(personal.toString())).put("global_preferences",new JSONObject().put("audio_lang_priority",new JSONArray().put("chi").put("original").put("eng")).put("subtitle_priority",new JSONArray().put("effect").put("chs")));
            if(path.contains("/Similar"))return new JSONObject().put("Items",new JSONArray().put(base("movie-similar","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1/Items"))return new JSONObject().put("Items",new JSONArray().put(base("movie-person","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1"))return new JSONObject().put("Id","person-1").put("Name","合成演员").put("Overview","合成人物简介");
            if(path.contains("/PlaybackInfo")){JSONArray original=sources(!path.contains("movie-single")),reversed=new JSONArray();for(int i=original.length()-1;i>=0;i--)reversed.put(original.get(i));return new JSONObject().put("MediaSources",reversed).put("PlaySessionId","synthetic-play");}
            if(path.contains("/Seasons")){JSONArray seasons=new JSONArray().put(new JSONObject().put("Id","season-1").put("Name","第 1 季"));if(path.contains("series-many"))seasons.put(new JSONObject().put("Id","season-2").put("Name","第 2 季"));return new JSONObject().put("Items",seasons);}
            if(path.contains("/Episodes")){JSONArray episodes=new JSONArray();int seasons=path.contains("series-many")?2:1;for(int s=1;s<=seasons;s++)for(int e=1;e<=(s==1?24:12);e++)episodes.put(new JSONObject().put("Id","episode-"+s+"-"+e).put("Type","Episode").put("Name","故事 "+e).put("SeriesName","合成剧集").put("SeriesId",seasons==2?"series-many":"series-one").put("SeasonId","season-"+s).put("ParentIndexNumber",s).put("IndexNumber",e).put("RunTimeTicks",100000000).put("Overview","第 "+e+" 集的合成简介").put("ParentBackdropItemId","series-parent").put("MediaSources",sources(false)).put("UserData",new JSONObject().put("PlaybackPositionTicks",!noProgress&&s==seasons&&e==3?30000000:0).put("Played",e==1)));if(placeholderDates)for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.getJSONObject(i);int n=ep.optInt("IndexNumber");if(n>=4&&n<=7){ep.put("IsMissing",true).put("LocationType","Virtual");if(n<=6)ep.put("PremiereDate",placeholderDate(n-4)+"T00:00:00Z");}}if(!path.contains("Fields=MediaSources,MediaStreams,Overview"))for(int i=0;i<episodes.length();i++)episodes.getJSONObject(i).remove("MediaSources");return new JSONObject().put("Items",episodes);}
            String id=path.substring(path.lastIndexOf('/')+1).split("\\?")[0];boolean series=id.startsWith("series");JSONObject item=base(id,series?"Series":id.startsWith("episode-")?"Episode":"Movie");if(browseFeatures){item.put(series?"Networks":"Companies",new JSONArray().put(series?"中文平台":"中文公司"));if(studioIcons)item.put("StudioImageTags",new JSONObject().put(series?"Networks":"Companies",new JSONObject().put(series?"中文平台":"中文公司","studio-icon")));}if(liveArtwork&&changed)item.put("Name","服务器更新后的片名").put("Overview","服务器已更新的简介").put("TMDbRating",8.2).put("DoubanRating",9.1).put("CustomRating","自定义 16+");if(id.equals("movie-no-overview"))item.put("Overview","");if(id.startsWith("episode-"))item.put("SeriesId","series-one");if(!series)item.put("MediaSources",sources(!id.equals("movie-single")));if(id.equals("movie-chapters")||id.equals("movie-no-overview"))item.put("Chapters",new JSONArray().put(new JSONObject().put("Name","开场").put("ChapterIndex",0).put("StartPositionTicks",0).put("ImageTag","chapter-0").put("ImagePath","/api/tv/Items/movie-chapters/Images/Chapter/0")).put(new JSONObject().put("Name","转折").put("ChapterIndex",1).put("StartPositionTicks",600000000).put("ImageTag","chapter-1").put("ImagePath","/api/tv/Items/movie-chapters/Images/Chapter/1")));return item;
        }
        void serve(Socket client)throws Exception{
            try(client){BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String line=reader.readLine();if(line==null)return;String path=line.split(" ")[1];boolean post=line.startsWith("POST ");int range=0,length=0;while((line=reader.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes="))range=Integer.parseInt(line.substring(13).split("-")[0]);if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
                if(liveArtwork&&path.endsWith("/Home/Events")){
                    OutputStream events=client.getOutputStream();events.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: home\ndata: {\"revision\":\"v1\"}\n\n".getBytes(StandardCharsets.UTF_8));events.flush();eventsConnected.countDown();
                    updateGate.await(20,java.util.concurrent.TimeUnit.SECONDS);if(!closed){events.write("event: home\ndata: {\"revision\":\"v2\"}\n\nevent: home\ndata: {\"revision\":\"v2\"}\n\n".getBytes(StandardCharsets.UTF_8));events.flush();while(!closed){Thread.sleep(1000);events.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));events.flush();}}return;
                }
                if(path.contains("/FavoriteItems/"))favorite=post;if(path.contains("/PlayedItems/"))watched=post;
                if(post&&path.contains("/PlaybackPreferences")){char[] body=new char[length];int received=0,n;while(received<length&&(n=reader.read(body,received,length-received))>0)received+=n;JSONObject changes=new JSONObject(new String(body));for(String key:new String[]{"audio_lang_priority","subtitle_priority"})if(changes.has(key))personal.put(key,changes.get(key));}
                if(post&&path.contains("/Sessions/Playing/Stopped")){char[] data=new char[length];int received=0,n;while(received<length&&(n=reader.read(data,received,length-received))>0)received+=n;stopped.add(new JSONObject(new String(data)));}
                if(post&&path.endsWith("/SubtitleTranslation")){char[] data=new char[length];int received=0,n;while(received<length&&(n=reader.read(data,received,length-received))>0)received+=n;JSONArray cues=new JSONObject(new String(data)).getJSONArray("cues");synchronized(translationCues){for(int i=0;i<cues.length();i++){JSONObject cue=cues.getJSONObject(i);if(translationCues.stream().noneMatch(c->c.toString().equals(cue.toString())))translationCues.add(cue);}}}
                if(path.endsWith("/SubtitleTranslation")&&translationGate!=null){java.util.concurrent.CountDownLatch gate=translationGate;translationRequested.countDown();if(!gate.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Synthetic translation gate timed out");if(translationGate==gate)translationGate=new java.util.concurrent.CountDownLatch(1);}
                if(!path.startsWith("/api/tv/"))unexpectedPath=path;
                boolean picture=path.contains("/Images/")||path.contains("/FacetArtwork?"),video=path.startsWith("/api/tv/media.mkv"),subtitle=path.contains("subtitle.vtt"),missing=path.contains("/Images/Logo")&&!path.contains("movie-single")||path.contains("/FacetArtwork?")&&path.contains("Kind=Networks");if(picture)imagePaths.add(path);
                byte[] body=video?media:subtitle?(pgsSubtitle?PlaybackCompatibilityTest.supSample():"WEBVTT\n\n00:00:00.000 --> 00:00:20.000\nExternal synthetic subtitle\n".getBytes(StandardCharsets.UTF_8)):picture?(path.contains("tag=art2")?updatedImage:image):response(path).toString().getBytes(StandardCharsets.UTF_8);
                if(holdMetadata&&!post&&!picture&&path.contains("/Users/")&&path.contains("/Items/")){metadataRequested.countDown();metadataGate.await(15,java.util.concurrent.TimeUnit.SECONDS);}
                if(holdIdentity&&path.contains("/Items/movie-single/Images/")){if(path.contains("/Backdrop")){backgroundRequested.countDown();backdropGate.await(15,java.util.concurrent.TimeUnit.SECONDS);}if(path.contains("/Logo"))logoRequested.countDown();}
                String code=failNext&&path.contains("/Episodes")&&!path.contains("Fields=")?"500 Internal Server Error":missing?"404 Not Found":range>0?"206 Partial Content":"200 OK";int start=video?range:0;
                String headers="HTTP/1.1 "+code+"\r\nContent-Type: "+(video?"video/x-matroska":subtitle?"text/vtt":picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+(body.length-start)+"\r\n"+(range>0?"Content-Range: bytes "+start+"-"+(body.length-1)+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n";
                OutputStream output=client.getOutputStream();output.write(headers.getBytes(StandardCharsets.US_ASCII));output.write(body,start,body.length-start);
            }
        }
        @Override public void close()throws IOException{closed=true;backdropGate.countDown();updateGate.countDown();metadataGate.countDown();if(translationGate!=null)translationGate.countDown();socket.close();}
    }
}
