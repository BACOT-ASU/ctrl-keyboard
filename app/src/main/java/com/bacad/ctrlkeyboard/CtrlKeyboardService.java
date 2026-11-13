package com.bacad.ctrlkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Satu keyboard, dua mode:
 *  - Mode TEKS  : tata letak ala Gboard + bar saran yang belajar dari ketikanmu.
 *  - Mode CTRL  : tombol Ctrl/Shift/Alt/Ins/Esc/Tab/panah untuk shell (TrueNAS, SSH).
 * Pindah mode lewat tombol "⌨ Ctrl" di bar saran, dan "ABC" di mode Ctrl.
 */
public class CtrlKeyboardService extends InputMethodService {

    static final String PREFS = "ctrlkb";

    private static final int OFF = 0, ONCE = 1, LOCK = 2;
    private static final int MODE_TEXT = 0, MODE_CTRL = 1;
    private static final int L_ALPHA = 0, L_SYM1 = 1, L_SYM2 = 2, L_PHRASES = 3, L_EMOJI = 4;

    /** Tinggi area tombol (di bawah bar saran), sama untuk semua layer agar tidak "loncat". */
    private static final int KEYS_DP = 5 * 52;
    private static final int STRIP_DP = 42;

    // Warna mengikuti Gboard tema gelap
    private static final int C_BG = Color.parseColor("#1B1B1D");
    private static final int C_KEY = Color.parseColor("#3A3A3C");
    private static final int C_SPECIAL = Color.parseColor("#3A3A3C");
    private static final int C_ONCE = Color.parseColor("#5B6B8C");
    private static final int C_LOCK = Color.parseColor("#A8C7FA");
    private static final int C_PRESSED = Color.parseColor("#5C5C60");
    private static final int C_HINT = Color.parseColor("#D0D0D4");
    private static final int C_ACCENT = Color.parseColor("#A8C7FA");
    private static final int C_ON_ACCENT = Color.parseColor("#0B1D36");
    private static final int C_PREVIEW = Color.parseColor("#5C5C60");

    // ---------------- Mode TEKS (ala Gboard + baris angka). "q %" = tombol q, tahan lama = %
    private static final String[] T0 = {"1 !", "2 @", "3 #", "4 $", "5 %", "6 ^", "7 &", "8 *", "9 (", "0 )"};
    private static final String[] T1 = {"q %", "w \\", "e |", "r =", "t [", "y ]", "u <", "i >", "o {", "p }"};
    private static final String[] T2 = {"a @", "s #", "d $", "f _", "g &", "h -", "j +", "k (", "l )"};
    private static final String[] T3 = {":shift", "z *", "x \"", "c '", "v :", "b ;", "n !", "m ?", ":bksp"};
    private static final String[] T4 = {":sym", ",", ":emoji", ":space", ".", ":enter"};

