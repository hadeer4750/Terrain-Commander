package com.linksave.app;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends android.app.Activity {
    private EditText urlInput;
    private TextView statusText;
    private VideoView videoPreview;
    private FrameLayout previewCard;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String detectedMime = null;
    private String detectedExt = "";
    private String detectedFileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.urlInput);
        statusText = findViewById(R.id.statusText);
        videoPreview = findViewById(R.id.videoPreview);
        previewCard = findViewById(R.id.previewCard);
        Button pasteButton = findViewById(R.id.pasteButton);
        Button checkButton = findViewById(R.id.checkButton);
        Button downloadButton = findViewById(R.id.downloadButton);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoPreview);
        videoPreview.setMediaController(controls);

        pasteButton.setOnClickListener(v -> pasteFromClipboard());
        checkButton.setOnClickListener(v -> inspectUrl());
        downloadButton.setOnClickListener(v -> downloadUrl());
        handleSharedText(getIntent());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
    }

    private void handleSharedText(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) {
                urlInput.setText(extractFirstUrl(text));
                inspectUrl();
            }
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (text != null) {
                    urlInput.setText(extractFirstUrl(text.toString()));
                    inspectUrl();
                    return;
                }
            }
        }
        Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show();
    }

    private String extractFirstUrl(String text) {
        for (String p : text.trim().split("\\s+")) {
            if (p.startsWith("https://") || p.startsWith("http://")) return p;
        }
        return text.trim();
    }

    private boolean validHttpUrl(String text) {
        try {
            URI uri = URI.create(text);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void inspectUrl() {
        final String url = urlInput.getText().toString().trim();
        if (!validHttpUrl(url)) {
            statusText.setText("الرابط غير صحيح. استخدم رابط يبدأ بـ http:// أو https://");
            previewCard.setVisibility(View.GONE);
            return;
        }

        statusText.setText("جارٍ تحليل الفيديو...\nأتحقق من النوع والحجم والدقة والامتداد الحقيقي.");
        previewCard.setVisibility(View.GONE);
        detectedMime = null;
        detectedExt = "";
        detectedFileName = "";

        executor.execute(() -> {
            String fileName = guessFileName(url);
            String ext = extensionOf(fileName).toLowerCase(Locale.ROOT);
            String mime = null;
            long length = -1;
            int width = -1;
            int height = -1;
            long durationMs = -1;
            String error = null;

            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "LinkSave/2.1 Android");
                conn.connect();
                mime = normalizeMime(conn.getContentType());
                length = conn.getContentLengthLong();
                String disposition = conn.getHeaderField("Content-Disposition");
                if (disposition != null) {
                    String parsed = fileNameFromDisposition(disposition);
                    if (parsed != null) {
                        fileName = parsed;
                        ext = extensionOf(fileName).toLowerCase(Locale.ROOT);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }

            String mimeExt = extensionFromMime(mime);
            if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/") && !mimeExt.isEmpty()) {
                ext = mimeExt;
                fileName = replaceExtension(fileName, ext);
            }

            boolean metadataSaysVideo = false;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "LinkSave/2.1 Android");
                mmr.setDataSource(url, headers);
                String w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (w != null) width = Integer.parseInt(w);
                if (h != null) height = Integer.parseInt(h);
                if (d != null) durationMs = Long.parseLong(d);
                metadataSaysVideo = width > 0 && height > 0;
            } catch (Exception ignored) {
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }

            boolean looksVideo = isVideo(ext) || (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) || metadataSaysVideo;
            if (looksVideo) {
                if (!isVideo(ext)) {
                    ext = !mimeExt.isEmpty() ? mimeExt : "mp4";
                    fileName = replaceExtension(fileName, ext);
                }
                if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
                    String guessedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    if (guessedMime != null) mime = guessedMime;
                }
            } else {
                error = "الرابط لا يبدو أنه ملف فيديو مباشر.";
            }

            final String fName = sanitizeFileName(fileName);
            final String fExt = ext;
            final String fMime = mime;
            final long fLength = length;
            final int fWidth = width;
            final int fHeight = height;
            final long fDuration = durationMs;
            final String fError = error;

            runOnUiThread(() -> {
                detectedMime = fMime;
                detectedExt = fExt;
                detectedFileName = fName;
                StringBuilder info = new StringBuilder();
                if (fError != null) info.append(fError).append("\n");
                info.append("الاسم: ").append(fName).append("\n");
                info.append("الامتداد الحقيقي: ").append(fExt.isEmpty() ? "غير معروف" : fExt.toUpperCase(Locale.ROOT)).append("\n");
                info.append("النوع: ").append(fMime == null ? "غير معروف" : fMime).append("\n");
                if (fWidth > 0 && fHeight > 0) {
                    info.append("الجودة: ").append(qualityLabel(fWidth, fHeight)).append(" (").append(fWidth).append("×").append(fHeight).append(")\n");
                } else {
                    info.append("الجودة: لم يتمكن المصدر من إظهار الدقة\n");
                }
                if (fLength > 0) info.append("الحجم: ").append(formatBytes(fLength)).append("\n");
                if (fDuration > 0) info.append("المدة: ").append(formatDuration(fDuration));
                statusText.setText(info.toString().trim());

                if (fError == null) {
                    try {
                        previewCard.setVisibility(View.VISIBLE);
                        videoPreview.setVideoURI(Uri.parse(url));
                        videoPreview.setOnPreparedListener(mp -> {
                            mp.setLooping(false);
                            videoPreview.seekTo(1);
                        });
                        videoPreview.setOnErrorListener((mp, what, extra) -> {
                            Toast.makeText(this, "المعاينة غير متاحة لهذا الرابط، لكن قد يبقى التنزيل ممكنًا", Toast.LENGTH_LONG).show();
                            return true;
                        });
                    } catch (Exception e) {
                        previewCard.setVisibility(View.GONE);
                    }
                } else {
                    previewCard.setVisibility(View.GONE);
                }
            });
        });
    }

    private void downloadUrl() {
        String url = urlInput.getText().toString().trim();
        if (!validHttpUrl(url)) {
            statusText.setText("أدخل رابطًا صحيحًا أولًا");
            return;
        }
        try {
            String fileName = detectedFileName;
            String ext = detectedExt;

            if (fileName == null || fileName.isEmpty()) fileName = guessFileName(url);
            if (ext == null || ext.isEmpty()) ext = extensionOf(fileName).toLowerCase(Locale.ROOT);

            if (!isVideo(ext) && detectedMime != null && detectedMime.toLowerCase(Locale.ROOT).startsWith("video/")) {
                String fromMime = extensionFromMime(detectedMime);
                ext = fromMime.isEmpty() ? "mp4" : fromMime;
            }
            if (isVideo(ext)) fileName = replaceExtension(fileName, ext);
            fileName = sanitizeFileName(fileName);

            String mime = detectedMime;
            if (mime == null && !ext.isEmpty()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("جارٍ تنزيل الفيديو بواسطة LinkSave");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.addRequestHeader("User-Agent", "LinkSave/2.1 Android");
            if (mime != null) request.setMimeType(mime);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkSave/" + fileName);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            manager.enqueue(request);
            statusText.append("\n\nسيُحفظ بصيغة: " + ext.toUpperCase(Locale.ROOT) + "\nبدأ التنزيل إلى Downloads/LinkSave");
            Toast.makeText(this, "بدأ تنزيل الفيديو بصيغته الصحيحة", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            statusText.setText("تعذر بدء التنزيل: " + e.getMessage());
        }
    }

    private String normalizeMime(String mime) {
        if (mime == null) return null;
        int semi = mime.indexOf(';');
        if (semi >= 0) mime = mime.substring(0, semi);
        mime = mime.trim().toLowerCase(Locale.ROOT);
        return mime.isEmpty() ? null : mime;
    }

    private String guessFileName(String url) {
        try {
            Uri uri = Uri.parse(url);
            String segment = uri.getLastPathSegment();
            if (segment != null && !segment.trim().isEmpty()) {
                segment = URLDecoder.decode(segment, StandardCharsets.UTF_8.name());
                segment = sanitizeFileName(segment);
                if (segment.length() > 100) segment = segment.substring(segment.length() - 100);
                if (!segment.isEmpty()) return segment;
            }
        } catch (Exception ignored) {}
        return "linksave_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String fileNameFromDisposition(String cd) {
        try {
            int p = cd.toLowerCase(Locale.ROOT).indexOf("filename=");
            if (p < 0) return null;
            String value = cd.substring(p + 9).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) value = value.substring(0, semi);
            value = value.replace("\"", "").trim();
            return value.isEmpty() ? null : sanitizeFileName(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String replaceExtension(String fileName, String ext) {
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        int dot = base.lastIndexOf('.');
        if (dot > slash) base = base.substring(0, dot);
        if (base.isEmpty()) base = "linksave_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return base + "." + ext.toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot + 1);
        int q = ext.indexOf('?');
        if (q >= 0) ext = ext.substring(0, q);
        return ext.length() <= 6 ? ext : "";
    }

    private String extensionFromMime(String mime) {
        if (mime == null) return "";
        if ("video/mp4".equals(mime)) return "mp4";
        if ("video/webm".equals(mime)) return "webm";
        if ("video/quicktime".equals(mime)) return "mov";
        if ("video/3gpp".equals(mime)) return "3gp";
        if ("video/x-matroska".equals(mime)) return "mkv";
        if ("video/mp2t".equals(mime)) return "ts";
        String e = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return e == null ? "" : e;
    }

    private boolean isVideo(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return e.equals("mp4") || e.equals("webm") || e.equals("mkv") || e.equals("mov") || e.equals("m4v") || e.equals("3gp") || e.equals("ts");
    }

    private String qualityLabel(int w, int h) {
        int shortSide = Math.min(w, h);
        int longSide = Math.max(w, h);
        if (longSide >= 7000 || shortSide >= 4000) return "8K";
        if (longSide >= 3800 || shortSide >= 2100) return "4K";
        if (longSide >= 2500 || shortSide >= 1400) return "1440p";
        if (longSide >= 1900 || shortSide >= 1000) return "1080p";
        if (longSide >= 1200 || shortSide >= 700) return "720p";
        if (longSide >= 800 || shortSide >= 450) return "480p";
        if (longSide >= 600 || shortSide >= 340) return "360p";
        return shortSide + "p";
    }

    private String formatBytes(long bytes) {
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return new DecimalFormat(v >= 100 ? "0" : v >= 10 ? "0.0" : "0.00").format(v) + " " + units[i];
    }

    private String formatDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
