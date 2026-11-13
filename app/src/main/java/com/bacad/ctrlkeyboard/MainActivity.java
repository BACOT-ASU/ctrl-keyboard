package com.bacad.ctrlkeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Layar pengaturan: aktifkan keyboard, kelola kalimat tersimpan, kolom uji coba. */
public class MainActivity extends Activity {

    private LearnStore store;
    private TextView stats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LearnStore(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);

        TextView info = new TextView(this);
        info.setText("Ctrl Keyboard\n\n"
                + "1. Tekan 'Aktifkan keyboard', lalu nyalakan Ctrl Keyboard.\n"
                + "2. Tekan 'Pilih keyboard', lalu pilih Ctrl Keyboard.\n\n"
                + "Mode Teks (ala Gboard): bar saran di atas belajar dari kata & kalimat yang sering kamu ketik. "
                + "Tahan saran untuk menghapusnya. ☆ = daftar kalimat tersimpan. Tahan huruf untuk angka/simbol. "
                + "Spasi 2x = titik. Shift 2x cepat = huruf besar terus.\n\n"
                + "Tombol '⌨ Ctrl' pindah ke mode Ctrl (Ctrl, Shift, Alt, Ins, Esc, Tab, panah, Salin ⌃Ins, Tempel ⇧Ins). "
                + "Tombol 'ABC' kembali ke mode Teks.\n\n"
                + "Data belajar hanya disimpan di HP ini. Kolom password dan mode incognito tidak dipelajari.");
        box.addView(info);

        Button enable = new Button(this);
        enable.setText("Aktifkan keyboard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(enable);

        Button pick = new Button(this);
        pick.setText("Pilih keyboard");
        pick.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        box.addView(pick);

        final android.content.SharedPreferences prefs =
                getSharedPreferences(CtrlKeyboardService.PREFS, MODE_PRIVATE);
        android.widget.Switch autoCap = new android.widget.Switch(this);
        autoCap.setText("Huruf besar otomatis di awal kalimat");
        autoCap.setChecked(prefs.getBoolean("autoCap", true));
        autoCap.setPadding(0, p, 0, 0);
        autoCap.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("autoCap", on).apply());
        box.addView(autoCap);

        stats = new TextView(this);
        stats.setPadding(0, p, 0, p / 2);
        box.addView(stats);

        final EditText phrase = new EditText(this);
        phrase.setHint("Tulis kalimat yang sering dipakai…");
        box.addView(phrase);

        Button add = new Button(this);
        add.setText("Simpan kalimat");
        add.setOnClickListener(v -> {
            String s = phrase.getText().toString().trim();
            if (s.isEmpty()) return;
            store.reloadIfChanged();
            store.pin(s);
            store.save();
            phrase.setText("");
            Toast.makeText(this, "Disimpan", Toast.LENGTH_SHORT).show();
            updateStats();
        });
        box.addView(add);

        Button clear = new Button(this);
        clear.setText("Hapus semua data belajar");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Hapus semua kata dan kalimat yang sudah dipelajari?")
                .setPositiveButton("Hapus", (d, w) -> { store.clearAll(); updateStats(); })
                .setNegativeButton("Batal", null)
                .show());
        box.addView(clear);

        EditText test = new EditText(this);
        test.setHint("Ketik di sini untuk mencoba…");
        test.setMinLines(4);
        box.addView(test);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStats();
    }

    private void updateStats() {
        store.reloadIfChanged();
        stats.setText("Sudah dipelajari: " + store.wordCount() + " kata, "
                + store.phraseCount() + " kalimat.");
    }
}
