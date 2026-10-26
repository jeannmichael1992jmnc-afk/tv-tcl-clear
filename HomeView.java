package br.tvlauncher.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Tela inicial inteira desenhada em canvas, num espaco de 960x540 "unidades"
 * escalado para a tela. Sem blur, sem video, sem rede em segundo plano.
 */
public class HomeView extends View {

    public static class Item {
        public String pkg, label;
        public Bitmap tile;
        public int glow = 0xFF3A5FCD;
        public Intent intent;
    }

    public static class Input {
        public final String label;
        public final Intent intent;

        public Input(String label, Intent intent) {
            this.label = label;
            this.intent = intent;
        }
    }

    private static final int TOP = 0, DOCK = 1, GRID = 2;
    private static final float STRIP_TOP = 318f;   // topo da faixa do dock
    private static final float CLIP_TOP = 150f;    // o conteudo que rola some acima disto
    private static final float FOCUS_Y = 360f;     // onde a fileira da grade em foco para
    private static final float FOCUS_SCALE = 1.08f;
    private static final float PILL_TOP = 26f, PILL_H = 32f;

    private float u = 1f, ox = 0f, oy = 0f;

    private List<Item> dock = new ArrayList<>();
    private List<Item> grid = new ArrayList<>();
    private List<Input> inputs = new ArrayList<>();
    private int cols = 4;
    private float tw = 150f, th = 84.375f, gap = 18f;

    private String wifi = "Wi-Fi";
    private String greetText = "", clockText = "", dateText = "";

    private int section = DOCK, idx = 0, lastDock = 0;
    private boolean menuOpen = false;
    private int menuSel = 0;

    private final float[] pillL = new float[3], pillW = new float[3];
    private final String[] pillText = new String[3];

    // animacao: tudo e interpolado por "prog" entre o estado "From" e "To"
    private final float[] hlFrom = new float[5], hlTo = new float[5], hlCur = new float[5];
    private float scrollFrom = 0f, scrollTo = 0f;
    private int glowFrom = 0xFF3A5FCD, glowTo = 0xFF3A5FCD;
    private float prog = 1f;
    private final ValueAnimator anim;

    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pStrip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPillBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMenuBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMenuStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMenuSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tGreet, tClock, tDate, tBig, tPill, tName, tMenu;

    public HomeView(Context ctx) {
        super(ctx);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.BLACK);

        Typeface tf;
        try {
            tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/Outfit.ttf");
        } catch (Throwable t) {
            tf = Typeface.DEFAULT;
        }
        tGreet = text(tf, 15f, 0xB3FFFFFF, 300, Paint.Align.LEFT);
        tClock = text(tf, 56f, 0xFFFFFFFF, 200, Paint.Align.LEFT);
        tDate = text(tf, 15f, 0xB3FFFFFF, 300, Paint.Align.LEFT);
        tBig = text(tf, 38f, 0xFFFFFFFF, 300, Paint.Align.CENTER);
        tPill = text(tf, 13f, 0xFFFFFFFF, 400, Paint.Align.CENTER);
        tName = text(tf, 13f, 0xFFFFFFFF, 400, Paint.Align.CENTER);
        tMenu = text(tf, 14f, 0xFFFFFFFF, 400, Paint.Align.LEFT);

