package com.terrain.commander;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.provider.MediaStore;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import android.media.AudioAttributes;
import org.json.JSONObject;
import java.util.*;

public class MainActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int MIC_REQ=77;
    private WebView web;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean listening=false, ttsReady=false, destroyed=false, permissionPending=false;

    @Override public void onCreate(Bundle b){ super.onCreate(b);
        web=new WebView(this); setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false); s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new Bridge(),"AndroidVoice");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                if("file:///android_asset/index.html".equals(r.getUrl().toString())) return false;
                openExternal(r.getUrl().toString()); return true;
            }
        });
        tts=new TextToSpeech(this,this);
        if(SpeechRecognizer.isRecognitionAvailable(this)){ sr=SpeechRecognizer.createSpeechRecognizer(this); sr.setRecognitionListener(this); }
        web.loadUrl("file:///android_asset/index.html");
    }
    private void js(String code){ runOnUiThread(()->{if(!destroyed && web!=null)web.evaluateJavascript("if(window.NativeVoice){"+code+"}",null);}); }
    private String q(String x){ return JSONObject.quote(x==null?"":x); }
    private void error(String message,boolean fatal){ js("NativeVoice.onError("+q(message)+","+fatal+")"); }
    private void requestMic(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) js("NativeVoice.onReady()");
        else if(!permissionPending){permissionPending=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQ);}
    }
    private void begin(String lang){
        if(listening)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ requestMic(); return; }
        if(sr==null){ error("خدمة التعرف الصوتي غير متاحة على الهاتف",true); return; }
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,lang==null?"ar-IQ":lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try{ listening=true; sr.startListening(intent); }catch(Exception e){listening=false;error("تعذر بدء الميكروفون",true);}
    }
    private void stopMic(){listening=false;if(sr!=null)sr.cancel();}
    private void openExternal(String u){
        Uri uri=Uri.parse(u);String scheme=uri.getScheme();
        if(!"https".equals(scheme)&&!"tel".equals(scheme)&&!"geo".equals(scheme))return;
        try{startActivity(new Intent("tel".equals(scheme)?Intent.ACTION_DIAL:Intent.ACTION_VIEW,uri));}
        catch(ActivityNotFoundException e){js("NativeVoice.onNotice('لا يوجد تطبيق مناسب لتنفيذ الأمر')");}
    }
    private Intent musicIntent(String query){
        Intent intent=new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
        intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS,"vnd.android.cursor.item/audio");
        intent.putExtra(SearchManager.QUERY,query);
        return intent;
    }
    private void playMusic(String query,String provider){
        if(query==null||query.trim().isEmpty())return;
        stopMic();
        Intent intent=musicIntent(query.trim());
        if("spotify".equals(provider))intent.setPackage("com.spotify.music");
        else if("youtube_music".equals(provider)||"youtube".equals(provider))intent.setPackage("com.google.android.apps.youtube.music");
        try{startActivity(intent);}
        catch(ActivityNotFoundException|SecurityException e){
            new AlertDialog.Builder(this).setTitle("التشغيل المباشر غير متاح")
                .setMessage("المشغّل المختار غير مثبت أو لا يستقبل طلب تشغيل الأغنية. اختر مشغّلاً آخر يدعم التشغيل بالبحث، أو افتح البحث اليدوي.")
                .setPositiveButton("مشغّل آخر",(dialog,which)->{
                    Intent other=musicIntent(query);
                    if(other.resolveActivity(getPackageManager())!=null)startActivity(Intent.createChooser(other,"تشغيل الأغنية بواسطة"));
                    else js("NativeVoice.onNotice('لا يوجد مشغّل يدعم التشغيل بالبحث على الهاتف')");
                })
                .setNeutralButton("بحث يدوي",(dialog,which)->openExternal("spotify".equals(provider)?"https://open.spotify.com/search/"+Uri.encode(query):"https://music.youtube.com/search?q="+Uri.encode(query)))
                .setNegativeButton("إلغاء",null).show();
        }
    }
    public class Bridge {
        @JavascriptInterface public void playMusic(String query,String provider){runOnUiThread(()->MainActivity.this.playMusic(query,provider));}
        @JavascriptInterface public void requestMic(){runOnUiThread(()->MainActivity.this.requestMic());}
        @JavascriptInterface public void startListening(String lang){runOnUiThread(()->begin(lang));}
        @JavascriptInterface public void stopListening(){runOnUiThread(()->stopMic());}
        @JavascriptInterface public void speak(String text,String lang){runOnUiThread(()->{
            stopMic();
            if(!ttsReady){js("NativeVoice.onSpeakDone();NativeVoice.onNotice('محرك النطق غير جاهز؛ الرد ظاهر على الشاشة')");return;}
            int result=tts.setLanguage(Locale.forLanguageTag(lang==null?"ar-IQ":lang));
            if(result<0){js("NativeVoice.onSpeakDone();NativeVoice.onNotice('ثبّت بيانات الصوت للغة المختارة من إعدادات الهاتف')");return;}
            tts.setSpeechRate(.98f);
            if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"terrain")==TextToSpeech.ERROR)js("NativeVoice.onSpeakDone()");
        });}
        @JavascriptInterface public void stopSpeaking(){runOnUiThread(()->{if(tts!=null)tts.stop();});}
        @JavascriptInterface public void openUrl(String url){runOnUiThread(()->openExternal(url));}
    }
    @Override public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            public void onStart(String id){} public void onDone(String id){js("NativeVoice.onSpeakDone()");} public void onError(String id){js("NativeVoice.onSpeakDone()");}
        });
    }}
    @Override public void onReadyForSpeech(Bundle p){if(listening)js("NativeVoice.onListening()");}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float r){}
    @Override public void onBufferReceived(byte[] b){}
    @Override public void onEndOfSpeech(){}
    @Override public void onError(int e){
        if(!listening)return;listening=false;
        if(e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){error("لم أسمع أمرًا واضحًا",false);return;}
        error("خطأ صوتي "+e+"؛ تحقق من الإنترنت وخدمة التعرف ثم ابدأ مجددًا",true);
    }
    @Override public void onResults(Bundle b){if(!listening)return;listening=false;ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);js("NativeVoice.onResult("+q(r!=null&&!r.isEmpty()?r.get(0):"")+ ");NativeVoice.onEnd()");}
    @Override public void onPartialResults(Bundle b){}
    @Override public void onEvent(int e,Bundle b){}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==MIC_REQ){permissionPending=false;if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)js("NativeVoice.onReady()");else error("صلاحية الميكروفون مرفوضة؛ فعّلها من إعدادات التطبيق",true);}}
    @Override protected void onPause(){js("NativeVoice.onPause()");stopMic();if(tts!=null)tts.stop();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;if(sr!=null)sr.destroy();if(tts!=null){tts.stop();tts.shutdown();}if(web!=null){web.removeJavascriptInterface("AndroidVoice");web.destroy();}super.onDestroy();}
}
