# Ctrl Keyboard (Android)

Keyboard Android dengan baris tombol ekstra: **Esc, Tab, Ctrl, Alt, Ins, Del, Home, End, PgUp, PgDn**,
plus **Shift** dan tombol panah ← ↑ ↓ →.

## Tata letak
```
Esc  Tab  Ctrl  Alt  Ins  Del  Home  End  PgUp  PgDn
1    2    3     4    5    6    7     8    9     0
q    w    e     r    t    y    u     i    o     p
a    s    d     f    g    h    j     k    l
⇧Shift  z  x  c  v  b  n  m  ⌫
?123  ,     spasi     .  ←  ↑  ↓  →  ⏎
```
`?123` membuka layer simbol (! @ # - _ = [ ] { } \ | ; : ' " ` ~ < > / ?).

## Cara pakai modifier
- Tap 1x → aktif sekali (biru), mati setelah tombol berikutnya
- Tap 2x → terkunci (oranye)
- Tap 3x → mati

Contoh: `Ctrl` → `C` = salin, `Ctrl` → `V` = tempel, `Ctrl` → `A` = pilih semua,
`Shift` → `Ins` = tempel (di terminal seperti Termux), `Shift` + panah = blok teks.
Backspace, Del, panah, dan spasi bisa ditahan untuk mengulang.

## Build
**Cara A – GitHub Actions (tanpa PC):** upload folder ini ke repo GitHub. Workflow
`.github/workflows/build.yml` otomatis mem-build. Buka tab *Actions* → run terbaru →
unduh artifact `CtrlKeyboard-apk` → ekstrak → instal `app-debug.apk`.

**Cara B – Android Studio:** File → Open → pilih folder ini → Run / Build APK.

## Instal & aktifkan
1. Instal APK (izinkan "instal dari sumber tidak dikenal").
2. Buka aplikasi **Ctrl Keyboard** → *Aktifkan keyboard* → nyalakan.
3. *Pilih keyboard* → pilih Ctrl Keyboard.
