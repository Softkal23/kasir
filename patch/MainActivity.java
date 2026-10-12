package com.warkop48.admin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class MainActivity extends BridgeActivity {
    private static final String HOST = "warkop48.freedev.app";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Object btLock = new Object();
    private BluetoothSocket btSocket;
    private String btAddr = "";

    // Disuntikkan ke halaman situs: sembunyikan tombol Google (diblokir Google di WebView),
    // ganti window.print() dan unduhan blob: dengan versi Android.
    private static final String PAGE_JS =
            "(function(){\n" +
            "if(window.__wk)return;window.__wk=1;\n" +
            "var st=document.createElement('style');st.textContent='.g-btn{display:none!important}';document.head.appendChild(st);\n" +
            "var blobs={},cu=URL.createObjectURL;\n" +
            "URL.createObjectURL=function(b){var u=cu.call(URL,b);blobs[u]=b;return u;};\n" +
            "window.__wkSave=function(url,name){\n" +
            "var go=function(b){var fr=new FileReader();fr.onload=function(){var s=String(fr.result);delete blobs[url];AndroidApp.saveFile(name,b.type||'application/octet-stream',s.substring(s.indexOf(',')+1));};fr.readAsDataURL(b);};\n" +
            "if(blobs[url])go(blobs[url]);else fetch(url).then(function(r){return r.blob();}).then(go).catch(function(){AndroidApp.toast('Gagal menyimpan file');});\n" +
            "};\n" +
            "var oc=HTMLAnchorElement.prototype.click;\n" +
            "HTMLAnchorElement.prototype.click=function(){\n" +
            "if(this.download&&String(this.href).indexOf('blob:')===0){window.__wkSave(this.href,this.download);return;}\n" +
            "return oc.apply(this,arguments);\n" +
            "};\n" +
            "window.print=function(){AndroidApp.print();};\n" +
            "})();\n";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layar tidak mati selama aplikasi terbuka (layar kasir).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Android 6-9: simpan ke folder Download butuh izin penyimpanan.
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 48);
        }

        final Bridge bridge = getBridge();
        final WebView web = bridge.getWebView();
        WebSettings settings = web.getSettings();
        // Bunyi pesanan baru boleh berbunyi tanpa menunggu sentuhan.
        settings.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new AppBridge(), "AndroidApp");

        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (openRawbt(request.getUrl().toString())) return true;
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (openRawbt(url)) return true;
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains(HOST)) view.evaluateJavascript(PAGE_JS, null);
            }
        });

        // Unduhan https (CSV, PDF, cadangan database) lewat DownloadManager; blob: lewat JS.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (url.startsWith("blob:")) {
                web.evaluateJavascript("window.__wkSave&&window.__wkSave(" + JSONObject.quote(url) + "," + JSONObject.quote(name) + ")", null);
                return;
            }
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                req.setTitle(name);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(req);
                toast("Mengunduh " + name + " ke folder Download");
            } catch (Exception e) {
                toast("Gagal mengunduh: " + e.getMessage());
            }
        });
    }

    // ---------- Bluetooth printer (SPP / Bluetooth biasa) ----------
    private SharedPreferences prefs() {
        return getSharedPreferences("warkop48", Context.MODE_PRIVATE);
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private BluetoothAdapter btAdapter() {
        BluetoothManager m = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    private void closeBt() {
        try { if (btSocket != null) btSocket.close(); } catch (Exception ignore) { /* sudah tertutup */ }
        btSocket = null;
    }

    private void connectBt(String addr) throws Exception {
        BluetoothAdapter ad = btAdapter();
        if (ad == null || !ad.isEnabled()) throw new Exception("Bluetooth HP mati. Nyalakan Bluetooth lalu cetak lagi.");
        BluetoothDevice d = ad.getRemoteDevice(addr);
        BluetoothSocket s = d.createRfcommSocketToServiceRecord(SPP);
        try {
            s.connect();
        } catch (Exception first) {
            try { s.close(); } catch (Exception ignore) { /* abaikan */ }
            // Cadangan untuk printer murah yang tidak mengumumkan layanan SPP.
            s = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
            s.connect();
        }
        btSocket = s;
        btAddr = addr;
    }

    // Kirim data ke printer. Satu koneksi dipakai ulang; bila putus, sambung ulang sekali lalu kirim lagi.
    private void sendBt(byte[] data) throws Exception {
        synchronized (btLock) {
            String addr = prefs().getString("bt_addr", "");
            if (addr.isEmpty()) throw new Exception("Printer belum dipilih.");
            Exception last = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    if (btSocket == null || !addr.equals(btAddr)) { closeBt(); connectBt(addr); }
                    OutputStream os = btSocket.getOutputStream();
                    int off = 0;
                    while (off < data.length) {
                        int n = Math.min(512, data.length - off);
                        os.write(data, off, n);
                        off += n;
                        Thread.sleep(25);
                    }
                    os.flush();
                    Thread.sleep(300); // beri waktu printer menyelesaikan cetakan
                    return;
                } catch (Exception e) {
                    last = e;
                    closeBt();
                }
            }
            String m = last == null ? "" : String.valueOf(last.getMessage());
            if (m.contains("Bluetooth HP mati")) throw last;
            throw new Exception("Tidak bisa tersambung ke printer. Pastikan printer menyala, dekat HP, sudah dipasangkan di Bluetooth HP, dan tidak sedang dipakai aplikasi lain (mis. RawBT).");
        }
    }

    private void jsPrintDone(String id, boolean ok, String msg) {
        runOnUiThread(() -> getBridge().getWebView().evaluateJavascript(
                "window.__wkBtDone&&window.__wkBtDone(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(msg == null ? "" : msg) + ")", null));
    }

    @Override
    public void onDestroy() {
        synchronized (btLock) { closeBt(); }
        super.onDestroy();
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    // Cetak struk lewat RawBT: bila aplikasinya belum terpasang, beri tahu dan buka Play Store.
    private boolean openRawbt(String url) {
        if (url == null || !url.startsWith("rawbt:")) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            toast("Aplikasi RawBT belum terpasang. Pasang RawBT dari Play Store, lalu coba cetak lagi.");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=ru.a402d.rawbtprinter")));
            } catch (Exception ignore) { /* Play Store tidak ada */ }
        } catch (Exception e) {
            toast("Gagal membuka RawBT: " + e.getMessage());
        }
        return true;
    }

    // Jembatan yang dipanggil dari JavaScript situs (hanya disuntik untuk warkop48.freedev.app).
    private class AppBridge {
        @JavascriptInterface
        public void toast(String msg) {
            MainActivity.this.toast(msg);
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    String job = "Warkop48";
                    pm.print(job, getBridge().getWebView().createPrintDocumentAdapter(job), new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    MainActivity.this.toast("Gagal membuka cetak: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String mime, String base64) {
            new Thread(() -> {
                String safe = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safe.isEmpty()) safe = "unduhan";
                try {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                        v.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (uri == null) throw new Exception("file tidak bisa dibuat");
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(data); }
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                        try (OutputStream os = new FileOutputStream(new File(dir, safe))) { os.write(data); }
                    }
                    MainActivity.this.toast("Tersimpan di folder Download: " + safe);
                } catch (Exception e) {
                    MainActivity.this.toast("Gagal menyimpan: " + e.getMessage());
                }
            }).start();
        }

        // ----- Bluetooth printer -----
        @JavascriptInterface
        public boolean btAsk() {
            if (hasBtPermission()) return true;
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 49));
            return false;
        }

        @JavascriptInterface
        public String btList() {
            JSONArray arr = new JSONArray();
            try {
                BluetoothAdapter ad = btAdapter();
                if (ad != null && hasBtPermission()) {
                    Set<BluetoothDevice> bonded = ad.getBondedDevices();
                    if (bonded != null) for (BluetoothDevice d : bonded) {
                        JSONObject o = new JSONObject();
                        o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                        o.put("address", d.getAddress());
                        arr.put(o);
                    }
                }
            } catch (Exception ignore) { /* daftar kosong */ }
            return arr.toString();
        }

        @JavascriptInterface
        public String btStatus() {
            JSONObject o = new JSONObject();
            try {
                BluetoothAdapter ad = btAdapter();
                String addr = prefs().getString("bt_addr", "");
                String name = prefs().getString("bt_name", "");
                o.put("permission", hasBtPermission());
                o.put("enabled", ad != null && ad.isEnabled());
                o.put("address", addr);
                o.put("name", name.isEmpty() ? addr : name);
            } catch (Exception ignore) { /* kosong */ }
            return o.toString();
        }

        @JavascriptInterface
        public void btSelect(String address) {
            String name = address;
            try {
                BluetoothAdapter ad = btAdapter();
                if (ad != null && hasBtPermission()) {
                    String n = ad.getRemoteDevice(address).getName();
                    if (n != null) name = n;
                }
            } catch (Exception ignore) { /* pakai alamat sebagai nama */ }
            prefs().edit().putString("bt_addr", address).putString("bt_name", name).apply();
            synchronized (btLock) { closeBt(); }
        }

        @JavascriptInterface
        public void btSettings() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
                catch (Exception e) { MainActivity.this.toast("Buka Pengaturan > Bluetooth di HP."); }
            });
        }

        @JavascriptInterface
        public void btPrint(final String id, final String base64) {
            new Thread(() -> {
                try {
                    sendBt(Base64.decode(base64, Base64.DEFAULT));
                    jsPrintDone(id, true, "");
                } catch (Exception e) {
                    jsPrintDone(id, false, e.getMessage());
                }
            }).start();
        }
    }
}
