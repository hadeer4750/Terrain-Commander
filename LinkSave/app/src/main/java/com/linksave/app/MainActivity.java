package com.linksave.app;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends android.app.Activity {
    private EditText urlInput, providerInput;
    private TextView statusText, summaryText;
    private VideoView videoPreview;
    private ImageView imagePreview;
    private FrameLayout previewCard;
    private Spinner qualitySpinner, modeSpinner, audioFormatSpinner, bitrateSpinner;
    private CheckBox wifiOnly, allowDuplicates, downloadAll;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private SharedPreferences prefs;
    private static final String PREFS = "linksave_v3";
    private static final String DEFAULT_PROVIDER = "https://api.cobalt.liubquanti.click/";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bindViews();
        setupSpinners();
        setupActions();
        providerInput.setText(prefs.getString("provider", DEFAULT_PROVIDER));
        handleSharedText(getIntent());
    }

    private void bindViews() {
        urlInput=findViewById(R.id.urlInput); providerInput=findViewById(R.id.providerInput);
        statusText=findViewById(R.id.statusText); summaryText=findViewById(R.id.summaryText);
        videoPreview=findViewById(R.id.videoPreview); imagePreview=findViewById(R.id.imagePreview); previewCard=findViewById(R.id.previewCard);
        qualitySpinner=findViewById(R.id.qualitySpinner); modeSpinner=findViewById(R.id.modeSpinner);
        audioFormatSpinner=findViewById(R.id.audioFormatSpinner); bitrateSpinner=findViewById(R.id.bitrateSpinner);
        wifiOnly=findViewById(R.id.wifiOnly); allowDuplicates=findViewById(R.id.allowDuplicates); downloadAll=findViewById(R.id.downloadAll);
        MediaController controls=new MediaController(this); controls.setAnchorView(videoPreview); videoPreview.setMediaController(controls);
    }

    private void setupSpinners() {
        setSpinner(qualitySpinner, new String[]{"أفضل نسخة","2160p","1440p","1080p","720p","480p","360p"});
        setSpinner(modeSpinner, new String[]{"فيديو + صوت","صوت فقط","فيديو بدون صوت"});
        setSpinner(audioFormatSpinner, new String[]{"MP3","BEST","OPUS","OGG","WAV"});
        setSpinner(bitrateSpinner, new String[]{"320 kbps","256 kbps","128 kbps","96 kbps","64 kbps"});
        qualitySpinner.setSelection(3); bitrateSpinner.setSelection(2);
    }
    private void setSpinner(Spinner s,String[] values){ ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a); }

    private void setupActions() {
        findViewById(R.id.pasteButton).setOnClickListener(v->pasteFromClipboard());
        findViewById(R.id.checkButton).setOnClickListener(v->analyze());
        findViewById(R.id.downloadButton).setOnClickListener(v->downloadResolved());
        findViewById(R.id.saveProviderButton).setOnClickListener(v->saveProvider());
        findViewById(R.id.historyButton).setOnClickListener(v->showHistory());
        findViewById(R.id.openDownloadsButton).setOnClickListener(v->openDownloads());
        findViewById(R.id.clearButton).setOnClickListener(v->{urlInput.setText("");mediaItems.clear();previewCard.setVisibility(View.GONE);statusText.setText("جاهز");summaryText.setText("");});
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleSharedText(intent);}
    @Override protected void onResume(){super.onResume();if(urlInput.getText().toString().trim().isEmpty())detectClipboardQuietly();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}

    private void handleSharedText(Intent intent){
        if(Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())){
            String text=intent.getStringExtra(Intent.EXTRA_TEXT); if(text!=null){List<String> u=extractUrls(text); if(!u.isEmpty()){urlInput.setText(join(u));analyze();}}
        }
    }
    private void detectClipboardQuietly(){
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(cb==null||!cb.hasPrimaryClip())return; ClipData c=cb.getPrimaryClip(); if(c==null||c.getItemCount()==0)return;
        CharSequence t=c.getItemAt(0).coerceToText(this); if(t==null)return; List<String> u=extractUrls(t.toString()); if(!u.isEmpty()){urlInput.setHint("يوجد رابط في الحافظة — اضغط لصق");}
    }
    private void pasteFromClipboard(){
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(cb!=null&&cb.hasPrimaryClip()){ClipData c=cb.getPrimaryClip();if(c!=null&&c.getItemCount()>0){CharSequence t=c.getItemAt(0).coerceToText(this);if(t!=null){List<String>u=extractUrls(t.toString());urlInput.setText(u.isEmpty()?t.toString():join(u));if(!u.isEmpty())analyze();return;}}}Toast.makeText(this,"الحافظة فارغة",Toast.LENGTH_SHORT).show();
    }
    private List<String> extractUrls(String text){List<String>r=new ArrayList<>();Matcher m=URL_PATTERN.matcher(text==null?"":text);while(m.find()){String u=m.group();while(u.endsWith(")")||u.endsWith(",")||u.endsWith("."))u=u.substring(0,u.length()-1);r.add(u);}return r;}
    private String join(List<String> l){StringBuilder b=new StringBuilder();for(String s:l){if(b.length()>0)b.append('\n');b.append(s);}return b.toString();}

    private void saveProvider(){String p=normalizeProvider(providerInput.getText().toString());if(!validHttpUrl(p)){Toast.makeText(this,"عنوان API غير صحيح",Toast.LENGTH_SHORT).show();return;}prefs.edit().putString("provider",p).apply();providerInput.setText(p);Toast.makeText(this,"تم حفظ مزود التحليل",Toast.LENGTH_SHORT).show();}

    private void analyze(){
        List<String> urls=extractUrls(urlInput.getText().toString()); if(urls.isEmpty()){statusText.setText("أدخل رابطًا صحيحًا يبدأ بـ http:// أو https://");return;}
        mediaItems.clear(); previewCard.setVisibility(View.GONE); summaryText.setText(""); statusText.setText("جارٍ التحليل الذكي…");
        executor.execute(()->{
            StringBuilder report=new StringBuilder(); int index=0;
            for(String source:urls){index++;try{List<MediaItem> got=resolveSource(source);synchronized(mediaItems){mediaItems.addAll(got);}report.append("# ").append(index).append(" • ").append(hostOf(source)).append("\n");for(MediaItem x:got)report.append("• ").append(x.label()).append("\n");}catch(Exception e){report.append("# ").append(index).append(" • فشل: ").append(shortError(e)).append("\n");}}
            runOnUiThread(()->{statusText.setText(report.toString().trim());updateSummary();showPreview();});
        });
    }

    private List<MediaItem> resolveSource(String source) throws Exception {
        if(!validHttpUrl(source)) throw new Exception("رابط غير صالح");
        MediaItem direct=probeDirect(source,null);
        if(direct!=null && direct.isMedia()) {List<MediaItem> l=new ArrayList<>();l.add(direct);return l;}
        Exception last=null; for(String provider:providerCandidates()){try{return resolveViaCobalt(provider,source);}catch(Exception e){last=e;}}
        throw last==null?new Exception("لا يوجد مزود متاح"):last;
    }

    private List<String> providerCandidates(){
        List<String> p=new ArrayList<>();String saved=normalizeProvider(providerInput.getText().toString());if(validHttpUrl(saved))p.add(saved);
        String[] fallbacks={DEFAULT_PROVIDER,"https://api.cobalt.tools/"};for(String f:fallbacks)if(!p.contains(f))p.add(f);return p;
    }

    private List<MediaItem> resolveViaCobalt(String provider,String source) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(normalizeProvider(provider)).openConnection(); c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("User-Agent","LinkSave/3.0 Android");
        JSONObject body=new JSONObject();body.put("url",source);body.put("videoQuality",qualityValue());body.put("audioFormat",audioFormatValue());body.put("audioBitrate",bitrateValue());body.put("downloadMode",modeValue());body.put("filenameStyle","pretty");body.put("youtubeVideoCodec","h264");body.put("youtubeVideoContainer","mp4");body.put("disableMetadata",false);
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(bytes);}int code=c.getResponseCode();InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=readAll(is);c.disconnect();if(code<200||code>=300)throw new Exception("API "+code+" "+trim(text,100));
        JSONObject j=new JSONObject(text);String status=j.optString("status","");if("error".equals(status)){JSONObject e=j.optJSONObject("error");throw new Exception(e!=null?e.optString("code",e.toString()):"خطأ من المزود");}
        List<MediaItem> out=new ArrayList<>();
        if("picker".equals(status)){
            JSONArray a=j.optJSONArray("picker");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String u=o.optString("url","");if(!u.isEmpty()){MediaItem mi=probeDirect(u,"item_"+(i+1));if(mi==null)mi=new MediaItem(u,"item_"+(i+1),mimeFromPicker(o.optString("type","")),-1,source);out.add(mi);}}
            String au=j.optString("audio","");if(!au.isEmpty()){String fn=j.optString("audioFilename","audio.mp3");MediaItem mi=probeDirect(au,fn);if(mi!=null)out.add(mi);}
        } else if("tunnel".equals(status)||"redirect".equals(status)||"success".equals(status)||"stream".equals(status)){
            String u=j.optString("url","");if(u.isEmpty())throw new Exception("المزود لم يرجع ملفًا");String fn=j.optString("filename","");MediaItem mi=probeDirect(u,fn);if(mi==null)mi=new MediaItem(u,fn.isEmpty()?guessFileName(u):fn,null,-1,source);out.add(mi);
        } else throw new Exception("استجابة غير معروفة: "+status);
        if(out.isEmpty())throw new Exception("لم يتم العثور على وسائط");return out;
    }

    private MediaItem probeDirect(String url,String preferredName){
        HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(true);c.setRequestMethod("HEAD");c.setConnectTimeout(9000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent","LinkSave/3.0 Android");c.connect();String mime=normalizeMime(c.getContentType());long len=c.getContentLengthLong();String fn=(preferredName!=null&&!preferredName.trim().isEmpty())?preferredName:fileNameFromDisposition(c.getHeaderField("Content-Disposition"));if(fn==null||fn.isEmpty())fn=guessFileName(c.getURL().toString());String ext=extensionFromMime(mime);if(!ext.isEmpty()&&!extensionOf(fn).equalsIgnoreCase(ext))fn=replaceExtension(fn,ext);return new MediaItem(c.getURL().toString(),sanitizeFileName(fn),mime,len,url);}catch(Exception e){if(preferredName!=null)return new MediaItem(url,sanitizeFileName(preferredName),null,-1,url);return null;}finally{if(c!=null)c.disconnect();}}

    private void updateSummary(){int v=0,a=0,i=0,g=0;long size=0;for(MediaItem m:mediaItems){if(m.mime!=null){if(m.mime.startsWith("video/"))v++;else if(m.mime.startsWith("audio/"))a++;else if(m.mime.startsWith("image/"))i++;else g++;}else g++;if(m.size>0)size+=m.size;}summaryText.setText("تم العثور على "+mediaItems.size()+" ملف • فيديو "+v+" • صوت "+a+" • صور "+i+(size>0?" • الحجم المعروف "+formatBytes(size):""));}

    private void showPreview(){if(mediaItems.isEmpty()){previewCard.setVisibility(View.GONE);return;}MediaItem m=mediaItems.get(0);previewCard.setVisibility(View.VISIBLE);videoPreview.setVisibility(View.GONE);imagePreview.setVisibility(View.GONE);if(m.mime!=null&&m.mime.startsWith("video/")){videoPreview.setVisibility(View.VISIBLE);try{videoPreview.setVideoURI(Uri.parse(m.url));videoPreview.setOnPreparedListener(mp->videoPreview.seekTo(1));}catch(Exception ignored){}}else if(m.mime!=null&&m.mime.startsWith("image/")){imagePreview.setVisibility(View.VISIBLE);executor.execute(()->{try(InputStream in=new URL(m.url).openStream()){Bitmap b=BitmapFactory.decodeStream(in);runOnUiThread(()->imagePreview.setImageBitmap(b));}catch(Exception ignored){}});}else previewCard.setVisibility(View.GONE);}

    private void downloadResolved(){
        if(mediaItems.isEmpty()){analyze();Toast.makeText(this,"يتم تحليل الرابط أولًا",Toast.LENGTH_SHORT).show();return;}
        List<MediaItem> todo=new ArrayList<>();if(downloadAll.isChecked())todo.addAll(mediaItems);else todo.add(mediaItems.get(0));int queued=0;for(MediaItem m:todo)if(queueDownload(m))queued++;statusText.append("\n\nتمت إضافة "+queued+" تنزيل إلى مدير تنزيلات Android.");
    }

    private boolean queueDownload(MediaItem m){
        try{String key=Integer.toHexString((m.source+"|"+m.url).hashCode());Set<String>done=new HashSet<>(prefs.getStringSet("downloaded",new HashSet<>()));if(done.contains(key)&&!allowDuplicates.isChecked()){Toast.makeText(this,"تم تجاوز ملف مكرر: "+m.filename,Toast.LENGTH_SHORT).show();return false;}
            DownloadManager.Request r=new DownloadManager.Request(Uri.parse(m.url));r.setTitle(m.filename);r.setDescription("LinkSave V3");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setAllowedOverRoaming(false);r.setAllowedOverMetered(!wifiOnly.isChecked());if(wifiOnly.isChecked())r.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);r.addRequestHeader("User-Agent","LinkSave/3.0 Android");if(m.mime!=null)r.setMimeType(m.mime);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"LinkSave/"+m.filename);DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);if(dm==null)return false;long id=dm.enqueue(r);done.add(key);prefs.edit().putStringSet("downloaded",done).apply();appendHistory(m,id);return true;}catch(Exception e){Toast.makeText(this,"تعذر التنزيل: "+shortError(e),Toast.LENGTH_LONG).show();return false;}
    }

    private void appendHistory(MediaItem m,long id){String h=prefs.getString("history","");String line=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())+" | "+m.filename+" | #"+id;String next=line+(h.isEmpty()?"":"\n"+h);String[] lines=next.split("\\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(lines.length,40);i++){if(i>0)b.append('\n');b.append(lines[i]);}prefs.edit().putString("history",b.toString()).apply();}
    private void showHistory(){String h=prefs.getString("history","");statusText.setText(h.isEmpty()?"لا يوجد سجل تنزيلات بعد.":"سجل آخر التنزيلات:\n"+h);}
    private void openDownloads(){try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}}

    private String qualityValue(){String s=String.valueOf(qualitySpinner.getSelectedItem());if(s.startsWith("أفضل"))return "max";return s.replace("p","");}
    private String modeValue(){int p=modeSpinner.getSelectedItemPosition();return p==1?"audio":p==2?"mute":"auto";}
    private String audioFormatValue(){return String.valueOf(audioFormatSpinner.getSelectedItem()).toLowerCase(Locale.ROOT);}
    private String bitrateValue(){return String.valueOf(bitrateSpinner.getSelectedItem()).split(" ")[0];}
    private String normalizeProvider(String p){p=p==null?"":p.trim();if(!p.endsWith("/"))p+="/";return p;}
    private boolean validHttpUrl(String text){try{URI u=URI.create(text);return ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null;}catch(Exception e){return false;}}
    private String hostOf(String u){try{return Uri.parse(u).getHost();}catch(Exception e){return "رابط";}}
    private String readAll(InputStream in)throws Exception{if(in==null)return"";BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=b.readLine())!=null)s.append(l);return s.toString();}
    private String trim(String s,int n){if(s==null)return"";return s.length()>n?s.substring(0,n):s;}
    private String shortError(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():trim(s,120);}
    private String normalizeMime(String mime){if(mime==null)return null;int x=mime.indexOf(';');if(x>=0)mime=mime.substring(0,x);mime=mime.trim().toLowerCase(Locale.ROOT);return mime.isEmpty()?null:mime;}
    private String mimeFromPicker(String type){if("photo".equals(type))return"image/jpeg";if("video".equals(type))return"video/mp4";if("gif".equals(type))return"image/gif";return null;}
    private String guessFileName(String url){try{Uri u=Uri.parse(url);String s=u.getLastPathSegment();if(s!=null&&!s.isEmpty()){s=URLDecoder.decode(s,StandardCharsets.UTF_8.name());s=sanitizeFileName(s);if(!s.isEmpty())return s;}}catch(Exception ignored){}return"linksave_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());}
    private String fileNameFromDisposition(String cd){if(cd==null)return null;try{int p=cd.toLowerCase(Locale.ROOT).indexOf("filename=");if(p<0)return null;String v=cd.substring(p+9).trim();int s=v.indexOf(';');if(s>=0)v=v.substring(0,s);return sanitizeFileName(v.replace("\"","").trim());}catch(Exception e){return null;}}
    private String sanitizeFileName(String n){if(n==null||n.trim().isEmpty())return"linksave_"+System.currentTimeMillis();n=n.replaceAll("[\\\\/:*?\"<>|]","_").trim();return n.length()>140?n.substring(n.length()-140):n;}
    private String extensionOf(String n){int d=n.lastIndexOf('.');if(d<0||d==n.length()-1)return"";String e=n.substring(d+1).toLowerCase(Locale.ROOT);return e.length()<=6?e:"";}
    private String extensionFromMime(String m){if(m==null)return"";if(m.equals("video/mp4"))return"mp4";if(m.equals("video/webm"))return"webm";if(m.equals("audio/mpeg"))return"mp3";if(m.equals("audio/ogg"))return"ogg";if(m.equals("audio/opus"))return"opus";if(m.equals("image/jpeg"))return"jpg";if(m.equals("image/png"))return"png";String e=MimeTypeMap.getSingleton().getExtensionFromMimeType(m);return e==null?"":e;}
    private String replaceExtension(String n,String e){int d=n.lastIndexOf('.');if(d>0)n=n.substring(0,d);return n+"."+e;}
    private String formatBytes(long b){if(b<1024)return b+" B";double k=b/1024.0;if(k<1024)return new DecimalFormat("0.0").format(k)+" KB";double m=k/1024;if(m<1024)return new DecimalFormat("0.0").format(m)+" MB";return new DecimalFormat("0.00").format(m/1024)+" GB";}

    private static class MediaItem{
        final String url,filename,mime,source;final long size;MediaItem(String u,String f,String m,long s,String src){url=u;filename=(f==null||f.isEmpty())?"linksave_"+System.currentTimeMillis():f;mime=m;size=s;source=src==null?u:src;}
        boolean isMedia(){return mime!=null&&(mime.startsWith("video/")||mime.startsWith("audio/")||mime.startsWith("image/"));}
        String label(){String t=mime==null?"ملف":mime.startsWith("video/")?"فيديو":mime.startsWith("audio/")?"صوت":mime.startsWith("image/")?"صورة":"ملف";return t+" • "+filename+(size>0?" • "+human(size):"");}
        static String human(long b){double m=b/1048576.0;return m<1024?String.format(Locale.US,"%.1f MB",m):String.format(Locale.US,"%.2f GB",m/1024.0);}
    }
}
