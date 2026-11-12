package xyz.etkn.tv;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.SubtitleView;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlayerDependencyIsolationTest {
    @Test public void media3UsesPrivateGuavaAndCreatesBothSubtitleModes() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Class<?> mediaItem=Class.forName("androidx.media3.common.MediaItem$DrmConfiguration",true,context.getClassLoader());
        String map=mediaItem.getField("licenseRequestHeaders").getType().getName();
        assertTrue("Packaged Media3 must reference private Guava: "+map,map.startsWith("xyz.etkn.tv.internal.guava."));
        Class.forName("androidx.media3.exoplayer.audio.AudioCapabilities",true,context.getClassLoader());
        for(boolean enhanced:new boolean[]{true,false}){
            AtomicReference<ExoPlayer> player=new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{player.set(TvPlayerFactory.create(context,new SubtitleView(context),false,false,enhanced));assertNotNull(player.get());}
                finally{if(player.get()!=null)player.get().release();}
            });
        }
    }
}
