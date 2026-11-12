package com.bacad.ctrlkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyboard Android dengan tombol Ctrl, Shift, Alt, Insert, Esc, Tab, Del,
 * Home/End, PgUp/PgDn dan panah.
 *
 * Tombol modifier (Ctrl / Shift / Alt):
 *   tap 1x = aktif sekali (biru)   -> mati otomatis setelah tombol berikutnya
 *   tap 2x = terkunci   (oranye)   -> tetap aktif sampai di-tap lagi
 *   tap 3x = mati
 */
public class CtrlKeyboardService extends InputMethodService {

    private static final int OFF = 0, ONCE = 1, LOCK = 2;

    private static final int C_BG = Color.parseColor("#1F2227");
    private static final int C_KEY = Color.parseColor("#3A3F47");
    private static final int C_SPECIAL = Color.parseColor("#2B2F36");
    private static final int C_ONCE = Color.parseColor("#1E88E5");
    private static final int C_LOCK = Color.parseColor("#F57C00");
    private static final int C_PRESSED = Color.parseColor("#5A616B");

    // Token diawali ":" = tombol khusus
    // Baris alat copas: sekali tap langsung kirim kombinasi (tanpa perlu tekan modifier dulu)
    private static final String[] ROW_TOOLS = {":copy", ":paste", ":pastetext", ":ctrlc"};
    private static final String[] ROW_EXTRA = {":esc", ":tab", ":ctrl", ":alt", ":ins", ":del", ":home", ":end", ":pgup", ":pgdn"};
    private static final String[] ROW_NUM = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] ROW_Q = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW_A = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW_Z = {":shift", "z", "x", "c", "v", "b", "n", "m", ":bksp"};
    private static final String[] ROW_S1 = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")"};
    private static final String[] ROW_S2 = {"-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"};
    private static final String[] ROW_S3 = {":shift", ";", ":", "'", "\"", "`", "~", "<", ">", "/", "?", ":bksp"};
    private static final String[] ROW_BOTTOM = {":sym", ",", ":space", ".", ":left", ":up", ":down", ":right", ":enter"};

    private static final String NUM_SHIFTED = "!@#$%^&*()";

    private int ctrl = OFF, shift = OFF, alt = OFF;
    private boolean symMode = false;

    private LinearLayout root;
    private final Map<String, TextView> modKeys = new HashMap<>();
    private final List<TextView> charKeys = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ view

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        int p = dp(3);
        root.setPadding(p, p, p, p);
        buildKeyboard();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (ctrl == ONCE) ctrl = OFF;
        if (shift == ONCE) shift = OFF;
        if (alt == ONCE) alt = OFF;
        refresh();
    }

    private void buildKeyboard() {
        root.removeAllViews();
        modKeys.clear();
        charKeys.clear();
        addRow(ROW_TOOLS, 38);
        addRow(ROW_EXTRA);
        addRow(ROW_NUM);
        if (symMode) {
            addRow(ROW_S1);
            addRow(ROW_S2);
            addRow(ROW_S3);
        } else {
            addRow(ROW_Q);
            addRow(ROW_A);
            addRow(ROW_Z);
        }
        addRow(ROW_BOTTOM);
        refresh();
    }

    private void addRow(String[] tokens) {
        addRow(tokens, 46);
    }

    private void addRow(String[] tokens, int heightDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (String t : tokens) row.addView(makeKey(t));
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private View makeKey(final String token) {
        final TextView key = new TextView(this);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(Color.WHITE);
        key.setSingleLine(true);
        boolean special = token.startsWith(":") && token.length() > 1;
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, special ? 12 : 18);
        key.setText(special ? label(token) : token);
        key.setTag(token);

        float weight = 1f;
        if (token.equals(":space")) weight = 3.5f;
        else if (token.equals(":shift") || token.equals(":bksp") || token.equals(":enter")) weight = 1.5f;

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight);
        int m = dp(2);
        lp.setMargins(m, m, m, m);
        key.setLayoutParams(lp);
        key.setBackground(bg(special ? C_SPECIAL : C_KEY));

        if (token.equals(":ctrl") || token.equals(":shift") || token.equals(":alt")) {
            modKeys.put(token, key);
        } else if (!special) {
            charKeys.add(key);
        }

        final boolean repeat = token.equals(":bksp") || token.equals(":del")
                || token.equals(":left") || token.equals(":right")
                || token.equals(":up") || token.equals(":down")
                || token.equals(":space");

        key.setOnTouchListener(new View.OnTouchListener() {
            final Runnable repeater = new Runnable() {
                @Override public void run() {
                    press(token, true);
                    handler.postDelayed(this, 50);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        setPressed(key, true);
                        if (repeat) {
                            press(token, true);
                            handler.postDelayed(repeater, 400);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        setPressed(key, false);
                        if (repeat) {
                            handler.removeCallbacks(repeater);
                            consumeOnce();
                        } else {
                            press(token, false);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        setPressed(key, false);
                        handler.removeCallbacks(repeater);
                        return true;
                }
                return false;
            }
        });
        return key;
    }

    private String label(String t) {
        switch (t) {
            case ":copy": return "Salin ⌃Ins";
            case ":paste": return "Tempel ⇧Ins";
            case ":pastetext": return "Tempel teks";
            case ":ctrlc": return "Ctrl+C";
            case ":esc": return "Esc";
            case ":tab": return "Tab";
            case ":ctrl": return "Ctrl";
            case ":alt": return "Alt";
            case ":ins": return "Ins";
            case ":del": return "Del";
            case ":home": return "Home";
            case ":end": return "End";
            case ":pgup": return "PgUp";
            case ":pgdn": return "PgDn";
            case ":shift": return "⇧ Shift";
            case ":bksp": return "⌫";
            case ":sym": return symMode ? "ABC" : "?123";
            case ":space": return "spasi";
            case ":left": return "←";
            case ":right": return "→";
            case ":up": return "↑";
            case ":down": return "↓";
            case ":enter": return "⏎";
        }
        return t;
    }

    // ---------------------------------------------------------------- input

    /** @param fromRepeat true bila dipanggil dari tombol yang bisa ditahan (modifier dilepas saat jari diangkat). */
    private void press(String token, boolean fromRepeat) {
        switch (token) {
            case ":ctrl":  ctrl = next(ctrl);   refresh(); return;
            case ":shift": shift = next(shift); refresh(); return;
            case ":alt":   alt = next(alt);     refresh(); return;
            case ":sym":   symMode = !symMode;  buildKeyboard(); return;

            case ":copy":      sendCombo(KeyEvent.KEYCODE_INSERT, true, false); return;
            case ":paste":     sendCombo(KeyEvent.KEYCODE_INSERT, false, true); return;
            case ":ctrlc":     sendCombo(KeyEvent.KEYCODE_C, true, false); return;
            case ":pastetext": typeClipboard(); return;

            case ":esc":   sendKey(KeyEvent.KEYCODE_ESCAPE); break;
            case ":tab":   sendKey(KeyEvent.KEYCODE_TAB); break;
            case ":ins":   sendKey(KeyEvent.KEYCODE_INSERT); break;
            case ":del":   sendKey(KeyEvent.KEYCODE_FORWARD_DEL); break;
            case ":home":  sendKey(KeyEvent.KEYCODE_MOVE_HOME); break;
            case ":end":   sendKey(KeyEvent.KEYCODE_MOVE_END); break;
            case ":pgup":  sendKey(KeyEvent.KEYCODE_PAGE_UP); break;
            case ":pgdn":  sendKey(KeyEvent.KEYCODE_PAGE_DOWN); break;
            case ":left":  sendKey(KeyEvent.KEYCODE_DPAD_LEFT); break;
            case ":right": sendKey(KeyEvent.KEYCODE_DPAD_RIGHT); break;
            case ":up":    sendKey(KeyEvent.KEYCODE_DPAD_UP); break;
            case ":down":  sendKey(KeyEvent.KEYCODE_DPAD_DOWN); break;
            case ":bksp":  sendKey(KeyEvent.KEYCODE_DEL); break;
            case ":enter": enter(); break;
            case ":space":
                if (anyMod()) sendKey(KeyEvent.KEYCODE_SPACE);
                else commit(" ");
                break;
            default:
                typeChar(token);
        }
        if (!fromRepeat) consumeOnce();
    }

    private void typeChar(String ch) {
        char c = ch.charAt(0);
        // Ctrl/Alt + huruf -> kirim sebagai key event (Ctrl+C, Ctrl+V, Ctrl+Z, dll)
        if (ctrl != OFF || alt != OFF) {
            int code = keyCodeFor(c);
            if (code != KeyEvent.KEYCODE_UNKNOWN) {
                sendKey(code);
                return;
            }
        }
        commit(shift != OFF ? shifted(ch) : ch);
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (!anyMod() && ei != null
                && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendKey(KeyEvent.KEYCODE_ENTER);
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private void sendKey(int code) {
        sendCombo(code, ctrl != OFF, shift != OFF, alt != OFF);
    }

    private void sendCombo(int code, boolean withCtrl, boolean withShift) {
        sendCombo(code, withCtrl, withShift, false);
    }

    /**
     * Kirim kombinasi seperti keyboard fisik: modifier ditekan dulu, lalu tombol,
     * lalu semuanya dilepas. Terminal berbasis web (xterm.js di TrueNAS) butuh urutan ini.
     */
    private void sendCombo(int code, boolean withCtrl, boolean withShift, boolean withAlt) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int meta = 0;
        if (withCtrl)  meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (withShift) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (withAlt)   meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;

        int[] mods = new int[3];
        int[] modMeta = new int[3];
        int n = 0, running = 0;
        if (withCtrl)  { running |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;   mods[n] = KeyEvent.KEYCODE_CTRL_LEFT;  modMeta[n++] = running; }
        if (withShift) { running |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON; mods[n] = KeyEvent.KEYCODE_SHIFT_LEFT; modMeta[n++] = running; }
        if (withAlt)   { running |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;     mods[n] = KeyEvent.KEYCODE_ALT_LEFT;   modMeta[n++] = running; }

        for (int i = 0; i < n; i++) raw(ic, KeyEvent.ACTION_DOWN, mods[i], modMeta[i]);
        raw(ic, KeyEvent.ACTION_DOWN, code, meta);
        raw(ic, KeyEvent.ACTION_UP, code, meta);
        for (int i = n - 1; i >= 0; i--) {
            int after = (i == 0) ? 0 : modMeta[i - 1];
            raw(ic, KeyEvent.ACTION_UP, mods[i], after);
        }
    }

    private void raw(InputConnection ic, int action, int code, int meta) {
        long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, action, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    /** Cadangan bila Shift+Ins tidak jalan: ketikkan langsung isi clipboard Android. */
    private void typeClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            toast("Clipboard kosong");
            return;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("Clipboard kosong");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            toast("Clipboard kosong");
            return;
        }
        commit(text.toString());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static int keyCodeFor(char c) {
        if (c >= 'a' && c <= 'z') return KeyEvent.KEYCODE_A + (c - 'a');
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        switch (c) {
            case ',': return KeyEvent.KEYCODE_COMMA;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case '=': return KeyEvent.KEYCODE_EQUALS;
            case '[': return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']': return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\': return KeyEvent.KEYCODE_BACKSLASH;
            case ';': return KeyEvent.KEYCODE_SEMICOLON;
            case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case '`': return KeyEvent.KEYCODE_GRAVE;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private static String shifted(String ch) {
        char c = ch.charAt(0);
        if (c >= '0' && c <= '9') {
            int i = (c == '0') ? 9 : (c - '1');
            return String.valueOf(NUM_SHIFTED.charAt(i));
        }
        return ch.toUpperCase();
    }

    // ------------------------------------------------------------ modifiers

    private static int next(int state) {
        return state == OFF ? ONCE : (state == ONCE ? LOCK : OFF);
    }

    private boolean anyMod() {
        return ctrl != OFF || shift != OFF || alt != OFF;
    }

    private void consumeOnce() {
        boolean changed = false;
        if (ctrl == ONCE)  { ctrl = OFF;  changed = true; }
        if (shift == ONCE) { shift = OFF; changed = true; }
        if (alt == ONCE)   { alt = OFF;   changed = true; }
        if (changed) refresh();
    }

    private void refresh() {
        if (root == null) return;
        paintMod(":ctrl", ctrl);
        paintMod(":shift", shift);
        paintMod(":alt", alt);
        for (TextView k : charKeys) {
            String base = (String) k.getTag();
            k.setText(shift != OFF ? shifted(base) : base);
        }
    }

    private void paintMod(String token, int state) {
        TextView k = modKeys.get(token);
        if (k == null) return;
        int color = state == ONCE ? C_ONCE : (state == LOCK ? C_LOCK : C_SPECIAL);
        k.setBackground(bg(color));
    }

    // -------------------------------------------------------------- helpers

    private void setPressed(TextView key, boolean pressed) {
        String t = (String) key.getTag();
        if (modKeys.containsKey(t)) return; // warna modifier diatur refresh()
        boolean special = t.startsWith(":") && t.length() > 1;
        key.setBackground(bg(pressed ? C_PRESSED : (special ? C_SPECIAL : C_KEY)));
    }

    private GradientDrawable bg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(6));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
