package com.linksave.app;

import android.app.DownloadManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.webkit.MimeTypeMap;
import android.widget.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends android.app.Activity {
    private EditText urlInput; private TextView statusText;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);urlInput=findViewById(R.id.urlInput);statusText=findViewById(R.id.statusText);findViewById(R.id.pasteButton).setOnClickListener(v->paste());findViewById(R.id.checkButton).setOnClickListener(v->inspect());findViewById(R.id.downloadButton).setOnClickListener(v->download());handle(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);}
    private void handle(Intent i){if(Intent.ACTION_SEND.equals(i.getAction())&&"text/plain".equals(i.getType())){String t=i.getStringExtra(Intent.EXTRA_TEXT);if(t!=null){urlInput.setText(firstUrl(t));inspect();}}}
    private void paste(){ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c!=null&&c.hasPrimaryClip()){CharSequence t=c.getPrimaryClip().getItemAt(0).coerceToText(this);urlInput.setText(firstUrl(t.toString()));inspect();}else Toast.makeText(this,"الحافظة فارغة",Toast.LENGTH_SHORT).show();}
    private String firstUrl(String t){for(String p:t.trim().split("\\s+"))if(p.startsWith("http://")||p.startsWith("https://"))return p;return t.trim();}
    private boolean valid(String t){try{URI u=URI.create(t);return ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null;}catch(Exception e){return false;}}
    private void inspect(){String u=urlInput.getText().toString().trim();statusText.setText(valid(u)?"الرابط صالح وجاهز للتنزيل":"الرابط غير صحيح");}
    private void download(){String u=urlInput.getText().toString().trim();if(!valid(u)){statusText.setText("أدخل رابطًا صحيحًا");return;}try{String name=guess(u);String ext="";int d=name.lastIndexOf('.');if(d>=0)ext=name.substring(d+1);String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));DownloadManager.Request r=new DownloadManager.Request(Uri.parse(u));r.setTitle(name);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);if(mime!=null)r.setMimeType(mime);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"LinkSave/"+name);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);statusText.setText("بدأ التنزيل إلى Downloads/LinkSave");}catch(Exception e){statusText.setText("تعذر التنزيل: "+e.getMessage());}}
    private String guess(String u){try{String s=Uri.parse(u).getLastPathSegment();if(s!=null&&s.contains("."))return s.replaceAll("[\\\\/:*?\"<>|]","_");}catch(Exception ignored){}return "linksave_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".bin";}
}
