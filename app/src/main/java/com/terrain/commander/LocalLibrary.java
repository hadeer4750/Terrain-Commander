package com.terrain.commander;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class LocalLibrary {
    public interface Listener {void changed(String message);}
    private final Activity activity;
    private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed=false,busy=false;
    public LocalLibrary(Activity a,Listener l){activity=a;listener=l;}
    public String json(){return activity.getSharedPreferences("music",0).getString("tracks","[]");}
    public JSONArray tracks(){try{return new JSONArray(json());}catch(JSONException e){return new JSONArray();}}
    public boolean busy(){return busy;}
    private void report(String s){activity.runOnUiThread(()->{if(!closed)listener.changed(s);});}
    public void scan(Uri tree){
        if(busy){report("انتظر اكتمال قراءة المجلد");return;}busy=true;report("جارٍ قراءة ملفات الموسيقى…");
        worker.execute(()->{
            try{
                List<JSONObject> found=new ArrayList<>();Set<String> seen=new HashSet<>();
                walk(tree,DocumentsContract.getTreeDocumentId(tree),found,seen,0);
                found.sort(Comparator.comparing(t->t.optString("title"),String.CASE_INSENSITIVE_ORDER));
                JSONArray array=new JSONArray();for(JSONObject item:found)array.put(item);
                if(!closed)activity.getSharedPreferences("music",0).edit().putString("tracks",array.toString()).putString("tree",tree.toString()).commit();
                report("تمت قراءة "+found.size()+" ملف"+(found.size()>=5000?"؛ الحد الحالي 5000 ملف":"")+(found.isEmpty()?". اختر مجلدًا يحتوي ملفات أغاني صوتية":""));
            }catch(Exception e){report("تعذر قراءة المجلد. اختره مجددًا وتأكد من صلاحية الوصول");}
            finally{busy=false;}
        });
    }
    private void walk(Uri tree,String id,List<JSONObject> out,Set<String> seen,int depth)throws Exception{
        if(closed||Thread.currentThread().isInterrupted()||depth>12||out.size()>=5000||!seen.add(id))return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor c=activity.getContentResolver().query(children,columns,null,null,null)){
            if(c==null)throw new IllegalStateException();
            while(c.moveToNext()&&out.size()<5000&&!closed){
                String child=c.getString(0),name=c.getString(1),mime=c.getString(2);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){walk(tree,child,out,seen,depth+1);continue;}
                if(name==null)continue;
                if((mime!=null&&mime.startsWith("audio/"))||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a|aac|wav|ogg|flac|opus|amr)$")){
                    out.add(new JSONObject().put("title",name).put("uri",DocumentsContract.buildDocumentUriUsingTree(tree,child).toString()).put("mime",mime!=null&&mime.startsWith("audio/")?mime:"audio/*"));
                }
            }
        }
    }
    public void refresh(){String value=activity.getSharedPreferences("music",0).getString("tree","");if(value.isEmpty())report("اختر مجلد الموسيقى أولاً");else scan(Uri.parse(value));}
    public void close(){closed=true;worker.shutdownNow();}
}
