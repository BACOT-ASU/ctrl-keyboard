package com.bacad.ctrlkeyboard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menyimpan kata, pasangan kata (untuk tebakan kata berikutnya) dan kalimat
 * yang sering diketik. Semua data hanya disimpan di HP (file learn.json di
 * folder privat aplikasi), tidak dikirim ke mana pun.
 */
public class LearnStore {

    /** Kalimat baru muncul sebagai saran setelah diketik sebanyak ini. */
    static final int PHRASE_MIN_USES = 2;

    private static final Set<String> SEED = new HashSet<>(Arrays.asList(SeedWords.WORDS));

    private final File file;
    private long loadedStamp = -1;

    private final Map<String, Integer> words = new HashMap<>();
    private final Map<String, String> forms = new HashMap<>();
    private final Map<String, Map<String, Integer>> bigrams = new HashMap<>();
    private final Map<String, Integer> phrases = new HashMap<>();
    private final Set<String> pinned = new LinkedHashSet<>();

    public LearnStore(Context c) {
        file = new File(c.getFilesDir(), "learn.json");
        load();
    }

    // ------------------------------------------------------------ persist

    public synchronized void load() {
        words.clear(); forms.clear(); bigrams.clear(); phrases.clear(); pinned.clear();
        if (!file.exists()) { loadedStamp = 0; return; }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            JSONObject o = new JSONObject(new String(buf, 0, off, StandardCharsets.UTF_8));
            readCounts(o.optJSONObject("words"), words);
            readCounts(o.optJSONObject("phrases"), phrases);
            JSONObject f = o.optJSONObject("forms");
            if (f != null) for (Iterator<String> it = f.keys(); it.hasNext(); ) {
                String k = it.next(); forms.put(k, f.optString(k));
            }
            JSONObject b = o.optJSONObject("bigrams");
            if (b != null) for (Iterator<String> it = b.keys(); it.hasNext(); ) {
                String k = it.next();
                Map<String, Integer> m = new HashMap<>();
                readCounts(b.optJSONObject(k), m);
                bigrams.put(k, m);
            }
            JSONArray p = o.optJSONArray("pinned");
            if (p != null) for (int i = 0; i < p.length(); i++) pinned.add(p.optString(i));
        } catch (Exception ignored) {
        }
        loadedStamp = file.lastModified();
    }

    private static void readCounts(JSONObject o, Map<String, Integer> into) {
        if (o == null) return;
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next(); into.put(k, o.optInt(k));
        }
    }

    public synchronized void reloadIfChanged() {
        long m = file.exists() ? file.lastModified() : 0;
        if (m != loadedStamp) load();
    }

    public synchronized void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("words", new JSONObject(words));
            o.put("forms", new JSONObject(forms));
            o.put("phrases", new JSONObject(phrases));
            JSONObject b = new JSONObject();
            for (Map.Entry<String, Map<String, Integer>> e : bigrams.entrySet())
                b.put(e.getKey(), new JSONObject(e.getValue()));
            o.put("bigrams", b);
            o.put("pinned", new JSONArray(new ArrayList<>(pinned)));
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
            loadedStamp = file.lastModified();
        } catch (Exception ignored) {
        }
    }

    public synchronized void clearAll() {
        words.clear(); forms.clear(); bigrams.clear(); phrases.clear(); pinned.clear();
        save();
    }

    // ------------------------------------------------------------- learn

    static String key(String w) {
        return w.toLowerCase(Locale.ROOT);
    }

    private static int get(Map<String, Integer> m, String k) {
        Integer v = m.get(k);
        return v == null ? 0 : v;
    }

    public synchronized void addWord(String form) {
        if (form.length() < 2 || form.length() > 40) return;
        String k = key(form);
        words.put(k, get(words, k) + 1);
        if (!form.equals(k)) forms.put(k, form);
        if (words.size() > 8000) {
            words.values().removeIf(v -> v <= 1);
            forms.keySet().retainAll(words.keySet());
        }
    }

    public synchronized void addBigram(String prev, String form) {
        String p = key(prev), k = key(form);
        Map<String, Integer> m = bigrams.get(p);
        if (m == null) { m = new HashMap<>(); bigrams.put(p, m); }
        m.put(k, get(m, k) + 1);
        if (bigrams.size() > 6000) {
            bigrams.values().removeIf(x -> x.isEmpty() || (x.size() == 1 && x.values().iterator().next() <= 1));
        }
    }

    public synchronized void addPhrase(String p) {
        p = p.trim();
        if (p.length() < 5 || p.length() > 300 || p.indexOf(' ') < 0) return;
        phrases.put(p, get(phrases, p) + 1);
        if (phrases.size() > 400) {
            phrases.entrySet().removeIf(e -> e.getValue() <= 1 && !pinned.contains(e.getKey()));
        }
    }

    public synchronized void pin(String p) {
        p = p.trim();
        if (!p.isEmpty()) pinned.add(p);
    }

    public synchronized void remove(String s) {
        String k = key(s);
        words.remove(k);
        forms.remove(k);
        bigrams.remove(k);
        for (Map<String, Integer> m : bigrams.values()) m.remove(k);
        phrases.remove(s);
        pinned.remove(s);
    }

    // ----------------------------------------------------------- suggest

    private String display(String k) {
        String f = forms.get(k);
        return f != null ? f : k;
    }

    /** Lengkapi kata yang sedang diketik. */
    public synchronized List<String> completions(String prefix, int n) {
        String p = key(prefix);
        final Map<String, Double> score = new HashMap<>();
        for (Map.Entry<String, Integer> e : words.entrySet()) {
            String k = e.getKey();
            if (k.length() > p.length() && k.startsWith(p)) score.put(k, 10.0 * e.getValue());
        }
        for (String k : SEED) {
            if (k.length() > p.length() && k.startsWith(p) && !score.containsKey(k))
                score.put(k, 1.0 / k.length());
        }
        List<String> keys = new ArrayList<>(score.keySet());
        keys.sort((a, b) -> Double.compare(score.get(b), score.get(a)));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < keys.size() && out.size() < n; i++) out.add(display(keys.get(i)));
        return out;
    }

    /** Tebak kata berikutnya setelah kata {@code prev}. */
    public synchronized List<String> predictions(String prev, int n) {
        List<String> out = new ArrayList<>();
        if (prev == null) return out;
        final Map<String, Integer> m = bigrams.get(key(prev));
        if (m == null) return out;
        List<String> keys = new ArrayList<>(m.keySet());
        keys.sort((a, b) -> m.get(b) - m.get(a));
        for (int i = 0; i < keys.size() && out.size() < n; i++) out.add(display(keys.get(i)));
        return out;
    }

    /** Kalimat tersimpan yang diawali teks {@code line}, atau null. */
    public synchronized String matchPhrase(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = 0;
        for (String p : candidatesPhrases()) {
            if (p.length() > line.length() && p.toLowerCase(Locale.ROOT).startsWith(l)) {
                int s = (pinned.contains(p) ? 100000 : 0) + get(phrases, p);
                if (s > bestScore) { bestScore = s; best = p; }
            }
        }
        return best;
    }

    private List<String> candidatesPhrases() {
        List<String> c = new ArrayList<>(pinned);
        for (Map.Entry<String, Integer> e : phrases.entrySet())
            if (e.getValue() >= PHRASE_MIN_USES && !pinned.contains(e.getKey())) c.add(e.getKey());
        return c;
    }

    /** Daftar kalimat untuk panel ★: yang disematkan dulu, lalu yang paling sering. */
    public synchronized List<String> topPhrases(int n) {
        List<String> out = new ArrayList<>(pinned);
        List<String> rest = new ArrayList<>();
        for (Map.Entry<String, Integer> e : phrases.entrySet())
            if (e.getValue() >= PHRASE_MIN_USES && !pinned.contains(e.getKey())) rest.add(e.getKey());
        rest.sort((a, b) -> get(phrases, b) - get(phrases, a));
        out.addAll(rest);
        return out.size() > n ? out.subList(0, n) : out;
    }

    public synchronized boolean isPinned(String p) {
        return pinned.contains(p);
    }

    public synchronized int wordCount() { return words.size(); }

    public synchronized int phraseCount() { return candidatesPhrases().size(); }
}
