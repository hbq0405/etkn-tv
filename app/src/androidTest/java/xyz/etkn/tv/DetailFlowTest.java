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
    private DetailActivity detail;
    private PlayerActivity playback;
    private void onMain(Runnable action){instrumentation.runOnMainSync(action);}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){boolean[] value={false};onMain(()->value[0]=condition.getAsBoolean());if(value[0])return;Thread.sleep(100);}fail("Timed out waiting for detail/playback state");}
    private void settle(){instrumentation.waitForIdleSync();SystemClock.sleep(450);instrumentation.waitForIdleSync();}
    private Object field(Object owner,String name){try{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    private List<View> descendants(View root){List<View> views=new ArrayList<>();views.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)views.addAll(descendants(((ViewGroup)root).getChildAt(i)));return views;}
    private Button button(String prefix){for(View view:descendants(detail.content))if(view instanceof Button&&((Button)view).getText().toString().startsWith(prefix))return (Button)view;return null;}
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
            assertTrue("控制栏显示时也必须一次返回退出播放",playback.isFinishing());
            await(()->field(playback,"player")==null&&server.stopped.size()==1&&detail.hasWindowFocus());
        }finally{cleanup();}
    }

    @Test public void backExitsPlaybackImmediatelyAndReportsCurrentPosition()throws Exception{
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
                assertTrue("播放中或快进后都必须一次返回退出",playback.isFinishing());
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
        assertSame(field(playback,"nextButton"),playback.getCurrentFocus());screenshot("next-episode-controls.png");
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
            open(server,"movie");assertEquals(View.VISIBLE,button("版本").getVisibility());
            assertTrue(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&view.isShown()&&((TextView)view).getText().toString().equals("合成电影")));
            screenshot("movie.png");choose("版本",1);choose("音轨",2);choose("字幕",3);
            assertTrue(button("音轨").getText().toString().contains("音轨二"));assertTrue(button("字幕").getText().toString().contains("内封二"));screenshot("movie-choices.png");
            play();assertEquals("source-b",playback.getIntent().getStringExtra("sourceId"));assertEquals(4,playback.getIntent().getIntExtra("audioIndex",-1));assertEquals(8,playback.getIntent().getIntExtra("subtitleIndex",-1));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO))&&"4".equals(selected(C.TRACK_TYPE_TEXT)));
            assertEquals("source-b",((JSONObject)field(playback,"source")).getString("Id"));closePlayer();
            assertNull("客户端只能访问 TV 接口",server.unexpectedPath);assertTrue(server.reports>0);
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
            open(server,"series-one");assertNull(button("第 1 季"));assertTrue("分集必须显式请求版本和轨道字段",((JSONArray)field(detail,"sources")).length()>0);assertEquals("继续播放",((Button)field(detail,"playButton")).getText().toString());screenshot("single-season-entry.png");
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

    private final class Fixture implements AutoCloseable{
        final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        final byte[] image,media;
        final List<String> imagePaths=Collections.synchronizedList(new ArrayList<>());
        final List<JSONObject> stopped=Collections.synchronizedList(new ArrayList<>());
        volatile java.util.concurrent.CountDownLatch nextRequested,nextGate;
        volatile boolean closed,preferSecond,extraTrack,pgsSubtitle,ordinalFixture,missingAudio,noProgress,failNext;volatile int audioOffset;volatile String unexpectedPath;volatile int reports;
        final boolean codecMedia;
        final JSONObject personal=new JSONObject();
        Fixture()throws Exception{this(false);}
        Fixture(boolean codecs)throws Exception{this(codecs,codecs?"player-codecs.mkv":"detail-tracks.mkv");}
        Fixture(boolean codecs,String file)throws Exception{codecMedia=codecs;personal.put("audio_lang_priority",JSONObject.NULL).put("subtitle_priority",new JSONArray().put("chs"));
            Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(38,60,84));Paint paint=new Paint();paint.setColor(Color.rgb(95,140,155));canvas.drawCircle(750,180,220,paint);paint.setColor(Color.rgb(205,155,85));canvas.drawRect(620,300,900,500,paint);ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);bitmap.recycle();image=output.toByteArray();
            File local=new File(instrumentation.getTargetContext().getFilesDir(),"codec-fixtures/"+file);
            File external=local.isFile()?local:new File(instrumentation.getTargetContext().getExternalFilesDir(null),"codec-fixtures/"+file);
            if("player-vc1.mkv".equals(file))org.junit.Assume.assumeTrue("请按构建规范放置 FFmpeg VC1 测试片段；不随公开仓库分发",external.isFile());
            try(InputStream stream=external.isFile()?new FileInputStream(external):instrumentation.getContext().getAssets().open(file)){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)data.write(buffer,0,n);media=data.toByteArray();}
            Thread thread=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DetailFixture","request",error);}});worker.setDaemon(true);worker.start();}catch(IOException ignored){if(closed)return;}},"detail-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject stream(String type,int index,String title)throws Exception{return new JSONObject().put("Type",type).put("Index",index).put("DisplayTitle",title+" · 主标题 (默认)").put("Title",title+" · 副标题").put("Language","chi").put("Codec",type.equals("Audio")?"pcm_s16le":"subrip");}
        JSONArray sources(boolean multi)throws Exception{
            if(codecMedia){JSONArray streams=new JSONArray();String[] codecs={"aac","ac3","eac3","dts","truehd"};for(int i=0;i<codecs.length;i++)streams.put(stream("Audio",i+1,"归一化 "+codecs[i]).put("Title","raw-"+codecs[i]).put("Codec",codecs[i]));streams.put(stream("Subtitle",6,"特效字幕 ASS").put("Title","raw-effect").put("Codec","ass"));
                if(pgsSubtitle)streams.put(stream("Subtitle",12,"外挂 PGS").put("IsExternal",true).put("Codec","pgs").put("DeliveryUrl","/api/tv/subtitle.vtt"));
                JSONArray chapters=new JSONArray();String[] types={"IntroStart","IntroEnd","CreditsStart"};long[] ticks={10000000,30000000,100000000};for(int i=0;i<types.length;i++)chapters.put(new JSONObject().put("MarkerType",types[i]).put("StartPositionTicks",ticks[i]));return new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","HEVC 多编码").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",1).put("DefaultSubtitleStreamIndex",6).put("Chapters",chapters));}
            JSONArray streams=new JSONArray().put(stream("Audio",0,"音轨一").put("IsDefault",true)).put(stream("Audio",4,"音轨二")).put(stream("Subtitle",7,"内封一")).put(stream("Subtitle",8,"内封二")).put(stream("Subtitle",12,"外挂字幕").put("IsExternal",true).put("Codec","webvtt").put("DeliveryUrl","/api/tv/subtitle.vtt"));
            if(pgsSubtitle)streams.getJSONObject(4).put("Codec","pgs");
            if(extraTrack)streams.put(stream("Audio",100,"未解析音轨"));
            if(ordinalFixture){streams.getJSONObject(0).put("Index",audioOffset).put("Title","国语");streams.getJSONObject(1).put("Index",audioOffset+4).put("Title","国语");if(missingAudio)streams.remove(1);}
            JSONArray sources=new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","1080P").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",preferSecond?4:0).put("DefaultSubtitleStreamIndex",preferSecond?8:-1));
            if(ordinalFixture)sources.getJSONObject(0).put("DefaultAudioStreamIndex",audioOffset);
            if(multi)sources.put(new JSONObject(sources.getJSONObject(0).toString()).put("Id","source-b").put("Name","4K 版本"));return sources;
        }
        JSONObject base(String id,String type)throws Exception{return new JSONObject().put("Id",id).put("Type",type).put("Name",type.equals("Movie")?"合成电影":"合成剧集").put("Overview","这是用于详情页验收的合成简介。遥控器可选择播放版本、音轨、字幕，也可浏览演员与分集预览。").put("ProductionYear",2026).put("TMDbRating",7.6).put("DoubanRating",8.7).put("OfficialRating","PG-13").put("RunTimeTicks",95L*600000000).put("Genres",new JSONArray().put("剧情").put("悬疑")).put("Tags",new JSONArray().put("悬疑").put("经典")).put("People",new JSONArray().put(new JSONObject().put("Id","person-1").put("Name","合成演员").put("Type","Actor").put("Role","主演")));}
        JSONObject response(String path)throws Exception{
            if(path.contains("/Episodes")&&!path.contains("Fields=")&&nextGate!=null){nextRequested.countDown();if(!nextGate.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Synthetic next-episode gate timed out");}
            if(path.contains("/Sessions/")){reports++;return new JSONObject();}
            if(path.contains("/PlaybackPreferences"))return new JSONObject().put("preferences",new JSONObject(personal.toString())).put("global_preferences",new JSONObject().put("audio_lang_priority",new JSONArray().put("chi").put("original").put("eng")).put("subtitle_priority",new JSONArray().put("effect").put("chs")));
            if(path.contains("/Similar"))return new JSONObject().put("Items",new JSONArray().put(base("movie-similar","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1/Items"))return new JSONObject().put("Items",new JSONArray().put(base("movie-person","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1"))return new JSONObject().put("Id","person-1").put("Name","合成演员").put("Overview","合成人物简介");
            if(path.contains("/PlaybackInfo")){JSONArray original=sources(!path.contains("movie-single")),reversed=new JSONArray();for(int i=original.length()-1;i>=0;i--)reversed.put(original.get(i));return new JSONObject().put("MediaSources",reversed).put("PlaySessionId","synthetic-play");}
            if(path.contains("/Seasons")){JSONArray seasons=new JSONArray().put(new JSONObject().put("Id","season-1").put("Name","第 1 季"));if(path.contains("series-many"))seasons.put(new JSONObject().put("Id","season-2").put("Name","第 2 季"));return new JSONObject().put("Items",seasons);}
            if(path.contains("/Episodes")){JSONArray episodes=new JSONArray();int seasons=path.contains("series-many")?2:1;for(int s=1;s<=seasons;s++)for(int e=1;e<=(s==1?24:12);e++)episodes.put(new JSONObject().put("Id","episode-"+s+"-"+e).put("Type","Episode").put("Name","故事 "+e).put("SeriesName","合成剧集").put("SeriesId",seasons==2?"series-many":"series-one").put("SeasonId","season-"+s).put("ParentIndexNumber",s).put("IndexNumber",e).put("RunTimeTicks",100000000).put("Overview","第 "+e+" 集的合成简介").put("ParentBackdropItemId","series-parent").put("MediaSources",sources(false)).put("UserData",new JSONObject().put("PlaybackPositionTicks",!noProgress&&s==seasons&&e==3?30000000:0).put("Played",e==1)));if(!path.contains("Fields=MediaSources,MediaStreams,Overview"))for(int i=0;i<episodes.length();i++)episodes.getJSONObject(i).remove("MediaSources");return new JSONObject().put("Items",episodes);}
            String id=path.substring(path.lastIndexOf('/')+1).split("\\?")[0];boolean series=id.startsWith("series");JSONObject item=base(id,series?"Series":id.startsWith("episode-")?"Episode":"Movie");if(id.equals("movie-no-overview"))item.put("Overview","");if(id.startsWith("episode-"))item.put("SeriesId","series-one");if(!series)item.put("MediaSources",sources(!id.equals("movie-single")));if(id.equals("movie-chapters")||id.equals("movie-no-overview"))item.put("Chapters",new JSONArray().put(new JSONObject().put("Name","开场").put("ChapterIndex",0).put("StartPositionTicks",0).put("ImageTag","chapter-0").put("ImagePath","/api/tv/Items/movie-chapters/Images/Chapter/0")).put(new JSONObject().put("Name","转折").put("ChapterIndex",1).put("StartPositionTicks",600000000).put("ImageTag","chapter-1").put("ImagePath","/api/tv/Items/movie-chapters/Images/Chapter/1")));return item;
        }
        void serve(Socket client)throws Exception{
            try(client){BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String line=reader.readLine();if(line==null)return;String path=line.split(" ")[1];boolean post=line.startsWith("POST ");int range=0,length=0;while((line=reader.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes="))range=Integer.parseInt(line.substring(13).split("-")[0]);if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
                if(post&&path.contains("/PlaybackPreferences")){char[] body=new char[length];int received=0,n;while(received<length&&(n=reader.read(body,received,length-received))>0)received+=n;JSONObject changes=new JSONObject(new String(body));for(String key:new String[]{"audio_lang_priority","subtitle_priority"})if(changes.has(key))personal.put(key,changes.get(key));}
                if(post&&path.contains("/Sessions/Playing/Stopped")){char[] data=new char[length];int received=0,n;while(received<length&&(n=reader.read(data,received,length-received))>0)received+=n;stopped.add(new JSONObject(new String(data)));}
                if(!path.startsWith("/api/tv/"))unexpectedPath=path;
                boolean picture=path.contains("/Images/"),video=path.startsWith("/api/tv/media.mkv"),subtitle=path.contains("subtitle.vtt"),missing=path.contains("/Images/Logo")&&!path.contains("movie-single");if(picture)imagePaths.add(path);
                byte[] body=video?media:subtitle?(pgsSubtitle?PlaybackCompatibilityTest.supSample():"WEBVTT\n\n00:00:00.000 --> 00:00:20.000\nExternal synthetic subtitle\n".getBytes(StandardCharsets.UTF_8)):picture?image:response(path).toString().getBytes(StandardCharsets.UTF_8);
                String code=failNext&&path.contains("/Episodes")&&!path.contains("Fields=")?"500 Internal Server Error":missing?"404 Not Found":range>0?"206 Partial Content":"200 OK";int start=video?range:0;
                String headers="HTTP/1.1 "+code+"\r\nContent-Type: "+(video?"video/x-matroska":subtitle?"text/vtt":picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+(body.length-start)+"\r\n"+(range>0?"Content-Range: bytes "+start+"-"+(body.length-1)+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n";
                OutputStream output=client.getOutputStream();output.write(headers.getBytes(StandardCharsets.US_ASCII));output.write(body,start,body.length-start);
            }
        }
        @Override public void close()throws IOException{closed=true;socket.close();}
    }
}
