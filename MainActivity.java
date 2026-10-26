package br.tvlauncher.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String SELF = "br.tvlauncher.home";
    private static final String TV_SETTINGS = "com.android.tv.settings";

    private HomeView view;
    private String lastKey = "";

    private final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (view != null) view.refreshTime();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new HomeView(this);
        setContentView(view);
        view.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeRx, f);
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(timeRx);
        } catch (Exception ignored) {
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.refreshTime();
        view.setWifi(readWifi());
        view.setInputs(readInputs());
        loadApps();
        view.requestFocus();
    }

    @Override
    protected void onPause() {
        String pkg = view.currentPkg();
        if (pkg != null) {
            getSharedPreferences("home", MODE_PRIVATE).edit().putString("last_pkg", pkg).apply();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        view.goHome();
    }

    @Override
    public void onBackPressed() {
        // Home nao fecha com Voltar
    }

    // ---------------------------------------------------------------- Wi-Fi

    private String readWifi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            String s = wi == null ? null : wi.getSSID();
            if (s == null || s.isEmpty() || s.contains("unknown ssid")) return "Wi-Fi";
            if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
            return s.isEmpty() ? "Wi-Fi" : s;
        } catch (Throwable t) {
            return "Wi-Fi";
        }
    }

    // -------------------------------------------------------------- entradas

    private List<HomeView.Input> readInputs() {
        List<HomeView.Input> out = new ArrayList<>();
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (m == null) return out;
            boolean tuner = false;
            List<HomeView.Input> pass = new ArrayList<>();
            for (TvInputInfo i : m.getTvInputList()) {
                if (i.isHidden(this)) continue;
                if (i.isPassthroughInput()) {
                    CharSequence l = i.loadCustomLabel(this);
                    if (l == null || l.length() == 0) l = i.loadLabel(this);
                    pass.add(new HomeView.Input(String.valueOf(l),
                            new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId()))));
                } else {
                    tuner = true;
                }
            }
            if (tuner) {
                PackageManager pm = getPackageManager();
                Intent live = pm.getLeanbackLaunchIntentForPackage("com.tcl.tv");
                if (live == null) live = pm.getLaunchIntentForPackage("com.tcl.tv");
                if (live == null) live = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
                out.add(new HomeView.Input("TV ao vivo", live));
            }
            out.addAll(pass);
        } catch (Throwable ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final int cols = getResources().getInteger(R.integer.columns);
        final String[] dockPkgs = getResources().getStringArray(R.array.dock_apps);

        Intent a = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent b = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final Map<String, ResolveInfo> map = new LinkedHashMap<>();
        final Map<String, Boolean> leanback = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(a, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(SELF) || p.equals(TV_SETTINGS) || map.containsKey(p)) continue;
            map.put(p, ri);
            leanback.put(p, true);
        }
        for (ResolveInfo ri : pm.queryIntentActivities(b, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(SELF) || p.equals(TV_SETTINGS) || map.containsKey(p)) continue;
            map.put(p, ri);
            leanback.put(p, false);
        }

        StringBuilder key = new StringBuilder().append(cols).append('|');
        for (String p : dockPkgs) key.append(p).append(',');
        key.append('|');
        for (Map.Entry<String, ResolveInfo> e : map.entrySet()) {
            key.append(e.getKey()).append('/').append(e.getValue().activityInfo.name).append(';');
        }
        if (key.toString().equals(lastKey)) return;
        lastKey = key.toString();

        view.setCols(cols);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final float unit = HomeView.scaleFor(dm.widthPixels, dm.heightPixels);
        final int W = view.tilePxW(unit), H = view.tilePxH(unit);
        final String lastPkg = getSharedPreferences("home", MODE_PRIVATE).getString("last_pkg", null);

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<HomeView.Item> dock = new ArrayList<>();
                List<HomeView.Item> grid = new ArrayList<>();
                List<String> used = new ArrayList<>();
                for (String p : dockPkgs) {
                    if (dock.size() >= cols) break;
                    ResolveInfo ri = map.get(p);
                    if (ri == null) continue;
                    dock.add(makeItem(pm, ri, Boolean.TRUE.equals(leanback.get(p)), W, H));
                    used.add(p);
                }
                for (Map.Entry<String, ResolveInfo> e : map.entrySet()) {
                    if (used.contains(e.getKey())) continue;
                    grid.add(makeItem(pm, e.getValue(), Boolean.TRUE.equals(leanback.get(e.getKey())), W, H));
                }
                Collections.sort(grid, new Comparator<HomeView.Item>() {
                    @Override
                    public int compare(HomeView.Item x, HomeView.Item y) {
                        return String.CASE_INSENSITIVE_ORDER.compare(x.label, y.label);
                    }
                });
                final List<HomeView.Item> fd = dock, fg = grid;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.setData(fd, fg, lastPkg);
                    }
                });
            }
        }).start();
    }

    private HomeView.Item makeItem(PackageManager pm, ResolveInfo ri, boolean lean, int W, int H) {
        HomeView.Item it = new HomeView.Item();
        it.pkg = ri.activityInfo.packageName;
        CharSequence l = ri.loadLabel(pm);
        it.label = l == null ? it.pkg : l.toString();
        it.intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(lean ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setClassName(it.pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Drawable banner = null, icon = null;
        try {
            banner = ri.activityInfo.loadBanner(pm);
        } catch (Throwable ignored) {
        }
        try {
            icon = ri.loadIcon(pm);
        } catch (Throwable ignored) {
        }
        it.tile = makeTile(banner, icon, W, H);
        it.glow = sampleGlow(it.tile);
        return it;
    }

    private static Bitmap makeTile(Drawable banner, Drawable icon, int W, int H) {
        Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(src);
        if (banner != null) {
            int iw = banner.getIntrinsicWidth(), ih = banner.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                banner.setBounds(0, 0, W, H);
            } else {
                float s = Math.max(W / (float) iw, H / (float) ih);
                float dw = iw * s, dh = ih * s;
                int l = Math.round((W - dw) / 2f), t = Math.round((H - dh) / 2f);
                banner.setBounds(l, t, l + Math.round(dw), t + Math.round(dh));
            }
            banner.draw(c);
        } else {
            c.drawColor(0xFF2C2C2E);
            if (icon != null) {
                int s = Math.round(H * 0.62f);
                int l = (W - s) / 2, t = (H - s) / 2;
                icon.setBounds(l, t, l + s, t + s);
                icon.draw(c);
            }
        }
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = H * 0.16f;
        oc.drawRoundRect(new RectF(0, 0, W, H), r, r, p);
        src.recycle();
        return out;
    }

    /** Cor media do banner, com mais saturacao e brilho, para o brilho ambiente. */
    private static int sampleGlow(Bitmap tile) {
        try {
            Bitmap one = Bitmap.createScaledBitmap(tile, 1, 1, true);
            int px = one.getPixel(0, 0);
            float[] hsv = new float[3];
            Color.colorToHSV(px, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.25f);
            hsv[2] = Math.max(hsv[2], 0.7f);
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return 0xFF3A5FCD;
        }
    }
}
