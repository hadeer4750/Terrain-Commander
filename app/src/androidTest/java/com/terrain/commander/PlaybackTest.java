package com.terrain.commander;

import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import java.io.*;
import java.nio.*;
import static org.junit.Assert.*;

public class PlaybackTest {
    private Context context;
    private void await(String state)throws Exception{
        for(int n=0;n<100;n++){if(state.equals(new JSONObject(MusicService.snapshot).optString("state")))return;Thread.sleep(100);}
        fail("Expected "+state+" but got "+MusicService.snapshot);
    }
    private void send(String action){context.startService(new Intent(context,MusicService.class).setAction(action));}
    private Activity launch(Instrumentation inst)throws Exception{
        context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        AtomicReference<Activity> current=new AtomicReference<>();
        for(int n=0;n<150;n++){
            inst.runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof MainActivity)current.set(a);});
            if(current.get()!=null)return current.get();Thread.sleep(100);
        }
        throw new AssertionError("MainActivity did not reach RESUMED");
    }
    @Test public void playPauseResumeNextAndMissingFile()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();context=inst.getTargetContext();
        Activity activity=launch(inst);
        try{
            File wav=new File(context.getFilesDir(),"fixture.wav");
            int length=8000*2*20;ByteBuffer b=ByteBuffer.allocate(44+length).order(ByteOrder.LITTLE_ENDIAN);
            b.put("RIFF".getBytes("US-ASCII")).putInt(36+length).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(length);
            while(b.remaining()>=2)b.putShort((short)0);
            try(FileOutputStream stream=new FileOutputStream(wav)){stream.write(b.array());}
            JSONArray tracks=new JSONArray().put(new JSONObject().put("title","one.wav").put("uri",Uri.fromFile(wav).toString())).put(new JSONObject().put("title","two.wav").put("uri",Uri.fromFile(wav).toString()));
            context.getSharedPreferences("music",0).edit().putString("tracks",tracks.toString()).commit();
            context.startForegroundService(new Intent(context,MusicService.class).setAction("play").putExtra("index",0));await("playing");
            send("pause");await("paused");send("resume");await("playing");
            send("next");for(int n=0;n<50&&!"two.wav".equals(new JSONObject(MusicService.snapshot).optString("title"));n++)Thread.sleep(100);await("playing");assertEquals("two.wav",new JSONObject(MusicService.snapshot).getString("title"));
            // Playback remains alive while the app UI is no longer resumed.
            inst.runOnMainSync(()->activity.moveTaskToBack(true));Thread.sleep(300);assertEquals("playing",new JSONObject(MusicService.snapshot).getString("state"));
            send("stop");await("idle");
            launch(inst);
            tracks=new JSONArray().put(new JSONObject().put("title","missing").put("uri","file:///missing-music.wav"));context.getSharedPreferences("music",0).edit().putString("tracks",tracks.toString()).commit();
            context.startForegroundService(new Intent(context,MusicService.class).setAction("play").putExtra("index",0));await("error");
        }finally{context.stopService(new Intent(context,MusicService.class));inst.runOnMainSync(activity::finish);}
    }
}
