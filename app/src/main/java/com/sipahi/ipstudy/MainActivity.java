package com.sipahi.ipstudy;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int EXPORT_REQUEST = 701;
    private static final String PREFS = "ipstudy_prefs";
    private static final String P_SESSION = "active_session";
    private static final String P_MODE = "active_mode";
    private static final String P_CARD = "current_card";
    private static final long DAY = 24L * 60L * 60L * 1000L;

    private Db db;
    private SharedPreferences prefs;
    private Card currentCard;
    private String currentMode = "Karma";
    private String currentSessionId = "";
    private long shownAtElapsed = 0L;
    private String pendingExport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new Db(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(28, 28, 30));
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(52));
        return b;
    }

    private LinearLayout rootColumn() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 248, 246));
        return root;
    }

    private void gap(LinearLayout parent, int heightDp) {
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void showHome() {
        currentCard = null;
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = rootColumn();
        scroll.addView(root);

        TextView title = text("Marka + Patent", 29, true);
        root.addView(title);
        TextView subtitle = text("Vekillik sınavı çalışma sistemi", 15, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle);
        gap(root, 22);

        long resumeCard = prefs.getLong(P_CARD, -1L);
        String resumeSession = prefs.getString(P_SESSION, "");
        if (resumeCard > 0 && resumeSession != null && !resumeSession.isEmpty() && db.getCard(resumeCard) != null) {
            TextView h = text("Devam Et", 17, true);
            root.addView(h);
            gap(root, 7);
            Button cont = button(prefs.getString(P_MODE, "Karma") + " · kaldığın kart");
            cont.setOnClickListener(v -> resumeSession());
            root.addView(cont, match());
            gap(root, 20);
        }

        TextView today = text("Bugün", 17, true);
        root.addView(today);
        gap(root, 6);
        TextView due = text(db.todaySummary(), 15, false);
        root.addView(due);
        gap(root, 22);

        TextView study = text("Çalış", 17, true);
        root.addView(study);
        gap(root, 8);

        addModeButton(root, "Karma", "Geçmiş sınav ağırlıkları + SRS önceliği");
        addModeButton(root, "Patent", "Patent ve faydalı model");
        addModeButton(root, "Marka", "Marka");
        addModeButton(root, "Tasarım", "Tasarım");
        addModeButton(root, "Coğrafi İşaret", "Coğrafi işaret / geleneksel ürün");
        addModeButton(root, "Vekillik", "TÜRKPATENT / vekillik");
        addModeButton(root, "Uluslararası", "PCT / EPC / Madrid");
        addModeButton(root, "Genel Hukuk", "TMK / TBK / TTK");

        gap(root, 22);
        TextView tools = text("Veri", 17, true);
        root.addView(tools);
        gap(root, 8);

        Button stats = button("İstatistikler");
        stats.setOnClickListener(v -> showStats());
        root.addView(stats, match());
        gap(root, 8);

        Button export = button("Zayıf Yönleri .txt Olarak Dışa Aktar");
        export.setOnClickListener(v -> exportReport());
        root.addView(export, match());

        gap(root, 18);
        TextView foot = text("Tüm çalışma geçmişi yalnızca bu cihazdaki SQLite veritabanında saklanır.", 12, false);
        foot.setTextColor(Color.GRAY);
        root.addView(foot);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void addModeButton(LinearLayout root, String mode, String detail) {
        Button b = button(mode + "\n" + detail);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), dp(8), dp(12), dp(8));
        b.setOnClickListener(v -> startSession(mode));
        root.addView(b, match());
        gap(root, 7);
    }

    private void startSession(String mode) {
        String old = prefs.getString(P_SESSION, "");
        if (old != null && !old.isEmpty()) db.closeSession(old);

        currentMode = mode;
        currentSessionId = UUID.randomUUID().toString();
        db.openSession(currentSessionId, mode);
        Card first = db.nextCard(mode, -1L);
        if (first == null) {
            Toast.makeText(this, "Bu modda henüz kart yok.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putString(P_SESSION, currentSessionId)
                .putString(P_MODE, mode)
                .putLong(P_CARD, first.id)
                .apply();
        showStudy(first, false);
    }

    private void resumeSession() {
        currentSessionId = prefs.getString(P_SESSION, "");
        currentMode = prefs.getString(P_MODE, "Karma");
        long id = prefs.getLong(P_CARD, -1L);
        Card c = db.getCard(id);
        if (c == null) {
            startSession(currentMode);
            return;
        }
        showStudy(c, false);
    }

    private void showStudy(Card card, boolean revealed) {
        currentCard = card;
        shownAtElapsed = SystemClock.elapsedRealtime();
        prefs.edit().putLong(P_CARD, card.id).apply();
        db.touchSession(currentSessionId, card.id);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = rootColumn();
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        Button home = button("‹");
        home.setMinWidth(dp(52));
        home.setOnClickListener(v -> showHome());
        top.addView(home, new LinearLayout.LayoutParams(dp(58), dp(52)));
        TextView meta = text(card.subject + "  ·  " + card.topic, 13, true);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(10), 0, 0, 0);
        top.addView(meta, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(top, match());

        gap(root, 18);
        TextView qLabel = text("SORU", 12, true);
        qLabel.setTextColor(Color.GRAY);
        root.addView(qLabel);
        gap(root, 8);

        TextView question = text(card.question, 21, false);
        question.setTextIsSelectable(true);
        root.addView(question);
        gap(root, 26);

        if (!revealed) {
            Button reveal = button("Cevabı Göster");
            reveal.setOnClickListener(v -> revealCurrent());
            root.addView(reveal, match());
        } else {
            addAnswer(root, card);
        }

        setContentView(scroll);
    }

    private void revealCurrent() {
        if (currentCard == null) return;
        long spent = Math.max(0, SystemClock.elapsedRealtime() - shownAtElapsed);
        db.setPendingResponseMs(currentSessionId, currentCard.id, spent);
        showStudy(currentCard, true);
    }

    private void addAnswer(LinearLayout root, Card card) {
        TextView aLabel = text("CEVAP", 12, true);
        aLabel.setTextColor(Color.GRAY);
        root.addView(aLabel);
        gap(root, 6);
        TextView answer = text(card.answer, 22, true);
        answer.setTextIsSelectable(true);
        root.addView(answer);

        if (card.basis != null && !card.basis.trim().isEmpty()) {
            gap(root, 22);
            TextView dLabel = text("DAYANAK", 12, true);
            dLabel.setTextColor(Color.GRAY);
            root.addView(dLabel);
            gap(root, 5);
            TextView basis = text(card.basis, 15, false);
            basis.setTextIsSelectable(true);
            root.addView(basis);
        }

        if (card.note != null && !card.note.trim().isEmpty()) {
            gap(root, 20);
            TextView nLabel = text("BİLGİ NOTU", 12, true);
            nLabel.setTextColor(Color.GRAY);
            root.addView(nLabel);
            gap(root, 5);
            TextView note = text(card.note, 16, false);
            note.setTextIsSelectable(true);
            root.addView(note);
        }

        gap(root, 28);
        LinearLayout ratings = new LinearLayout(this);
        ratings.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Tekrar", "Zor", "İyi", "Kolay"};
        int[] values = {1, 2, 3, 4};
        for (int i = 0; i < names.length; i++) {
            final int rating = values[i];
            Button b = button(names[i]);
            b.setTextSize(13);
            b.setOnClickListener(v -> rate(rating));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
            if (i > 0) lp.setMarginStart(dp(5));
            ratings.addView(b, lp);
        }
        root.addView(ratings, match());

        ReviewState state = db.getState(card.id);
        gap(root, 8);
        String interval = state == null ? "Yeni kart" : "Mevcut aralık: " + humanInterval(state.intervalDays);
        TextView info = text(interval, 11, false);
        info.setTextColor(Color.GRAY);
        root.addView(info);
    }

    private String humanInterval(int d) {
        if (d <= 0) return "< 1 gün";
        if (d == 1) return "1 gün";
        return d + " gün";
    }

    private void rate(int rating) {
        if (currentCard == null) return;
        long response = db.consumePendingResponseMs(currentSessionId, currentCard.id);
        db.review(currentCard.id, rating, response, currentSessionId, currentMode);
        Card next = db.nextCard(currentMode, currentCard.id);
        if (next == null) {
            Toast.makeText(this, "Bu moddaki kartlar tamamlandı.", Toast.LENGTH_SHORT).show();
            showHome();
            return;
        }
        prefs.edit().putLong(P_CARD, next.id).apply();
        showStudy(next, false);
    }

    private void showStats() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = rootColumn();
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        Button back = button("‹");
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(58), dp(52)));
        TextView title = text("İstatistikler", 25, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(top);

        gap(root, 20);
        Map<String, String> summary = db.statSummary();
        for (Map.Entry<String, String> e : summary.entrySet()) {
            TextView k = text(e.getKey(), 12, true);
            k.setTextColor(Color.GRAY);
            root.addView(k);
            TextView v = text(e.getValue(), 21, true);
            root.addView(v);
            gap(root, 16);
        }

        TextView weakTitle = text("Zayıf Konular", 17, true);
        root.addView(weakTitle);
        gap(root, 8);
        List<TopicStat> weak = db.weakTopics(8);
        if (weak.isEmpty()) {
            root.addView(text("Henüz yeterli review verisi yok.", 14, false));
        } else {
            int rank = 1;
            for (TopicStat s : weak) {
                String line = rank + ". " + s.subject + " > " + s.topic +
                        "\nMastery: %" + Math.round(s.mastery * 100.0) +
                        "  ·  Review: " + s.count +
                        "  ·  Exam risk: " + String.format(Locale.US, "%.1f", s.risk);
                TextView t = text(line, 15, false);
                root.addView(t);
                gap(root, 12);
                rank++;
            }
        }

        setContentView(scroll);
    }

    private void exportReport() {
        pendingExport = db.buildTextReport();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        i.putExtra(Intent.EXTRA_TITLE, "marka-patent-calisma-analizi-" + date + ".txt");
        startActivityForResult(i, EXPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Analiz raporu kaydedildi.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Kaydetme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        showHome();
    }

    static class Card {
        long id;
        String subject;
        String topic;
        String subtopic;
        String question;
        String answer;
        String basis;
        String note;
        int weight;
    }

    static class ReviewState {
        int intervalDays;
        long dueAt;
        int repetitions;
        int lapses;
        int lastRating;
    }

    static class TopicStat {
        String subject;
        String topic;
        int count;
        double mastery;
        double risk;
    }

    static class Db extends SQLiteOpenHelper {
        private static final String DB = "ipstudy.db";
        private final Context context;
        private final SharedPreferences temp;

        Db(Context context) {
            super(context, DB, null, 1);
            this.context = context;
            this.temp = context.getSharedPreferences("ipstudy_temp", Context.MODE_PRIVATE);
            getWritableDatabase();
            seedIfEmpty();
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE cards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "subject TEXT NOT NULL, topic TEXT NOT NULL, subtopic TEXT," +
                    "question TEXT NOT NULL, answer TEXT NOT NULL, basis TEXT, note TEXT," +
                    "exam_weight INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)");

            d.execSQL("CREATE TABLE review_state (" +
                    "card_id INTEGER PRIMARY KEY, interval_days INTEGER NOT NULL DEFAULT 0," +
                    "due_at INTEGER NOT NULL DEFAULT 0, repetitions INTEGER NOT NULL DEFAULT 0," +
                    "lapses INTEGER NOT NULL DEFAULT 0, last_rating INTEGER NOT NULL DEFAULT 0," +
                    "last_reviewed INTEGER NOT NULL DEFAULT 0)");

            d.execSQL("CREATE TABLE reviews (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, card_id INTEGER NOT NULL," +
                    "ts INTEGER NOT NULL, rating INTEGER NOT NULL, old_interval INTEGER NOT NULL," +
                    "new_interval INTEGER NOT NULL, response_ms INTEGER NOT NULL," +
                    "session_id TEXT NOT NULL, mode TEXT NOT NULL)");

            d.execSQL("CREATE INDEX idx_reviews_card ON reviews(card_id)");
            d.execSQL("CREATE INDEX idx_reviews_ts ON reviews(ts)");

            d.execSQL("CREATE TABLE sessions (" +
                    "id TEXT PRIMARY KEY, mode TEXT NOT NULL, started_at INTEGER NOT NULL," +
                    "ended_at INTEGER NOT NULL DEFAULT 0, reviewed_count INTEGER NOT NULL DEFAULT 0," +
                    "last_card_id INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

        private void seedIfEmpty() {
            SQLiteDatabase d = getWritableDatabase();
            Cursor c = d.rawQuery("SELECT COUNT(*) FROM cards", null);
            boolean empty = true;
            if (c.moveToFirst()) empty = c.getInt(0) == 0;
            c.close();
            if (!empty) return;

            String[][] seeds = {
                    {"Patent","Rüçhan","Genel","DEMO — Patent / Rüçhan kartı. İçerik paketi bir sonraki adımda gerçek kaynaklardan üretilecektir.","İçerik paketi bekleniyor.","—","Bu kart yalnızca uygulamanın SRS, oturum ve istatistik akışını test etmek içindir.","10"},
                    {"Patent","Patentlenebilirlik","Yenilik","DEMO — Patentlenebilirlik / yenilik kartı.","İçerik paketi bekleniyor.","—","Gerçek soru, cevap, dayanak ve bilgi notu sonraki veri paketinde yüklenecektir.","10"},
                    {"Patent","Çalışan Buluşları","Genel","DEMO — Çalışan buluşları kartı.","İçerik paketi bekleniyor.","—","Demo veri.","7"},
                    {"Patent","Faydalı Model","Genel","DEMO — Faydalı model kartı.","İçerik paketi bekleniyor.","—","Demo veri.","7"},
                    {"Marka","Mutlak Ret","SMK 5","DEMO — Marka / mutlak ret kartı.","İçerik paketi bekleniyor.","—","Demo veri.","10"},
                    {"Marka","Nispi Ret","SMK 6","DEMO — Marka / nispi ret kartı.","İçerik paketi bekleniyor.","—","Demo veri.","10"},
                    {"Marka","Kullanım İspatı","Genel","DEMO — Marka / kullanım ispatı kartı.","İçerik paketi bekleniyor.","—","Demo veri.","8"},
                    {"Marka","İptal ve Hükümsüzlük","Genel","DEMO — Marka / iptal ve hükümsüzlük kartı.","İçerik paketi bekleniyor.","—","Demo veri.","8"},
                    {"Tasarım","Koruma Şartları","Genel","DEMO — Tasarım kartı.","İçerik paketi bekleniyor.","—","Demo veri.","5"},
                    {"Coğrafi İşaret","Tescil","Genel","DEMO — Coğrafi işaret kartı.","İçerik paketi bekleniyor.","—","Demo veri.","4"},
                    {"Vekillik","Meslek Kuralları","Genel","DEMO — TÜRKPATENT vekillik meslek kuralları kartı.","İçerik paketi bekleniyor.","—","Demo veri.","5"},
                    {"Vekillik","Sicil","Genel","DEMO — Vekillik sicili kartı.","İçerik paketi bekleniyor.","—","Demo veri.","5"},
                    {"Uluslararası","PCT","Genel","DEMO — PCT kartı.","İçerik paketi bekleniyor.","—","Demo veri.","7"},
                    {"Uluslararası","EPC","Genel","DEMO — EPC kartı.","İçerik paketi bekleniyor.","—","Demo veri.","7"},
                    {"Uluslararası","Madrid","Genel","DEMO — Madrid sistemi kartı.","İçerik paketi bekleniyor.","—","Demo veri.","6"},
                    {"Genel Hukuk","TMK","Başlangıç","DEMO — TMK başlangıç hükümleri kartı.","İçerik paketi bekleniyor.","—","Demo veri.","4"},
                    {"Genel Hukuk","TBK","Vekâlet","DEMO — TBK vekâlet kartı.","İçerik paketi bekleniyor.","—","Demo veri.","4"},
                    {"Genel Hukuk","TTK","Tacir","DEMO — TTK tacir kartı.","İçerik paketi bekleniyor.","—","Demo veri.","4"}
            };
            d.beginTransaction();
            try {
                for (String[] s : seeds) {
                    ContentValues v = new ContentValues();
                    v.put("subject", s[0]);
                    v.put("topic", s[1]);
                    v.put("subtopic", s[2]);
                    v.put("question", s[3]);
                    v.put("answer", s[4]);
                    v.put("basis", s[5]);
                    v.put("note", s[6]);
                    v.put("exam_weight", Integer.parseInt(s[7]));
                    v.put("created_at", System.currentTimeMillis());
                    d.insert("cards", null, v);
                }
                d.setTransactionSuccessful();
            } finally {
                d.endTransaction();
            }
        }

        Card getCard(long id) {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,subject,topic,subtopic,question,answer,basis,note,exam_weight FROM cards WHERE id=?",
                    new String[]{String.valueOf(id)});
            Card card = null;
            if (c.moveToFirst()) card = readCard(c);
            c.close();
            return card;
        }

        Card nextCard(String mode, long excludeId) {
            long now = System.currentTimeMillis();
            String where = "";
            List<String> args = new ArrayList<>();
            args.add(String.valueOf(now));

            if (!"Karma".equals(mode)) {
                where += " WHERE c.subject=? ";
                args.add(mode);
                if (excludeId > 0) {
                    where += " AND c.id<>? ";
                    args.add(String.valueOf(excludeId));
                }
            } else if (excludeId > 0) {
                where += " WHERE c.id<>? ";
                args.add(String.valueOf(excludeId));
            }

            String sql = "SELECT c.id,c.subject,c.topic,c.subtopic,c.question,c.answer,c.basis,c.note,c.exam_weight " +
                    "FROM cards c LEFT JOIN review_state s ON s.card_id=c.id " +
                    where +
                    " ORDER BY CASE WHEN s.card_id IS NULL THEN 0 WHEN s.due_at<=? THEN 1 ELSE 2 END," +
                    " COALESCE(s.due_at,0), c.exam_weight DESC, c.id ASC LIMIT 1";

            // SQLite placeholder order follows SQL: where placeholders appear before ORDER BY placeholder.
            List<String> ordered = new ArrayList<>();
            if (!"Karma".equals(mode)) {
                ordered.add(mode);
                if (excludeId > 0) ordered.add(String.valueOf(excludeId));
            } else if (excludeId > 0) {
                ordered.add(String.valueOf(excludeId));
            }
            ordered.add(String.valueOf(now));

            Cursor c = getReadableDatabase().rawQuery(sql, ordered.toArray(new String[0]));
            Card card = null;
            if (c.moveToFirst()) card = readCard(c);
            c.close();

            if (card == null && excludeId > 0) {
                return nextCard(mode, -1L);
            }
            return card;
        }

        private Card readCard(Cursor c) {
            Card x = new Card();
            x.id = c.getLong(0);
            x.subject = c.getString(1);
            x.topic = c.getString(2);
            x.subtopic = c.getString(3);
            x.question = c.getString(4);
            x.answer = c.getString(5);
            x.basis = c.getString(6);
            x.note = c.getString(7);
            x.weight = c.getInt(8);
            return x;
        }

        ReviewState getState(long cardId) {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT interval_days,due_at,repetitions,lapses,last_rating FROM review_state WHERE card_id=?",
                    new String[]{String.valueOf(cardId)});
            ReviewState s = null;
            if (c.moveToFirst()) {
                s = new ReviewState();
                s.intervalDays = c.getInt(0);
                s.dueAt = c.getLong(1);
                s.repetitions = c.getInt(2);
                s.lapses = c.getInt(3);
                s.lastRating = c.getInt(4);
            }
            c.close();
            return s;
        }

        void review(long cardId, int rating, long responseMs, String sessionId, String mode) {
            SQLiteDatabase d = getWritableDatabase();
            ReviewState old = getState(cardId);
            int oldInterval = old == null ? 0 : old.intervalDays;
            int newInterval;
            long due;
            int reps = old == null ? 0 : old.repetitions;
            int lapses = old == null ? 0 : old.lapses;
            long now = System.currentTimeMillis();

            if (rating == 1) {
                newInterval = 0;
                due = now + 10L * 60L * 1000L;
                lapses++;
            } else if (rating == 2) {
                newInterval = Math.max(1, (int)Math.ceil(Math.max(1, oldInterval) * 1.2));
                due = now + newInterval * DAY;
            } else if (rating == 3) {
                newInterval = oldInterval <= 0 ? 1 : Math.max(oldInterval + 1, (int)Math.ceil(oldInterval * 2.2));
                due = now + newInterval * DAY;
            } else {
                newInterval = oldInterval <= 0 ? 4 : Math.max(oldInterval + 2, (int)Math.ceil(oldInterval * 3.5));
                due = now + newInterval * DAY;
            }
            reps++;

            d.beginTransaction();
            try {
                ContentValues state = new ContentValues();
                state.put("card_id", cardId);
                state.put("interval_days", newInterval);
                state.put("due_at", due);
                state.put("repetitions", reps);
                state.put("lapses", lapses);
                state.put("last_rating", rating);
                state.put("last_reviewed", now);
                d.insertWithOnConflict("review_state", null, state, SQLiteDatabase.CONFLICT_REPLACE);

                ContentValues r = new ContentValues();
                r.put("card_id", cardId);
                r.put("ts", now);
                r.put("rating", rating);
                r.put("old_interval", oldInterval);
                r.put("new_interval", newInterval);
                r.put("response_ms", Math.max(0, responseMs));
                r.put("session_id", sessionId == null ? "" : sessionId);
                r.put("mode", mode == null ? "" : mode);
                d.insert("reviews", null, r);

                d.execSQL("UPDATE sessions SET reviewed_count=reviewed_count+1,last_card_id=? WHERE id=?",
                        new Object[]{cardId, sessionId});
                d.setTransactionSuccessful();
            } finally {
                d.endTransaction();
            }
        }

        void openSession(String id, String mode) {
            ContentValues v = new ContentValues();
            v.put("id", id);
            v.put("mode", mode);
            v.put("started_at", System.currentTimeMillis());
            v.put("active", 1);
            getWritableDatabase().insert("sessions", null, v);
        }

        void touchSession(String id, long cardId) {
            if (id == null || id.isEmpty()) return;
            getWritableDatabase().execSQL("UPDATE sessions SET last_card_id=? WHERE id=?", new Object[]{cardId, id});
        }

        void closeSession(String id) {
            if (id == null || id.isEmpty()) return;
            getWritableDatabase().execSQL("UPDATE sessions SET ended_at=?,active=0 WHERE id=?",
                    new Object[]{System.currentTimeMillis(), id});
        }

        void setPendingResponseMs(String session, long card, long ms) {
            temp.edit().putLong("r_" + session + "_" + card, ms).apply();
        }

        long consumePendingResponseMs(String session, long card) {
            String key = "r_" + session + "_" + card;
            long v = temp.getLong(key, 0L);
            temp.edit().remove(key).apply();
            return v;
        }

        String todaySummary() {
            long now = System.currentTimeMillis();
            long start = now - (now % DAY);
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*),COALESCE(SUM(response_ms),0) FROM reviews WHERE ts>=?",
                    new String[]{String.valueOf(start)});
            int count = 0;
            long ms = 0;
            if (c.moveToFirst()) {
                count = c.getInt(0);
                ms = c.getLong(1);
            }
            c.close();

            Cursor due = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM review_state WHERE due_at<=?",
                    new String[]{String.valueOf(now)});
            int dueCount = 0;
            if (due.moveToFirst()) dueCount = due.getInt(0);
            due.close();
            return count + " review · " + formatDuration(ms) + " · " + dueCount + " due kart";
        }

        Map<String, String> statSummary() {
            Map<String, String> out = new LinkedHashMap<>();
            SQLiteDatabase d = getReadableDatabase();

            Cursor c = d.rawQuery("SELECT COUNT(*),COUNT(DISTINCT card_id),COALESCE(SUM(response_ms),0) FROM reviews", null);
            int reviews = 0, cards = 0;
            long ms = 0;
            if (c.moveToFirst()) {
                reviews = c.getInt(0);
                cards = c.getInt(1);
                ms = c.getLong(2);
            }
            c.close();

            Cursor r = d.rawQuery("SELECT " +
                    "SUM(CASE WHEN rating=1 THEN 1 ELSE 0 END)," +
                    "SUM(CASE WHEN rating=2 THEN 1 ELSE 0 END)," +
                    "SUM(CASE WHEN rating=3 THEN 1 ELSE 0 END)," +
                    "SUM(CASE WHEN rating=4 THEN 1 ELSE 0 END) FROM reviews", null);
            int a=0,h=0,g=0,e=0;
            if (r.moveToFirst()) {
                a = r.getInt(0); h = r.getInt(1); g = r.getInt(2); e = r.getInt(3);
            }
            r.close();

            long week = System.currentTimeMillis() - 7L * DAY;
            Cursor w = d.rawQuery("SELECT COUNT(*) FROM reviews WHERE ts>=?", new String[]{String.valueOf(week)});
            int last7 = w.moveToFirst() ? w.getInt(0) : 0;
            w.close();

            double retention = reviews == 0 ? 0 : (g + e) * 100.0 / reviews;
            out.put("Toplam review", String.valueOf(reviews));
            out.put("Çalışılmış kart", String.valueOf(cards));
            out.put("Toplam aktif süre", formatDuration(ms));
            out.put("Son 7 gün", last7 + " review");
            out.put("Retention (İyi + Kolay)", "%" + Math.round(retention));
            out.put("Tekrar / Zor / İyi / Kolay", a + " / " + h + " / " + g + " / " + e);
            return out;
        }

        List<TopicStat> weakTopics(int limit) {
            List<TopicStat> list = new ArrayList<>();
            String sql = "SELECT c.subject,c.topic,COUNT(r.id) n," +
                    "AVG(CASE r.rating WHEN 1 THEN 0.0 WHEN 2 THEN 0.45 WHEN 3 THEN 0.75 ELSE 1.0 END) mastery," +
                    "AVG(c.exam_weight) wt " +
                    "FROM reviews r JOIN cards c ON c.id=r.card_id " +
                    "GROUP BY c.subject,c.topic HAVING n>=1 " +
                    "ORDER BY (AVG(c.exam_weight)*(1.0-mastery)) DESC, n DESC LIMIT ?";
            Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                TopicStat s = new TopicStat();
                s.subject = c.getString(0);
                s.topic = c.getString(1);
                s.count = c.getInt(2);
                s.mastery = c.getDouble(3);
                double wt = c.getDouble(4);
                s.risk = wt * (1.0 - s.mastery);
                list.add(s);
            }
            c.close();
            return list;
        }

        String buildTextReport() {
            StringBuilder b = new StringBuilder();
            b.append("MARKA + PATENT ÇALIŞMA ANALİZİ\n");
            b.append("Tarih: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("tr","TR")).format(new Date())).append("\n\n");

            b.append("GENEL\n");
            for (Map.Entry<String,String> e : statSummary().entrySet()) {
                b.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }

            b.append("\nEN ZAYIF / YÜKSEK SINAV RİSKLİ ALANLAR\n");
            List<TopicStat> weak = weakTopics(12);
            if (weak.isEmpty()) {
                b.append("Henüz yeterli review verisi yok.\n");
            } else {
                int i=1;
                for (TopicStat s : weak) {
                    b.append(i++).append(". ").append(s.subject).append(" > ").append(s.topic).append("\n");
                    b.append("Mastery: %").append(Math.round(s.mastery*100.0)).append("\n");
                    b.append("Review sayısı: ").append(s.count).append("\n");
                    b.append("Exam risk: ").append(String.format(Locale.US,"%.2f",s.risk)).append("\n\n");
                }
            }

            b.append("SORUNLU KARTLAR\n");
            String sql = "SELECT c.id,c.subject,c.topic,c.question," +
                    "SUM(CASE WHEN r.rating=1 THEN 1 ELSE 0 END) again_n," +
                    "SUM(CASE WHEN r.rating=2 THEN 1 ELSE 0 END) hard_n," +
                    "COUNT(r.id) n FROM reviews r JOIN cards c ON c.id=r.card_id " +
                    "GROUP BY c.id ORDER BY (again_n*2+hard_n) DESC,n DESC LIMIT 20";
            Cursor c = getReadableDatabase().rawQuery(sql,null);
            while(c.moveToNext()) {
                b.append("#").append(c.getLong(0)).append(" · ")
                        .append(c.getString(1)).append(" > ").append(c.getString(2)).append("\n");
                b.append("Tekrar: ").append(c.getInt(4)).append(" · Zor: ").append(c.getInt(5))
                        .append(" · Toplam: ").append(c.getInt(6)).append("\n");
                b.append("Soru: ").append(c.getString(3)).append("\n\n");
            }
            c.close();

            b.append("LLM İÇİN NOT\n");
            b.append("Bu raporu kullanarak konu önceliği, tekrar planı ve yeni soru üretim hedefleri oluştur. ");
            b.append("Exam risk değeri, kartların sınav ağırlığı ile kullanıcının review performansını birlikte yansıtır.\n");
            return b.toString();
        }

        static String formatDuration(long ms) {
            long totalSec = ms / 1000L;
            long h = totalSec / 3600L;
            long m = (totalSec % 3600L) / 60L;
            long s = totalSec % 60L;
            if (h > 0) return h + "s " + m + "dk";
            if (m > 0) return m + "dk " + s + "sn";
            return s + "sn";
        }
    }
}