    private static final String[] S1_1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] S1_2 = {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"};
    private static final String[] S1_3 = {":sym2", "*", "\"", "'", ":", ";", "!", "?", "_", ":bksp"};
    private static final String[] S1_4 = {":abc", ",", ":emoji", ":space", ".", ":enter"};

    private static final String[] S2_1 = {"~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"};
    private static final String[] S2_2 = {"£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"};
    private static final String[] S2_3 = {":sym", "%", "©", "®", "™", "✓", "[", "]", ":bksp"};
    private static final String[] S2_4 = {":abc", "<", ":emoji", ":space", ">", ":enter"};

    // ---------------- Mode CTRL
    private static final String[] ROW_TOOLS = {":textmode", ":copy", ":paste", ":pastetext", ":ctrlc"};
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

    private static final class KeyInfo {
        final TextView v; final String base; final String hint;
        KeyInfo(TextView v, String base, String hint) { this.v = v; this.base = base; this.hint = hint; }
    }

    private int mode = MODE_TEXT;
    private int layer = L_ALPHA;

    // status mode TEKS
    private int tShift = OFF;
    private boolean autoShifted = false;
    private long lastShiftTap = 0, lastSpaceTime = 0;
    private boolean learnAllowed = true, suggestAllowed = true;
    private String lastLine = "";
    private boolean autoCap = true;

    // status mode CTRL
    private int ctrl = OFF, shift = OFF, alt = OFF;
    private boolean symMode = false;

    private LinearLayout root;
    private final Map<String, TextView> modKeys = new HashMap<>();
    private final List<KeyInfo> charKeys = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView[] sugViews = new TextView[3];
    private final String[] sugValues = new String[3];
    private final boolean[] sugPhrase = new boolean[3];

    private LearnStore store;
    private final Runnable saveTask = () -> { if (store != null) store.save(); };

    // ================================================================ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        store = new LearnStore(this);
        mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("mode", MODE_TEXT);
    }

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
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean pwd = (cls == InputType.TYPE_CLASS_TEXT
                && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        boolean noLearn = (info.imeOptions & 0x1000000) != 0; // IME_FLAG_NO_PERSONALIZED_LEARNING (incognito)
        learnAllowed = !pwd && !noLearn;
        suggestAllowed = !pwd && cls == InputType.TYPE_CLASS_TEXT;

        if (ctrl == ONCE) ctrl = OFF;
        if (shift == ONCE) shift = OFF;
        if (alt == ONCE) alt = OFF;
        tShift = OFF;
        autoShifted = false;
        boolean numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        layer = numeric ? L_SYM1 : L_ALPHA;
        lastLine = "";

        autoCap = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("autoCap", true);
        if (store != null) store.reloadIfChanged();
        if (root != null) buildKeyboard();
        updateAutoShift();
        updateSuggestions();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        handler.removeCallbacks(saveTask);
        if (store != null) store.save();
        hidePreview();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (mode != MODE_TEXT) return;
        String before = before(300);
        // Kolom tiba-tiba kosong (pesan terkirim) -> kalimat terakhir dicatat
        if (before.isEmpty() && lastLine.trim().length() >= 5 && learnAllowed && after(1).isEmpty()) {
            store.addPhrase(lastLine);
            scheduleSave();
        }
        lastLine = currentLine(before);
        updateAutoShift();
        updateSuggestions();
    }

    // ================================================================ build

    private void buildKeyboard() {
        if (root == null) return;
        root.removeAllViews();
        modKeys.clear();
        charKeys.clear();
        for (int i = 0; i < 3; i++) sugViews[i] = null;
        if (mode == MODE_CTRL) buildCtrl(); else buildText();
        refresh();
    }

    // ---------------------------------------------------------------- TEKS

    private void buildText() {
        buildStrip();
        if (layer == L_PHRASES) {
            buildPhrasePanel();
            return;
        }
        if (layer == L_EMOJI) {
            buildEmojiPanel();
            return;
        }
        String[][] rows;
        if (layer == L_SYM1) rows = new String[][]{S1_1, S1_2, S1_3, S1_4};
        else if (layer == L_SYM2) rows = new String[][]{S2_1, S2_2, S2_3, S2_4};
        else rows = new String[][]{T0, T1, T2, T3, T4};
        int rowH = KEYS_DP / rows.length;
        for (int r = 0; r < rows.length; r++) {
            addTextRow(rows[r], layer == L_ALPHA && r == 2, rowH);
        }
    }

    // ---------------------------------------------------------------- EMOJI

    private int emojiCat = 0;           // -1 = baru dipakai
    private final List<String> recentEmoji = new ArrayList<>();
    private boolean recentLoaded = false;

    private void loadRecent() {
        if (recentLoaded) return;
        recentLoaded = true;
        String s = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recentEmoji", "");
        for (String e : s.split("\n")) if (!e.isEmpty()) recentEmoji.add(e);
    }

    private void addRecent(String e) {
        recentEmoji.remove(e);
        recentEmoji.add(0, e);
        while (recentEmoji.size() > 40) recentEmoji.remove(recentEmoji.size() - 1);
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("recentEmoji", TextUtils.join("\n", recentEmoji)).apply();
    }

    private void buildEmojiPanel() {
        loadRecent();
        final int barH = 48;
        final ScrollView sv = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid);

        List<String> items = new ArrayList<>();
        if (emojiCat < 0) items.addAll(recentEmoji);
        else for (String e : Emojis.CATS[emojiCat]) items.add(e);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setTextColor(C_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(12));
            empty.setText("Emoji yang baru kamu pakai akan muncul di sini");
            grid.addView(empty);
        }
        final int perRow = 8;
        for (int i = 0; i < items.size(); i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < perRow; j++) {
                TextView t = new TextView(this);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
                t.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
                if (i + j < items.size()) {
                    final String e = items.get(i + j);
                    t.setText(e);
                    t.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        commit(e);
                        addRecent(e);
                    });
                }
                row.addView(t);
            }
            grid.addView(row);
        }
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KEYS_DP - barH)));

        // Bar kategori di bawah, seperti Gboard: ABC | 🕘 😀 👋 … | ⌫
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView abc = makeKey(":abc", null, 1.4f, true, false);
        abc.setText("ABC");
        abc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        abc.setBackground(bg(C_SPECIAL, dp(40)));
        bar.addView(abc);
        addEmojiTab(bar, "🕘", -1);
        for (int c = 0; c < Emojis.ICONS.length; c++) addEmojiTab(bar, Emojis.ICONS[c], c);
        TextView del = makeKey(":bksp", null, 1.4f, true, true);
        del.setText("⌫");
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        del.setBackground(bg(C_SPECIAL, dp(40)));
        bar.addView(del);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(barH)));
    }

    private void addEmojiTab(LinearLayout bar, String icon, final int cat) {
        TextView t = new TextView(this);
        t.setText(icon);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setAlpha(cat == emojiCat ? 1f : 0.45f);
        if (cat == emojiCat) t.setBackground(bg(C_PRESSED, dp(20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.setMargins(dp(1), 0, dp(1), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> { emojiCat = cat; buildKeyboard(); });
        bar.addView(t);
    }

    private void buildStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);

        TextView ctrlBtn = stripText("⌨ Ctrl", 1.1f, C_ACCENT);
        ctrlBtn.setOnClickListener(v -> switchMode(MODE_CTRL));
        strip.addView(ctrlBtn);

        TextView star = stripText(layer == L_PHRASES ? "★" : "☆", 0.6f, C_ACCENT);
        star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        star.setOnClickListener(v -> {
            layer = (layer == L_PHRASES) ? L_ALPHA : L_PHRASES;
            buildKeyboard();
            updateSuggestions();
        });
        strip.addView(star);

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView s = stripText("", 2f, Color.WHITE);
            s.setOnClickListener(v -> applySuggestion(idx));
            s.setOnLongClickListener(v -> { forgetSuggestion(idx); return true; });
            sugViews[i] = s;
            strip.addView(s);
        }
        root.addView(strip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(STRIP_DP)));
    }

    private TextView stripText(String text, float weight, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(dp(4), 0, dp(4), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight));
        return t;
    }

    private void addTextRow(String[] tokens, boolean indent, int heightDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        if (indent) row.addView(spacer(0.5f));
        for (String t : tokens) {
            String[] parts = t.split(" ", 2);
            String base = parts[0];
            String alt = parts.length > 1 ? parts[1] : null;
            boolean special = base.startsWith(":") && base.length() > 1;
            KeyView k = makeKey(base, alt, textWeight(base), special, base.equals(":bksp"));
            k.setSingleLine(true);
            if (special) {
                boolean icon = base.equals(":enter") || base.equals(":bksp") || base.equals(":shift") || base.equals(":emoji");
                k.setTextSize(TypedValue.COMPLEX_UNIT_SP, icon ? 22 : 15);
                k.setText(textSpecialLabel(base));
                if (base.equals(":shift")) modKeys.put(base, k);
                if (isPill(base)) k.setBackground(bg(base.equals(":enter") ? C_ACCENT : C_SPECIAL, dp(40)));
                if (base.equals(":enter")) k.setTextColor(C_ON_ACCENT);
                if (base.equals(":space")) k.setTextColor(C_HINT);
            } else {
                k.setTextSize(TypedValue.COMPLEX_UNIT_SP, base.equals(",") || base.equals(".") ? 22 : 24);
                k.hint = alt;
                charKeys.add(new KeyInfo(k, base, alt));
            }
            row.addView(k);
        }
        if (indent) row.addView(spacer(0.5f));
        root.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private static boolean isPill(String base) {
        return base.equals(":enter") || base.equals(":sym") || base.equals(":abc");
    }

    /** Tombol dengan huruf kecil di pojok kanan atas, seperti Gboard. */
    private class KeyView extends TextView {
        String hint;
        private final android.graphics.Paint hintPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        KeyView(Context c) {
            super(c);
            hintPaint.setColor(C_HINT);
            hintPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                    c.getResources().getDisplayMetrics()));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (hint == null) return;
            float pad = dp(4);
            float w = hintPaint.measureText(hint);
            canvas.drawText(hint, getScrollX() + getWidth() - pad - w,
                    getScrollY() + pad - hintPaint.ascent(), hintPaint);
        }
    }

    private View spacer(float w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, w));
        return v;
    }

    private static float textWeight(String base) {
        switch (base) {
            case ":space": return 4f;
            case ":shift": case ":bksp": case ":sym": case ":sym2": case ":abc": case ":enter": return 1.5f;
        }
        return 1f;
    }

    private String textSpecialLabel(String t) {
        switch (t) {
            case ":shift": return tShift == LOCK ? "⇪" : "⇧";
            case ":bksp": return "⌫";
            case ":sym": return "?123";
            case ":sym2": return "=\\<";
            case ":abc": return "ABC";
            case ":emoji": return "☺";
            case ":space": return "";
            case ":enter": return enterLabel();
        }
        return t;
    }

    private String enterLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "⏎";
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: return "🔍";
            case EditorInfo.IME_ACTION_SEND: return "➤";
            case EditorInfo.IME_ACTION_GO: case EditorInfo.IME_ACTION_NEXT: return "→";
            case EditorInfo.IME_ACTION_DONE: return "✓";
        }
        return "⏎";
    }

    private void buildPhrasePanel() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = makeButton("ABC", 1f);
        back.setOnClickListener(v -> { layer = L_ALPHA; buildKeyboard(); updateSuggestions(); });
        TextView save = makeButton("+ Simpan teks yang sedang diketik", 3f);
        save.setOnClickListener(v -> {
            String line = currentLine(before(300)).trim();
            if (line.isEmpty()) { toast("Ketik kalimatnya dulu, lalu tekan + Simpan"); return; }
            store.pin(line);
            scheduleSave();
            toast("Disimpan: " + line);
            buildKeyboard();
        });
        top.addView(back);
        top.addView(save);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        List<String> ps = store.topPhrases(80);
        if (ps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setTextColor(C_HINT);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            empty.setText("Belum ada kalimat tersimpan.\n\nKalimat yang kamu ketik " + LearnStore.PHRASE_MIN_USES
                    + "x atau lebih akan muncul di sini otomatis. Bisa juga ketik kalimat lalu tekan + Simpan.\n\n"
                    + "Tap = sisipkan, tahan = hapus.");
            list.addView(empty);
        }
        for (final String p : ps) {
            TextView item = makeButton((store.isPinned(p) ? "📌 " : "") + p, 0f);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setSingleLine(false);
            item.setMaxLines(3);
            item.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            item.setLayoutParams(lp);
            item.setOnClickListener(v -> {
                commit(p);
                layer = L_ALPHA;
                buildKeyboard();
                updateSuggestions();
            });
            item.setOnLongClickListener(v -> {
                store.remove(p);
                scheduleSave();
                toast("Dihapus dari daftar");
                buildKeyboard();
                return true;
            });
            list.addView(item);
        }
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KEYS_DP - 44)));
    }

    private TextView makeButton(String text, float weight) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(C_KEY));
        if (weight > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            t.setLayoutParams(lp);
        }
        return t;
    }

    // ---------------------------------------------------------------- CTRL

    private void buildCtrl() {
        addCtrlRow(ROW_TOOLS, 38);
        addCtrlRow(ROW_EXTRA, 46);
        addCtrlRow(ROW_NUM, 46);
        if (symMode) {
            addCtrlRow(ROW_S1, 46);
            addCtrlRow(ROW_S2, 46);
            addCtrlRow(ROW_S3, 46);
        } else {
            addCtrlRow(ROW_Q, 46);
            addCtrlRow(ROW_A, 46);
            addCtrlRow(ROW_Z, 46);
        }
        addCtrlRow(ROW_BOTTOM, 46);
    }

    private void addCtrlRow(String[] tokens, int heightDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (String t : tokens) {
            boolean special = t.startsWith(":") && t.length() > 1;
            float weight = 1f;
            if (t.equals(":space")) weight = 3.5f;
            else if (t.equals(":shift") || t.equals(":bksp") || t.equals(":enter")) weight = 1.5f;
            boolean repeat = t.equals(":bksp") || t.equals(":del") || t.equals(":left") || t.equals(":right")
                    || t.equals(":up") || t.equals(":down") || t.equals(":space");
            TextView k = makeKey(t, null, weight, special, repeat);
            k.setSingleLine(true);
            k.setTextSize(TypedValue.COMPLEX_UNIT_SP, special ? 12 : 18);
            k.setText(special ? ctrlLabel(t) : t);
            if (t.equals(":textmode")) k.setTextColor(C_ACCENT);
            if (t.equals(":ctrl") || t.equals(":shift") || t.equals(":alt")) modKeys.put(t, k);
            else if (!special) charKeys.add(new KeyInfo(k, t, null));
            row.addView(k);
        }
        root.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private String ctrlLabel(String t) {
        switch (t) {
            case ":textmode": return "ABC";
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

    // ---------------------------------------------------------------- keys

    private KeyView makeKey(String token, String alt, float weight, boolean special, boolean repeat) {
        KeyView key = new KeyView(this);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(Color.WHITE);
        key.setIncludeFontPadding(false);
        key.setTag(token);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        int m = dp(2);
        lp.setMargins(m, m + 1, m, m + 1);
        key.setLayoutParams(lp);
        key.setBackground(keyBg(token, special, false));
        key.setOnTouchListener(new KeyTouch(key, token, alt, special, repeat));
        return key;
    }

    private class KeyTouch implements View.OnTouchListener {
        final TextView key; final String token; final String alt; final boolean special; final boolean repeat;
        boolean longFired;
        final Runnable repeater = new Runnable() {
            @Override public void run() {
                onKey(token, true);
                handler.postDelayed(this, 50);
            }
        };
        final Runnable longPress = new Runnable() {
            @Override public void run() {
                longFired = true;
                key.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showPreview(key, alt, true);
            }
        };

        KeyTouch(TextView key, String token, String alt, boolean special, boolean repeat) {
            this.key = key; this.token = token; this.alt = alt; this.special = special; this.repeat = repeat;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    setPressedLook(key, special, true);
                    longFired = false;
                    if (!special) showPreview(key, key.getText().toString(), false);
                    if (repeat) {
                        onKey(token, true);
                        handler.postDelayed(repeater, 400);
                    } else if (alt != null) {
                        handler.postDelayed(longPress, 300);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(repeater);
                    handler.removeCallbacks(longPress);
                    setPressedLook(key, special, false);
                    hidePreview();
                    if (repeat) afterRepeat();
                    else if (longFired) onKey(alt, false);
                    else onKey(token, false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(repeater);
                    handler.removeCallbacks(longPress);
                    setPressedLook(key, special, false);
                    hidePreview();
                    return true;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- key preview (gelembung)

    private android.widget.PopupWindow preview;
    private TextView previewText;

    private void showPreview(TextView key, String label, boolean longPress) {
        if (key.getWindowToken() == null || label == null || label.isEmpty()) return;
        try {
            if (preview == null) {
                previewText = new TextView(this);
                previewText.setGravity(Gravity.CENTER);
                previewText.setIncludeFontPadding(false);
                preview = new android.widget.PopupWindow(previewText);
                preview.setTouchable(false);
                preview.setFocusable(false);
                preview.setClippingEnabled(false);
                preview.setAnimationStyle(0);
            }
            previewText.setText(label);
            previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, longPress ? 32 : 30);
            previewText.setTextColor(longPress ? C_ON_ACCENT : Color.WHITE);
            previewText.setBackground(bg(longPress ? C_ACCENT : C_PREVIEW, dp(12)));

            int[] loc = new int[2];
            key.getLocationInWindow(loc);
            int w = Math.max(key.getWidth() + dp(10), dp(52));
            int h = dp(62);
            int x = loc[0] + (key.getWidth() - w) / 2;
            int y = loc[1] - h - dp(2);
            if (preview.isShowing()) preview.update(x, y, w, h);
            else {
                preview.setWidth(w);
                preview.setHeight(h);
                preview.showAtLocation(key, Gravity.NO_GRAVITY, x, y);
            }
        } catch (Exception ignored) {
            // Sebagian HP menolak popup di atas keyboard; keyboard tetap jalan tanpa gelembung.
        }
    }

    private void hidePreview() {
        try {
            if (preview != null && preview.isShowing()) preview.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void setPressedLook(TextView key, boolean special, boolean pressed) {
        String t = (String) key.getTag();
        key.setBackground(keyBg(t, special, pressed));
        if (!pressed && modKeys.containsKey(t)) refresh();
    }

    private GradientDrawable keyBg(String t, boolean special, boolean pressed) {
        boolean pill = mode == MODE_TEXT && isPill(t);
        int radius = pill ? dp(40) : dp(8);
        int color;
        if (pressed) color = C_PRESSED;
        else if (mode == MODE_TEXT && ":enter".equals(t)) color = C_ACCENT;
        else color = special ? C_SPECIAL : C_KEY;
        return bg(color, radius);
    }

    private void onKey(String token, boolean fromRepeat) {
        if (mode == MODE_CTRL) pressCtrl(token, fromRepeat);
        else pressText(token);
    }

    private void afterRepeat() {
        if (mode == MODE_CTRL) consumeOnce();
        else { updateAutoShift(); updateSuggestions(); }
    }

    private void switchMode(int m) {
        mode = m;
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("mode", m).apply();
        layer = L_ALPHA;
        tShift = OFF;
        buildKeyboard();
        updateAutoShift();
        updateSuggestions();
    }

    // ================================================================ TEKS input

    private void pressText(String token) {
        switch (token) {
            case ":shift": {
                long now = SystemClock.uptimeMillis();
                if (tShift == OFF) tShift = ONCE;
                else if (tShift == ONCE) tShift = (now - lastShiftTap < 400 && !autoShifted) ? LOCK : OFF;
                else tShift = OFF;
                autoShifted = false;
                lastShiftTap = now;
                refresh();
                return;
            }
            case ":sym":  layer = L_SYM1;  buildKeyboard(); updateSuggestions(); return;
            case ":sym2": layer = L_SYM2;  buildKeyboard(); return;
            case ":abc":  layer = L_ALPHA; buildKeyboard(); updateAutoShift(); updateSuggestions(); return;
            case ":emoji":
                loadRecent();
                layer = L_EMOJI;
                emojiCat = recentEmoji.isEmpty() ? 0 : -1;
                buildKeyboard();
                updateSuggestions();
                return;
            case ":bksp":
                sendCombo(KeyEvent.KEYCODE_DEL, false, false, false);
                return; // saran diperbarui lewat onUpdateSelection / afterRepeat
            case ":space": handleSpace(); return;
            case ":enter":
                if (learnAllowed) {
                    learnWordBeforeCursor();
                    store.addPhrase(currentLine(before(300)));
                    scheduleSave();
                }
                lastLine = "";
                enter();
                return;
            default:
                typeText(token);
        }
    }

    private void typeText(String s) {
        boolean upper = tShift != OFF && layer == L_ALPHA;
        String out = upper ? s.toUpperCase() : s;
        if (out.length() == 1 && !isWordChar(out.charAt(0))) learnWordBeforeCursor();
        commit(out);
        if (tShift == ONCE) { tShift = OFF; autoShifted = false; refresh(); }
        updateAutoShift();
        updateSuggestions();
    }

    private void handleSpace() {
        long now = SystemClock.uptimeMillis();
        String b = before(2);
        if (now - lastSpaceTime < 600 && b.length() == 2 && b.charAt(1) == ' '
                && Character.isLetterOrDigit(b.charAt(0))) {
            // spasi dua kali = titik, seperti Gboard
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(1, 0);
            commit(". ");
            lastSpaceTime = 0;
        } else {
            learnWordBeforeCursor();
            commit(" ");
            lastSpaceTime = now;
        }
        updateAutoShift();
        updateSuggestions();
    }

    // ---------------------------------------------------------------- learning

    private void learnWordBeforeCursor() {
        if (!learnAllowed || store == null) return;
        String b = before(120);
        String w = trailingWord(b);
        if (w.length() < 2) return;
        String form = normalizeForm(w);
        store.addWord(form);
        String prev = previousWord(b.substring(0, b.length() - w.length()));
        if (prev != null) store.addBigram(prev, form);
        scheduleSave();
    }

    /** "Saya" di awal kalimat disimpan sebagai "saya"; "TrueNAS" tetap "TrueNAS". */
    private static String normalizeForm(String w) {
        for (int i = 1; i < w.length(); i++) if (Character.isUpperCase(w.charAt(i))) return w;
        return w.toLowerCase();
    }

    private void scheduleSave() {
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 3000);
    }

    // ---------------------------------------------------------------- suggestions

    private void updateSuggestions() {
        if (sugViews[0] == null) return;
        for (int i = 0; i < 3; i++) { sugValues[i] = null; sugPhrase[i] = false; }
        if (mode == MODE_TEXT && layer != L_PHRASES && layer != L_EMOJI && suggestAllowed && store != null) {
            String b = before(300);
            String cw = trailingWord(b);
            String line = currentLine(b).replaceAll("^\\s+", "");
            int slot = 0;
            if (line.length() >= 3) {
                String p = store.matchPhrase(line);
                if (p != null) { sugValues[slot] = p; sugPhrase[slot] = true; slot++; }
            }
            List<String> words;
            if (!cw.isEmpty()) words = store.completions(cw, 3);
            else words = b.endsWith(" ") ? store.predictions(previousWord(b), 3) : new ArrayList<>();
            boolean cap = (!cw.isEmpty() && Character.isUpperCase(cw.charAt(0)))
                    || (cw.isEmpty() && tShift != OFF);
            for (String w : words) {
                if (slot >= 3) break;
                sugValues[slot++] = cap ? capitalize(w) : w;
            }
        }
        for (int i = 0; i < 3; i++) {
            TextView v = sugViews[i];
            if (sugValues[i] == null) { v.setText(""); continue; }
            v.setText(sugPhrase[i] ? "“" + sugValues[i] + "”" : sugValues[i]);
            v.setTextColor(sugPhrase[i] ? C_ACCENT : Color.WHITE);
        }
    }

    private void applySuggestion(int i) {
        String s = sugValues[i];
        if (s == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String b = before(300);
        if (sugPhrase[i]) {
            String line = currentLine(b).replaceAll("^\\s+", "");
            ic.deleteSurroundingText(line.length(), 0);
            commit(s);
            if (learnAllowed) { store.addPhrase(s); scheduleSave(); }
        } else {
            String cw = trailingWord(b);
            ic.deleteSurroundingText(cw.length(), 0);
            commit(s);
            learnWordBeforeCursor();
            commit(" ");
            lastSpaceTime = 0;
        }
        if (tShift == ONCE) { tShift = OFF; autoShifted = false; refresh(); }
        updateAutoShift();
        updateSuggestions();
    }

    private void forgetSuggestion(int i) {
        String s = sugValues[i];
        if (s == null) return;
        store.remove(s);
        store.remove(s.toLowerCase());
        scheduleSave();
        toast("“" + s + "” dihapus dari saran");
        updateSuggestions();
    }

    private void updateAutoShift() {
        if (mode != MODE_TEXT || layer != L_ALPHA || tShift == LOCK) return;
        if (!autoCap) {
            if (tShift == ONCE && autoShifted) { tShift = OFF; autoShifted = false; refresh(); }
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null) return;
        boolean want = ic.getCursorCapsMode(ei.inputType) != 0;
        if (want && tShift == OFF) { tShift = ONCE; autoShifted = true; refresh(); }
        else if (!want && tShift == ONCE && autoShifted) { tShift = OFF; autoShifted = false; refresh(); }
    }

    // ---------------------------------------------------------------- text helpers

    private String before(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextBeforeCursor(n, 0);
        return cs == null ? "" : cs.toString();
    }

    private String after(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextAfterCursor(n, 0);
        return cs == null ? "" : cs.toString();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '-' || c == '_';
    }

    private static String trailingWord(String s) {
        int i = s.length();
        while (i > 0 && isWordChar(s.charAt(i - 1))) i--;
        return s.substring(i);
    }

    /** Kata terakhir sebelum spasi di akhir {@code s}; null jika dipisah tanda baca. */
    private static String previousWord(String s) {
        int j = s.length();
        while (j > 0 && s.charAt(j - 1) == ' ') j--;
        if (j == s.length() || j == 0) return null;
        String w = trailingWord(s.substring(0, j));
        return w.length() < 1 ? null : w;
    }

    private static String currentLine(String s) {
        int i = s.lastIndexOf('\n');
        return i < 0 ? s : s.substring(i + 1);
    }

    private static String capitalize(String w) {
        return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    // ================================================================ CTRL input

    private void pressCtrl(String token, boolean fromRepeat) {
        switch (token) {
            case ":textmode":  switchMode(MODE_TEXT); return;
            case ":copy":      sendCombo(KeyEvent.KEYCODE_INSERT, true, false, false); return;
            case ":paste":     sendCombo(KeyEvent.KEYCODE_INSERT, false, true, false); return;
            case ":ctrlc":     sendCombo(KeyEvent.KEYCODE_C, true, false, false); return;
            case ":pastetext": typeClipboard(); return;

            case ":ctrl":  ctrl = next(ctrl);   refresh(); return;
            case ":shift": shift = next(shift); refresh(); return;
            case ":alt":   alt = next(alt);     refresh(); return;
            case ":sym":   symMode = !symMode;  buildKeyboard(); return;

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
            case ":enter": sendKey(KeyEvent.KEYCODE_ENTER); break;
            case ":space":
                if (anyMod()) sendKey(KeyEvent.KEYCODE_SPACE);
                else commit(" ");
                break;
            default:
                ctrlTypeChar(token);
        }
        if (!fromRepeat) consumeOnce();
    }

    private void ctrlTypeChar(String ch) {
        char c = ch.charAt(0);
        if (ctrl != OFF || alt != OFF) {
            int code = keyCodeFor(c);
            if (code != KeyEvent.KEYCODE_UNKNOWN) {
                sendKey(code);
                return;
            }
        }
        commit(shift != OFF ? shifted(ch) : ch);
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

    // ================================================================ shared output

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null
                && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendCombo(KeyEvent.KEYCODE_ENTER, false, false, false);
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private void sendKey(int code) {
        sendCombo(code, ctrl != OFF, shift != OFF, alt != OFF);
    }

    /**
     * Kirim kombinasi seperti keyboard fisik: modifier ditekan dulu, lalu tombol,
     * lalu semuanya dilepas. Terminal berbasis web (xterm.js di TrueNAS) butuh urutan ini.
     */
    private void sendCombo(int code, boolean withCtrl, boolean withShift, boolean withAlt) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int[] mods = new int[3];
        int[] modMeta = new int[3];
        int n = 0, running = 0;
        if (withCtrl)  { running |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;   mods[n] = KeyEvent.KEYCODE_CTRL_LEFT;  modMeta[n++] = running; }
        if (withShift) { running |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON; mods[n] = KeyEvent.KEYCODE_SHIFT_LEFT; modMeta[n++] = running; }
        if (withAlt)   { running |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;     mods[n] = KeyEvent.KEYCODE_ALT_LEFT;   modMeta[n++] = running; }
        int meta = running;

        for (int i = 0; i < n; i++) raw(ic, KeyEvent.ACTION_DOWN, mods[i], modMeta[i]);
        raw(ic, KeyEvent.ACTION_DOWN, code, meta);
        raw(ic, KeyEvent.ACTION_UP, code, meta);
        for (int i = n - 1; i >= 0; i--) {
            int afterMeta = (i == 0) ? 0 : modMeta[i - 1];
            raw(ic, KeyEvent.ACTION_UP, mods[i], afterMeta);
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
        ClipData clip = (cm == null) ? null : cm.getPrimaryClip();
        CharSequence text = (clip == null || clip.getItemCount() == 0) ? null : clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            toast("Clipboard kosong");
            return;
        }
        commit(text.toString());
    }

    // ================================================================ visuals

    private void refresh() {
        if (root == null) return;
        if (mode == MODE_CTRL) {
            paintMod(":ctrl", ctrl);
            paintMod(":shift", shift);
            paintMod(":alt", alt);
            for (KeyInfo k : charKeys) k.v.setText(shift != OFF ? shifted(k.base) : k.base);
        } else {
            paintMod(":shift", tShift);
            TextView s = modKeys.get(":shift");
            if (s != null) s.setText(tShift == LOCK ? "⇪" : "⇧");
            boolean up = tShift != OFF && layer == L_ALPHA;
            for (KeyInfo k : charKeys) k.v.setText(up ? k.base.toUpperCase() : k.base);
        }
    }

    private CharSequence keyLabel(String base, String hint) {
        if (hint == null) return base;
        SpannableString ss = new SpannableString(hint + "\n" + base);
        ss.setSpan(new RelativeSizeSpan(0.5f), 0, hint.length(), 0);
        ss.setSpan(new ForegroundColorSpan(C_HINT), 0, hint.length(), 0);
        return ss;
    }

    private void paintMod(String token, int state) {
        TextView k = modKeys.get(token);
        if (k == null) return;
        int color = state == ONCE ? C_ONCE : (state == LOCK ? C_LOCK : C_SPECIAL);
        k.setBackground(bg(color));
    }

    private GradientDrawable bg(int color) {
        return bg(color, dp(8));
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
