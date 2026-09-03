package com.terrain.commander;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import org.json.*;

/** Foreground local-file playback; no network media extraction. */
public class MusicService extends Service {
    public static volatile String snapshot="{\"state\":\"idle\",\"title\":\"\"}";
    public static volatile boolean running=false;
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private JSONArray tracks=new JSONArray();
    private int index=-1;
    private boolean ready=false,wantsPlay=false,resumeOnGain=false,ducked=false;
    private String title="",state="idle";
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause(false);}};
    @Override public void onCreate(){super.onCreate();running=true;
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{
            if(change==AudioManager.AUDIOFOCUS_GAIN){if(resumeOnGain){resumeOnGain=false;resume();}}
            else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){boolean was=wantsPlay;pause(true);resumeOnGain=was;}
            else if(change==AudioManager.AUDIOFOCUS_LOSS){pause(false);}
        }).build();
        session=new MediaSession(this,"TerrainLocalMusic");
        session.setCallback(new MediaSession.Callback(){public void onPlay(){resume();}public void onPause(){pause(false);}public void onStop(){finish();}public void onSkipToNext(){skip(1);}public void onSkipToPrevious(){skip(-1);}public void onSeekTo(long pos){if(ready&&player!=null)player.seekTo((int)Math.max(0,Math.min(pos,player.getDuration())));publish();}});session.setActive(true);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("music","موسيقى الهاتف",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(Build.VERSION.SDK_INT>=29)startForeground(31,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(31,notification());
        if(intent==null){finish();return START_NOT_STICKY;}String action=intent.getAction();
        if("play".equals(action)){try{tracks=new JSONArray(getSharedPreferences("music",0).getString("tracks","[]"));}catch(JSONException e){tracks=new JSONArray();}playAt(intent.getIntExtra("index",-1));}
        else if("pause".equals(action))pause(false);else if("resume".equals(action))resume();else if("next".equals(action))skip(1);else if("previous".equals(action))skip(-1);else if("repeat".equals(action))repeat();else if("duck".equals(action))duck(true);else if("unduck".equals(action))duck(false);else if("volume_up".equals(action))adjustVolume(1);else if("volume_down".equals(action))adjustVolume(-1);else if("stop".equals(action))finish();
        return START_NOT_STICKY;
    }
    private boolean gainFocus(){return audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
    private void playAt(int target){if(target<0||target>=tracks.length()){fail("اختر أغنية من المكتبة أولاً");return;}releasePlayer();index=target;JSONObject track=tracks.optJSONObject(index);if(track==null){fail("ملف غير صالح");return;}title=track.optString("title");wantsPlay=true;resumeOnGain=false;if(!gainFocus()){fail("الصوت مشغول؛ حاول بعد انتهاء المكالمة أو التطبيق الآخر");return;}state="loading";publish();MediaPlayer p=new MediaPlayer();player=p;p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);p.setOnPreparedListener(mp->{if(player!=mp)return;ready=true;applyDuck();if(wantsPlay){mp.start();state="playing";}else state="paused";publish();});p.setOnCompletionListener(mp->{if(player!=mp)return;if(index+1<tracks.length())playAt(index+1);else{wantsPlay=false;state="ended";audio.abandonAudioFocusRequest(focus);publish();}});p.setOnErrorListener((mp,what,extra)->{if(player==mp)fail("تعذر تشغيل الملف؛ قد تكون صيغته غير مدعومة أو تم نقله");return true;});try{p.setDataSource(this,Uri.parse(track.getString("uri")));p.prepareAsync();}catch(Exception e){fail("تعذر فتح الملف؛ اختر المجلد من جديد إذا تغيّرت صلاحياته");}}
    private void resume(){if(player==null){if(index>=0)playAt(index);else fail("اختر أغنية أولاً");return;}if(!gainFocus()){state="paused";publish();return;}wantsPlay=true;resumeOnGain=false;if(ready){applyDuck();player.start();state="playing";}publish();}
    private void pause(boolean transientLoss){wantsPlay=false;resumeOnGain=false;if(player!=null&&ready&&player.isPlaying())player.pause();if(player!=null)state="paused";if(!transientLoss)audio.abandonAudioFocusRequest(focus);publish();}
    private void repeat(){if(player!=null&&ready){player.seekTo(0);wantsPlay=true;if(!player.isPlaying())player.start();state="playing";publish();}else if(index>=0)playAt(index);}
    private void skip(int step){if(tracks.length()>0)playAt((Math.max(0,index)+step+tracks.length())%tracks.length());}
    private void duck(boolean on){ducked=on;applyDuck();}
    private void applyDuck(){if(player!=null)try{float v=ducked?0.18f:1f;player.setVolume(v,v);}catch(Exception ignored){}}
    private void adjustVolume(int direction){int stream=AudioManager.STREAM_MUSIC;int current=audio.getStreamVolume(stream),max=audio.getStreamMaxVolume(stream);int step=Math.max(1,max/12);int next=Math.max(0,Math.min(max,current+(direction>0?step:-step)));audio.setStreamVolume(stream,next,0);publish();}
    private void releasePlayer(){ready=false;if(player!=null){player.release();player=null;}}
    private void fail(String message){releasePlayer();wantsPlay=false;resumeOnGain=false;ducked=false;state="error";title=message;audio.abandonAudioFocusRequest(focus);publish();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void finish(){releasePlayer();wantsPlay=false;resumeOnGain=false;ducked=false;state="idle";title="";audio.abandonAudioFocusRequest(focus);publish();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private PendingIntent command(String action,int id){Intent i=new Intent(this,MusicService.class).setAction(action);return PendingIntent.getService(this,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,ContactAwareMainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);boolean playing="playing".equals(state)||"loading".equals(state);return new Notification.Builder(this,"music").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Terrain Commander").setContentText(title.isEmpty()?"مشغّل ملفات الهاتف":title).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"السابق",command("previous",1)).build()).addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"إيقاف مؤقت":"استكمال",command(playing?"pause":"resume",2)).build()).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"التالي",command("next",3)).build()).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"إنهاء",command("stop",4)).build()).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();}
    private void publish(){JSONObject data=new JSONObject();try{data.put("state",state);data.put("title",title);data.put("index",index);data.put("ducked",ducked);}catch(JSONException ignored){}snapshot=data.toString();int st="playing".equals(state)?PlaybackState.STATE_PLAYING:"loading".equals(state)?PlaybackState.STATE_BUFFERING:"error".equals(state)?PlaybackState.STATE_ERROR:"idle".equals(state)?PlaybackState.STATE_STOPPED:PlaybackState.STATE_PAUSED;long pos=ready&&player!=null?player.getCurrentPosition():0;session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO).setState(st,pos,st==PlaybackState.STATE_PLAYING?1:0).build());session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).build());((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(31,notification());}
    @Override public void onDestroy(){releasePlayer();running=false;audio.abandonAudioFocusRequest(focus);unregisterReceiver(noisy);session.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
