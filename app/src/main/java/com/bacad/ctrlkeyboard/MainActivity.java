package com.bacad.ctrlkeyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Layar pengaturan sederhana: aktifkan keyboard, pilih keyboard, dan kolom uji coba. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);

        TextView info = new TextView(this);
        info.setText("Ctrl Keyboard\n\n"
                + "1. Tekan 'Aktifkan keyboard', lalu nyalakan Ctrl Keyboard.\n"
                + "2. Tekan 'Pilih keyboard', lalu pilih Ctrl Keyboard.\n"
                + "3. Coba ketik di kolom bawah.\n\n"
                + "Ctrl / Shift / Alt: tap 1x = sekali (biru), 2x = kunci (oranye), 3x = mati.\n"
                + "Contoh: Ctrl lalu C = salin, Ctrl lalu V = tempel, Shift lalu Ins = tempel (di terminal).");
        box.addView(info);

        Button enable = new Button(this);
        enable.setText("Aktifkan keyboard");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        box.addView(enable);

        Button pick = new Button(this);
        pick.setText("Pilih keyboard");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        box.addView(pick);

        EditText test = new EditText(this);
        test.setHint("Ketik di sini untuk mencoba...");
        test.setMinLines(4);
        box.addView(test);

        setContentView(box);
    }
}
