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

    @Test public void playbackUsesEntireViewportAndDownOpensControls()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-single");play();
            onMain(()->{
                View video=(View)field(playback,"video"),decor=playback.getWindow().getDecorView();
                assertEquals("视频必须占满视口宽度",decor.getWidth(),video.getWidth());
                assertEquals("视频必须占满视口高度",decor.getHeight(),video.getHeight());
                assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility());
            });
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();
            onMain(()->assertEquals(View.VISIBLE,((View)field(playback,"controls")).getVisibility()));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
            assertFalse(playback.isFinishing());
            onMain(()->assertEquals(View.GONE,((View)field(playback,"controls")).getVisibility()));
        }finally{cleanup();}
    }

    @Test public void playerUsesNormalizedTitlesExactDefaultsAndPreservesUnavailableTracks()throws Exception{
        try(Fixture server=new Fixture()){
            server.preferSecond=true;open(server,"movie-single");assertTrue(button("音轨").getText().toString().contains("音轨二"));assertTrue(button("字幕").getText().toString().contains("内封二"));play();
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO))&&"4".equals(selected(C.TRACK_TYPE_TEXT)));
            assertTrue(((Button)field(playback,"audioButton")).getText().toString().contains("音轨二"));clickTrack(false,1);await(()->"1".equals(selected(C.TRACK_TYPE_AUDIO)));
            assertTrue(((Button)field(playback,"audioButton")).getText().toString().contains("音轨一"));
            onMain(()->((Button)field(playback,"subtitleButton")).performClick());settle();
            onMain(()->{ListView list=((AlertDialog)field(playback,"trackDialog")).getListView();assertEquals(5,list.getCount());assertEquals("内封一",list.getAdapter().getItem(2));((AlertDialog)field(playback,"trackDialog")).dismiss();});closePlayer();
            server.extraTrack=true;play();onMain(()->((Button)field(playback,"audioButton")).performClick());settle();
            onMain(()->{AlertDialog dialog=(AlertDialog)field(playback,"trackDialog");ListView list=dialog.getListView();assertEquals("全部服务端音轨必须保留",4,list.getCount());assertTrue(list.getAdapter().getItem(3).toString().contains("未解析音轨"));list.performItemClick(null,3,3);assertTrue("无法可靠对应的轨道不可静默选错",dialog.isShowing());assertEquals(PlaybackSelection.AUTO,field(playback,"audioIndex"));dialog.dismiss();});
        }finally{cleanup();}
    }

    @Test public void continueWatchingEpisodeIncludesTheOtherEpisodes()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"episode-1-2");assertNotNull(button("第 1 集"));assertNotNull(button("第 24 集"));assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 2"));assertEquals("episode-1-2",((JSONObject)field(detail,"playable")).getString("Id"));screenshot("resume-episode-selection.png");
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
            await(()->{for(View view:descendants((View)field(playback,"video")))if(view instanceof TextureView&&view.getClass().getSimpleName().contains("AssSubtitle")){Bitmap image=((TextureView)view).getBitmap();if(image==null)return false;boolean pixels=false;for(int y=0;y<image.getHeight()&&!pixels;y+=3)for(int x=0;x<image.getWidth();x+=3)if(Color.alpha(image.getPixel(x,y))>100){pixels=true;break;}image.recycle();return pixels;}return false;});screenshot("hevc-native-audio-ass.png");
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

    private void assertVisibleVideoPixels() throws Exception{
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot();int colored=0,total=0,greenDominant=0;
        java.util.HashSet<Integer> colors=new java.util.HashSet<>();
        int minRed=255,maxRed=0,minGreen=255,maxGreen=0,minBlue=255,maxBlue=0;
        for(int y=image.getHeight()/3;y<image.getHeight()*2/3;y+=10)for(int x=image.getWidth()/3;x<image.getWidth()*2/3;x+=10){
            int color=image.getPixel(x,y),red=Color.red(color),green=Color.green(color),blue=Color.blue(color);total++;
            if(Math.max(red,Math.max(green,blue))>35)colored++;
            if(green>red*1.25f&&green>blue*1.25f)greenDominant++;
            colors.add((red>>4)<<8|(green>>4)<<4|(blue>>4));
            minRed=Math.min(minRed,red);maxRed=Math.max(maxRed,red);minGreen=Math.min(minGreen,green);maxGreen=Math.max(maxGreen,green);minBlue=Math.min(minBlue,blue);maxBlue=Math.max(maxBlue,blue);
        }
        image.recycle();
        assertTrue("字幕显示时，中央视频区域必须有实际画面像素",colored>total/5);
        assertTrue("视频画面不能退化成单色平面",colors.size()>=8&&maxRed-minRed>=24&&maxGreen-minGreen>=24&&maxBlue-minBlue>=24);
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
            onMain(()->((androidx.media3.ui.PlayerView)field(playback,"video")).hideController());settle();assertVisibleVideoPixels();screenshot(file+".png");
        }finally{cleanup();}
    }

    @Test public void remoteSeekingShowsTimelineAndKeepsControlsNavigable()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,"movie-single");new TvPreferences(detail,detail.api).setSkipMode(TvPreferences.SKIP_OFF);play();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();
            onMain(()->{androidx.media3.ui.PlayerView view=(androidx.media3.ui.PlayerView)field(playback,"video");assertTrue(view.isControllerFullyVisible());assertTrue(view.findViewById(androidx.media3.ui.R.id.exo_progress).isShown());assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()>=10000);});
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);settle();onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getCurrentPosition()<3000));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();assertEquals(field(playback,"audioButton"),playback.getCurrentFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getPlayWhenReady()));
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
        volatile boolean closed,preferSecond,extraTrack;volatile String unexpectedPath;volatile int reports;
        final boolean codecMedia;
        final JSONObject personal=new JSONObject();
        Fixture()throws Exception{this(false);}
        Fixture(boolean codecs)throws Exception{this(codecs,codecs?"player-codecs.mkv":"detail-tracks.mkv");}
        Fixture(boolean codecs,String file)throws Exception{codecMedia=codecs;personal.put("audio_lang_priority",JSONObject.NULL).put("subtitle_priority",new JSONArray().put("chs"));
            Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(38,60,84));Paint paint=new Paint();paint.setColor(Color.rgb(95,140,155));canvas.drawCircle(750,180,220,paint);paint.setColor(Color.rgb(205,155,85));canvas.drawRect(620,300,900,500,paint);ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);bitmap.recycle();image=output.toByteArray();
            File external=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"codec-fixtures/"+file);
            if("player-vc1.mkv".equals(file))org.junit.Assume.assumeTrue("请按构建规范放置 FFmpeg VC1 测试片段；不随公开仓库分发",external.isFile());
            try(InputStream stream=external.isFile()?new FileInputStream(external):instrumentation.getContext().getAssets().open(file)){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)data.write(buffer,0,n);media=data.toByteArray();}
            Thread thread=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DetailFixture","request",error);}});worker.setDaemon(true);worker.start();}catch(IOException ignored){if(closed)return;}},"detail-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject stream(String type,int index,String title)throws Exception{return new JSONObject().put("Type",type).put("Index",index).put("DisplayTitle",title).put("Title","raw-title-"+index).put("Language","chi").put("Codec",type.equals("Audio")?"pcm_s16le":"subrip");}
        JSONArray sources(boolean multi)throws Exception{
            if(codecMedia){JSONArray streams=new JSONArray();String[] codecs={"aac","ac3","eac3","dts","truehd"};for(int i=0;i<codecs.length;i++)streams.put(stream("Audio",i+1,"归一化 "+codecs[i]).put("Title","raw-"+codecs[i]).put("Codec",codecs[i]));streams.put(stream("Subtitle",6,"特效字幕 ASS").put("Title","raw-effect").put("Codec","ass"));JSONArray chapters=new JSONArray();String[] types={"IntroStart","IntroEnd","CreditsStart"};long[] ticks={10000000,30000000,100000000};for(int i=0;i<types.length;i++)chapters.put(new JSONObject().put("MarkerType",types[i]).put("StartPositionTicks",ticks[i]));return new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","HEVC 多编码").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",1).put("DefaultSubtitleStreamIndex",6).put("Chapters",chapters));}
            JSONArray streams=new JSONArray().put(stream("Audio",0,"音轨一").put("IsDefault",true)).put(stream("Audio",4,"音轨二")).put(stream("Subtitle",7,"内封一")).put(stream("Subtitle",8,"内封二")).put(stream("Subtitle",12,"外挂字幕").put("IsExternal",true).put("Codec","webvtt").put("DeliveryUrl","/api/tv/subtitle.vtt"));
            if(extraTrack)streams.put(stream("Audio",100,"未解析音轨"));
            JSONArray sources=new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","1080P").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultAudioStreamIndex",preferSecond?4:0).put("DefaultSubtitleStreamIndex",preferSecond?8:-1));
            if(multi)sources.put(new JSONObject(sources.getJSONObject(0).toString()).put("Id","source-b").put("Name","4K 版本"));return sources;
        }
        JSONObject base(String id,String type)throws Exception{return new JSONObject().put("Id",id).put("Type",type).put("Name",type.equals("Movie")?"合成电影":"合成剧集").put("Overview","这是用于详情页验收的合成简介。遥控器可选择播放版本、音轨、字幕，也可浏览演员与分集预览。").put("ProductionYear",2026).put("TMDbRating",7.6).put("DoubanRating",8.7).put("OfficialRating","PG-13").put("RunTimeTicks",95L*600000000).put("Genres",new JSONArray().put("剧情").put("悬疑")).put("Tags",new JSONArray().put("悬疑").put("经典")).put("People",new JSONArray().put(new JSONObject().put("Id","person-1").put("Name","合成演员").put("Type","Actor").put("Role","主演")));}
        JSONObject response(String path)throws Exception{
            if(path.contains("/Sessions/")){reports++;return new JSONObject();}
            if(path.contains("/PlaybackPreferences"))return new JSONObject().put("preferences",new JSONObject(personal.toString())).put("global_preferences",new JSONObject().put("audio_lang_priority",new JSONArray().put("chi").put("original").put("eng")).put("subtitle_priority",new JSONArray().put("effect").put("chs")));
            if(path.contains("/Similar"))return new JSONObject().put("Items",new JSONArray().put(base("movie-similar","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1/Items"))return new JSONObject().put("Items",new JSONArray().put(base("movie-person","Movie"))).put("TotalRecordCount",1);
            if(path.contains("/Persons/person-1"))return new JSONObject().put("Id","person-1").put("Name","合成演员").put("Overview","合成人物简介");
            if(path.contains("/PlaybackInfo")){JSONArray original=sources(!path.contains("movie-single")),reversed=new JSONArray();for(int i=original.length()-1;i>=0;i--)reversed.put(original.get(i));return new JSONObject().put("MediaSources",reversed).put("PlaySessionId","synthetic-play");}
            if(path.contains("/Seasons")){JSONArray seasons=new JSONArray().put(new JSONObject().put("Id","season-1").put("Name","第 1 季"));if(path.contains("series-many"))seasons.put(new JSONObject().put("Id","season-2").put("Name","第 2 季"));return new JSONObject().put("Items",seasons);}
            if(path.contains("/Episodes")){JSONArray episodes=new JSONArray();int seasons=path.contains("series-many")?2:1;for(int s=1;s<=seasons;s++)for(int e=1;e<=(s==1?24:12);e++)episodes.put(new JSONObject().put("Id","episode-"+s+"-"+e).put("Type","Episode").put("Name","故事 "+e).put("SeriesName","合成剧集").put("SeasonId","season-"+s).put("ParentIndexNumber",s).put("IndexNumber",e).put("Overview","第 "+e+" 集的合成简介").put("ParentBackdropItemId","series-parent").put("MediaSources",sources(false)).put("UserData",new JSONObject().put("PlaybackPositionTicks",s==seasons&&e==3?30000000:0)));if(!path.contains("Fields=MediaSources,MediaStreams,Overview"))for(int i=0;i<episodes.length();i++)episodes.getJSONObject(i).remove("MediaSources");return new JSONObject().put("Items",episodes);}
            String id=path.substring(path.lastIndexOf('/')+1).split("\\?")[0];boolean series=id.startsWith("series");JSONObject item=base(id,series?"Series":id.startsWith("episode-")?"Episode":"Movie");if(id.startsWith("episode-"))item.put("SeriesId","series-one");if(!series)item.put("MediaSources",sources(!id.equals("movie-single")));return item;
        }
        void serve(Socket client)throws Exception{
            try(client){BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String line=reader.readLine();if(line==null)return;String path=line.split(" ")[1];boolean post=line.startsWith("POST ");int range=0,length=0;while((line=reader.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes="))range=Integer.parseInt(line.substring(13).split("-")[0]);if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
                if(post&&path.contains("/PlaybackPreferences")){char[] body=new char[length];int received=0,n;while(received<length&&(n=reader.read(body,received,length-received))>0)received+=n;JSONObject changes=new JSONObject(new String(body));for(String key:new String[]{"audio_lang_priority","subtitle_priority"})if(changes.has(key))personal.put(key,changes.get(key));}
                if(!path.startsWith("/api/tv/"))unexpectedPath=path;
                boolean picture=path.contains("/Images/"),video=path.startsWith("/api/tv/media.mkv"),subtitle=path.contains("subtitle.vtt"),missing=path.contains("/Images/Logo")&&!path.contains("movie-single");if(picture)imagePaths.add(path);
                byte[] body=video?media:subtitle?"WEBVTT\n\n00:00:00.000 --> 00:00:20.000\nExternal synthetic subtitle\n".getBytes(StandardCharsets.UTF_8):picture?image:response(path).toString().getBytes(StandardCharsets.UTF_8);
                String code=missing?"404 Not Found":range>0?"206 Partial Content":"200 OK";int start=video?range:0;
                String headers="HTTP/1.1 "+code+"\r\nContent-Type: "+(video?"video/x-matroska":subtitle?"text/vtt":picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+(body.length-start)+"\r\n"+(range>0?"Content-Range: bytes "+start+"-"+(body.length-1)+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n";
                OutputStream output=client.getOutputStream();output.write(headers.getBytes(StandardCharsets.US_ASCII));output.write(body,start,body.length-start);
            }
        }
        @Override public void close()throws IOException{closed=true;socket.close();}
    }
}