        pStrip.setColor(0xFF1C1C1E);
        pPillBg.setColor(0x26FFFFFF);
        pGlassFill.setColor(0x40FFFFFF);
        pGlassStroke.setColor(0xA6FFFFFF);
        pGlassStroke.setStyle(Paint.Style.STROKE);
        pGlassStroke.setStrokeWidth(2f);
        pMenuBg.setColor(0xF01C1C1E);
        pMenuStroke.setColor(0x33FFFFFF);
        pMenuStroke.setStyle(Paint.Style.STROKE);
        pMenuStroke.setStrokeWidth(1.5f);
        pMenuSel.setColor(0x3DFFFFFF);

        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(170);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            prog = (Float) a.getAnimatedValue();
            invalidate();
        });

        refreshTime();
        layoutPills();
        relayout();
    }

    private static Paint text(Typeface tf, float size, int color, int wght, Paint.Align align) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        if (tf != null) p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(align);
        try {
            p.setFontVariationSettings("'wght' " + wght);
        } catch (Throwable ignored) {
        }
        return p;
    }

    // ---------------------------------------------------------------- dados

    public static float scaleFor(int w, int h) {
        return Math.min(w / 960f, h / 540f);
    }

    public void setCols(int c) {
        cols = Math.max(1, Math.min(7, c));
        relayout();
    }

    public int tilePxW(float unit) {
        return Math.round(tw * unit);
    }

    public int tilePxH(float unit) {
        return Math.round(th * unit);
    }

    private void relayout() {
        gap = cols > 5 ? 12f : 18f;
        tw = Math.min(150f, (900f - (cols + 1) * gap) / cols);
        th = tw * 9f / 16f;
    }

    public void setData(List<Item> newDock, List<Item> newGrid, String lastPkg) {
        String keep = currentPkg();
        if (keep == null) keep = lastPkg;
        dock = newDock;
        grid = newGrid;
        int fd = find(dock, keep), fg = find(grid, keep);
        if (fd >= 0) {
            section = DOCK;
            idx = fd;
            lastDock = fd;
        } else if (fg >= 0) {
            section = GRID;
            idx = fg;
        } else if (!dock.isEmpty()) {
            section = DOCK;
            idx = 0;
            lastDock = 0;
        } else if (!grid.isEmpty()) {
            section = GRID;
            idx = 0;
        } else {
            section = TOP;
            idx = 0;
        }
        snap();
    }

    private static int find(List<Item> l, String pkg) {
        if (pkg == null) return -1;
        for (int i = 0; i < l.size(); i++) if (pkg.equals(l.get(i).pkg)) return i;
        return -1;
    }

    public void setInputs(List<Input> list) {
        inputs = list;
        if (menuSel >= inputs.size()) menuSel = 0;
    }

    public void setWifi(String name) {
        if (name == null || name.equals(wifi)) return;
        wifi = name;
        layoutPills();
        snap();
    }

    /** Pacote do tile em foco (ou null se o foco esta na barra superior). */
    public String currentPkg() {
        Item it = focusedItem();
        return it == null ? null : it.pkg;
    }

    public void refreshTime() {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        greetText = h < 5 ? "Boa noite" : h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
        clockText = String.format(Locale.US, "%02d:%02d", h, cal.get(Calendar.MINUTE));
        String d = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR")).format(cal.getTime());
        dateText = d.isEmpty() ? d : Character.toUpperCase(d.charAt(0)) + d.substring(1);
        invalidate();
    }

    /** Tecla Home pressionada com o launcher ja aberto: volta ao dock. */
    public void goHome() {
        menuOpen = false;
        if (section != DOCK && !dock.isEmpty()) {
            moveTo(DOCK, Math.min(lastDock, dock.size() - 1));
        }
        invalidate();
    }

    // ------------------------------------------------------------ geometria

    private float dockStripW() {
        int n = dock.size();
        return n * tw + (n + 1) * gap;
    }

    private float dockX(int i) {
        return (960f - dockStripW()) / 2f + gap + i * (tw + gap);
    }

    private float dockTileY() {
        return STRIP_TOP + 18f;
    }

    private float stripH() {
        return th + 36f;
    }

    private float gridX(int c) {
        float w = cols * tw + (cols + 1) * gap;
        return (960f - w) / 2f + gap + c * (tw + gap);
    }

    private float rowY(int r) {
        return STRIP_TOP + stripH() + 54f + r * (th + 36f);
    }

    private void layoutPills() {
        pillText[0] = "Entradas";
        pillText[1] = wifi;
        pillText[2] = "Configurações";
        float right = 912f;
        for (int i = 2; i >= 0; i--) {
            float w = tPill.measureText(pillText[i]) + 40f;
            pillW[i] = w;
            right -= w;
            pillL[i] = right;
            right -= 10f;
        }
    }

    private Item focusedItem() {
        if (section == DOCK && idx >= 0 && idx < dock.size()) return dock.get(idx);
        if (section == GRID && idx >= 0 && idx < grid.size()) return grid.get(idx);
        return null;
    }

    private float scrollFor(int sec, int i) {
        if (sec == GRID) return Math.max(0f, rowY(i / cols) - FOCUS_Y);
        return 0f;
    }

    private void target(int sec, int i, float[] out, float scroll) {
        if (sec == TOP) {
            int k = Math.max(0, Math.min(2, i));
            out[0] = pillL[k] - 4f;
            out[1] = PILL_TOP - 4f;
            out[2] = pillW[k] + 8f;
            out[3] = PILL_H + 8f;
            out[4] = 20f;
        } else {
            float x, y;
            if (sec == DOCK) {
                x = dockX(i);
                y = dockTileY();
            } else {
                x = gridX(i % cols);
                y = rowY(i / cols);
            }
            float cx = x + tw / 2f, cy = y + th / 2f - scroll;
            float w = tw * FOCUS_SCALE + 12f, h = th * FOCUS_SCALE + 12f;
            out[0] = cx - w / 2f;
            out[1] = cy - h / 2f;
            out[2] = w;
            out[3] = h;
            out[4] = 18f;
        }
    }

    // ------------------------------------------------------------ animacao

    private float curScroll() {
        return scrollFrom + (scrollTo - scrollFrom) * prog;
    }

    private float[] curHl() {
        for (int k = 0; k < 5; k++) hlCur[k] = hlFrom[k] + (hlTo[k] - hlFrom[k]) * prog;
        return hlCur;
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private int curGlow() {
        return lerpColor(glowFrom, glowTo, prog);
    }

    private void snap() {
        anim.cancel();
        scrollTo = scrollFor(section, idx);
        scrollFrom = scrollTo;
        target(section, idx, hlTo, scrollTo);
        System.arraycopy(hlTo, 0, hlFrom, 0, 5);
        Item it = focusedItem();
        if (it != null) glowTo = it.glow;
        glowFrom = glowTo;
        prog = 1f;
        invalidate();
    }

    private void moveTo(int sec, int i) {
        float[] cur = curHl();
        System.arraycopy(cur, 0, hlFrom, 0, 5);
        scrollFrom = curScroll();
        glowFrom = curGlow();
        section = sec;
        idx = i;
        if (sec == DOCK) lastDock = i;
        scrollTo = scrollFor(sec, i);
        target(sec, i, hlTo, scrollTo);
        Item it = focusedItem();
        if (it != null) glowTo = it.glow;
        anim.cancel();
        prog = 0f;
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        u = scaleFor(w, h);
        ox = (w - 960f * u) / 2f;
        oy = (h - 540f * u) / 2f;
        layoutPills();
        snap();
    }

    // -------------------------------------------------------------- desenho

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        c.save();
        c.translate(ox, oy);
        c.scale(u, u);

        float scroll = curScroll();
        drawGlow(c, scroll);

        // faixa do dock
        if (!dock.isEmpty()) {
            c.save();
            c.clipRect(0, CLIP_TOP, 960, 540);
            c.translate(0, -scroll);
            float sw = dockStripW();
            rect.set((960f - sw) / 2f, STRIP_TOP, (960f + sw) / 2f, STRIP_TOP + stripH());
            c.drawRoundRect(rect, 24f, 24f, pStrip);
            c.restore();
        }

        // pilulas foscas
        for (int i = 0; i < 3; i++) {
            rect.set(pillL[i], PILL_TOP, pillL[i] + pillW[i], PILL_TOP + PILL_H);
            c.drawRoundRect(rect, 16f, 16f, pPillBg);
        }

        // destaque unico de vidro que desliza de item em item
        float[] h = curHl();
        rect.set(h[0], h[1], h[0] + h[2], h[1] + h[3]);
        c.drawRoundRect(rect, h[4], h[4], pGlassFill);
        c.drawRoundRect(rect, h[4], h[4], pGlassStroke);

        // tiles, nome grande e nome sob o tile
        c.save();
        c.clipRect(0, CLIP_TOP, 960, 540);
        c.translate(0, -scroll);
        for (int i = 0; i < dock.size(); i++) {
            drawTile(c, dock.get(i), dockX(i), dockTileY(), section == DOCK && idx == i);
        }
        for (int i = 0; i < grid.size(); i++) {
            drawTile(c, grid.get(i), gridX(i % cols), rowY(i / cols), section == GRID && idx == i);
        }
        if (section == DOCK && idx < dock.size()) {
            c.drawText(dock.get(idx).label, 480f, STRIP_TOP - 28f, tBig);
        } else if (section == GRID && idx < grid.size()) {
            float cy = rowY(idx / cols) + th / 2f;
            float bottom = cy + th * FOCUS_SCALE / 2f;
            c.drawText(grid.get(idx).label, gridX(idx % cols) + tw / 2f, bottom + 22f, tName);
        }
        c.restore();

        // cabecalho: textos das pilulas, saudacao, relogio e data
        for (int i = 0; i < 3; i++) {
            c.drawText(pillText[i], pillL[i] + pillW[i] / 2f, PILL_TOP + 20.5f, tPill);
        }
        c.drawText(greetText, 48f, 52f, tGreet);
        c.drawText(clockText, 46f, 110f, tClock);
        c.drawText(dateText, 48f, 136f, tDate);

        if (menuOpen) drawMenu(c);
        c.restore();
    }

    private void drawGlow(Canvas c, float scroll) {
        float a = 1f - Math.min(1f, scroll / 130f);
        if (a <= 0.02f) return;
        int g = curGlow() & 0x00FFFFFF;
        int center = ((int) (0x5C * a) << 24) | g;
        pGlow.setShader(new RadialGradient(480f, 300f, 380f, center, g, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 540, pGlow);
    }

    private void drawTile(Canvas c, Item it, float x, float y, boolean focused) {
        float s = focused ? 1f + (FOCUS_SCALE - 1f) * prog : 1f;
        c.save();
        c.scale(s, s, x + tw / 2f, y + th / 2f);
        rect.set(x, y, x + tw, y + th);
        if (it.tile != null) c.drawBitmap(it.tile, null, rect, paint);
        else c.drawRoundRect(rect, 14f, 14f, pStrip);
        c.restore();
    }

    private void drawMenu(Canvas c) {
        if (inputs.isEmpty()) return;
        float rowH = 34f, w = 220f;
        float x = Math.min(pillL[0], 960f - w - 16f), y = PILL_TOP + PILL_H + 10f;
        float h = inputs.size() * rowH + 16f;
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 18f, 18f, pMenuBg);
        c.drawRoundRect(rect, 18f, 18f, pMenuStroke);
        for (int i = 0; i < inputs.size(); i++) {
            float ry = y + 8f + i * rowH;
            if (i == menuSel) {
                rect.set(x + 8f, ry, x + w - 8f, ry + rowH);
                c.drawRoundRect(rect, 12f, 12f, pMenuSel);
            }
            c.drawText(inputs.get(i).label, x + 22f, ry + 22f, tMenu);
        }
    }

    // ------------------------------------------------------------ controle

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (menuOpen) return menuKey(k, e);
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                horiz(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                horiz(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                vert(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                vert(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) activate();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (section != DOCK && !dock.isEmpty()) {
                    int target = section == GRID ? Math.min(idx % cols, dock.size() - 1)
                            : Math.min(lastDock, dock.size() - 1);
                    moveTo(DOCK, target);
                }
                return true;
            default:
                return super.onKeyDown(k, e);
        }
    }

    private boolean menuKey(int k, KeyEvent e) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                menuSel = Math.max(0, menuSel - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                menuSel = Math.min(inputs.size() - 1, menuSel + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) {
                    menuOpen = false;
                    launch(inputs.get(menuSel).intent);
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                menuOpen = false;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                break;
            default:
                return super.onKeyDown(k, e);
        }
        invalidate();
        return true;
    }

    /** Esquerda/direita andam exatamente um tile e nao escorregam nas pontas. */
    private void horiz(int d) {
        if (section == TOP) {
            int ni = idx + d;
            if (ni < 0 || ni > 2) return;
            moveTo(TOP, ni);
        } else if (section == DOCK) {
            int ni = idx + d;
            if (ni < 0 || ni >= dock.size()) return;
            moveTo(DOCK, ni);
        } else {
            int r = idx / cols, c = idx % cols;
            int rowLen = Math.min(cols, grid.size() - r * cols);
            int nc = c + d;
            if (nc < 0 || nc >= rowLen) return;
            moveTo(GRID, r * cols + nc);
        }
    }

    /** Cima/baixo andam exatamente uma fileira. */
    private void vert(int d) {
        if (section == TOP) {
            if (d > 0) {
                if (!dock.isEmpty()) moveTo(DOCK, Math.min(lastDock, dock.size() - 1));
                else if (!grid.isEmpty()) moveTo(GRID, 0);
            }
            return;
        }
        if (section == DOCK) {
            if (d < 0) {
                moveTo(TOP, 0);
            } else if (!grid.isEmpty()) {
                moveTo(GRID, Math.min(idx, Math.min(cols, grid.size()) - 1));
            }
            return;
        }
        int r = idx / cols, c = idx % cols;
        if (d < 0) {
            if (r == 0) {
                if (!dock.isEmpty()) moveTo(DOCK, Math.min(c, dock.size() - 1));
                else moveTo(TOP, 0);
            } else {
                moveTo(GRID, (r - 1) * cols + c);
            }
        } else {
            int nr = r + 1;
            if (nr * cols >= grid.size()) return;
            int len = Math.min(cols, grid.size() - nr * cols);
            moveTo(GRID, nr * cols + Math.min(c, len - 1));
        }
    }

    private void activate() {
        if (section == TOP) {
            if (idx == 0) {
                if (inputs.isEmpty()) {
                    Toast.makeText(getContext(), "Nenhuma entrada encontrada", Toast.LENGTH_SHORT).show();
                } else {
                    menuOpen = true;
                    menuSel = 0;
                    invalidate();
                }
            } else if (idx == 1) {
                launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } else {
                launch(new Intent(Settings.ACTION_SETTINGS));
            }
        } else {
            Item it = focusedItem();
            if (it != null) launch(it.intent);
        }
    }

    private void launch(Intent i) {
        if (i == null) return;
        try {
            Intent copy = new Intent(i);
            copy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            getContext().startActivity(copy);
        } catch (Exception ex) {
            if (Settings.ACTION_WIFI_SETTINGS.equals(i.getAction())) {
                launch(new Intent(Settings.ACTION_SETTINGS));
            } else {
                Toast.makeText(getContext(), "Não foi possível abrir", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
