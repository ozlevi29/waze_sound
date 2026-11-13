package com.wazesound.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int PRIMARY = Color.parseColor("#1A73E8");
    private static final int PRIMARY_DARK = Color.parseColor("#0B4FB3");
    private static final int BG = Color.parseColor("#F2F5F9");
    private static final int TEXT = Color.parseColor("#1F2933");
    private static final int MUTED = Color.parseColor("#6B7785");
    private static final int GREEN = Color.parseColor("#1E8E3E");
    private static final int ORANGE = Color.parseColor("#E37400");

    private Prefs prefs;
    private TextView levelText, dndStatus, lastUsage;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> runOnUiThread(() -> this.lastUsage.setText("סוג השמע האחרון שזוהה: " + prefs.lastUsage()));

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(PRIMARY_DARK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);

        // ---- Header ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(28), dp(24), dp(36));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{PRIMARY_DARK, PRIMARY});
        float r = dp(32);
        hg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        header.setBackground(hg);
        TextView title = text("🔊  Waze Speaker", 28, true, Color.WHITE);
        TextView sub = text("הדיבור של Waze תמיד ברמקול הטלפון, בעוצמה שתבחר - גם ברטט וגם עם בלוטוס.",
                15, false, Color.parseColor("#DCE8FB"));
        sub.setPadding(0, dp(8), 0, 0);
        header.addView(title);
        header.addView(sub);
        root.addView(header);

        // ---- Enable card ----
        LinearLayout enableCard = card();
        Switch enable = new Switch(this);
        tint(enable);
        enable.setChecked(prefs.enabled());
        enable.setOnCheckedChangeListener((v, on) -> {
            prefs.enabled(on);
            if (on) startService(); else stopService(new Intent(this, SoundService.class));
        });
        enableCard.addView(row(text("הפעל", 20, true, TEXT), enable, "הפעל",
                "מדליק את האפליקציה. כשהיא פועלת היא מזהה מתי Waze מדברת ומתאימה את השמע. בהתראה בסרגל העליון תראה שהיא פעילה."));
        root.addView(enableCard);

        // ---- Volume card ----
        LinearLayout volCard = card();
        TextView volLabel = text("עוצמת דיבור", 16, true, TEXT);
        volCard.addView(row(volLabel, null, "עוצמת דיבור",
                "העוצמה שאליה תעלה האפליקציה את השמע בזמן שהדיבור של Waze מתנגן. 100% הכי חזק. אם זה חזק מדי, בחר ערך נמוך יותר, למשל 80%."));
        levelText = text("", 48, true, PRIMARY);
        levelText.setGravity(Gravity.CENTER);
        volCard.addView(levelText);
        SeekBar seek = new SeekBar(this);
        seek.setMax(9); // 0..9 -> 10%..100%
        seek.setProgress(Math.max(0, prefs.level() / 10 - 1));
        seek.setProgressTintList(ColorStateList.valueOf(PRIMARY));
        seek.setThumbTintList(ColorStateList.valueOf(PRIMARY));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#C9D6EA")));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                prefs.level((v + 1) * 10);
                updateLevel();
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        volCard.addView(seek);
        root.addView(volCard);

        // ---- Options card ----
        LinearLayout opts = card();
        opts.addView(option("לנתב לרמקול הטלפון גם עם בלוטוס/אוזניות", prefs.forceSpeaker(),
                (v, on) -> prefs.forceSpeaker(on), "ניתוב לרמקול הטלפון",
                "כשמחוברים לבלוטוס של הרכב או לאוזניות, הדיבור של Waze יישמע ברמקול של הטלפון ולא שם. בזמן הדיבור כל השמע עובר לרמקול, ולכן מומלץ להשאיר דלוקה גם את השהיית המוזיקה. אם כבוי, הדיבור יישמע בהתקן המחובר."));
        opts.addView(divider());
        opts.addView(option("להשהות מוזיקה/יוטיוב בזמן הדיבור", prefs.pauseMusic(),
                (v, on) -> prefs.pauseMusic(on), "השהיית מוזיקה",
                "כשמנגן שיר או סרטון והדיבור של Waze מתחיל, האפליקציה עוצרת אותם, ואחרי שהדיבור נגמר היא ממשיכה לנגן. כך השיר לא יישמע ברמקול הטלפון בזמן ההנחיה."));
        opts.addView(divider());
        opts.addView(option("להוציא מרטט/שקט בזמן הדיבור", prefs.leaveVibrate(),
                (v, on) -> prefs.leaveVibrate(on), "יציאה מרטט/שקט",
                "אם הטלפון על רטט או שקט, הוא עובר זמנית למצב רגיל בזמן הדיבור, ואחרי הדיבור חוזר למצב הקודם. נדרשת גישה ל\"נא לא להפריע\". אם הדיבור נשמע גם בלי זה, אפשר לכבות את האפשרות ולהשאיר את הטלפון על רטט כל הזמן."));
        opts.addView(divider());
        opts.addView(option("להחזיר עוצמות אחרי הדיבור", prefs.restore(),
                (v, on) -> prefs.restore(on), "החזרת עוצמות",
                "אחרי הדיבור מחזירה את עוצמות המדיה, השיחה, ההתראות והאזעקות לערכים שהיו לפני. כבוי כברירת מחדל, כלומר העוצמות נשארות כפי שהאפליקציה קבעה אותן."));
        root.addView(opts);

        // ---- Permission card ----
        LinearLayout perm = card();
        dndStatus = text("", 13, true, Color.WHITE);
        dndStatus.setPadding(dp(12), dp(4), dp(12), dp(4));
        perm.addView(row(text("גישה ל'נא לא להפריע'", 16, true, TEXT), dndStatus,
                "גישה ל'נא לא להפריע'",
                "הרשאה של אנדרואיד שנדרשת כדי שהאפליקציה תוכל להוציא את הטלפון ממצב רטט או שקט. בלעדיה הדיבור לא יישמע כשהטלפון על רטט. לוחצים, מוצאים את Waze Speaker ברשימה ומפעילים."));
        Button dnd = button("פתח הגדרות הרשאה", false);
        dnd.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        perm.addView(dnd);
        root.addView(perm);

        // ---- Test card ----
        LinearLayout testCard = card();
        Button test = button("🔔  בדיקה: נגן צליל ניווט", true);
        test.setOnClickListener(v -> {
            if (!prefs.enabled()) { prefs.enabled(true); enable.setChecked(true); }
            playTestTone();
        });
        testCard.addView(row(test, null, "בדיקה",
                "מנגן כמה צפצופים שהמערכת מזהה כדיבור ניווט, כדי לבדוק שהאפליקציה עובדת בלי צורך לנווט. אפשר לנסות את זה על רטט או עם בלוטוס מחובר."));
        lastUsage = text("", 13, false, MUTED);
        lastUsage.setGravity(Gravity.CENTER);
        lastUsage.setPadding(0, dp(10), 0, 0);
        testCard.addView(lastUsage);
        root.addView(testCard);

        // ---- Credit ----
        TextView credit = text("נוצר ע\"י Oz Levi", 14, true, PRIMARY);
        credit.setGravity(Gravity.CENTER);
        credit.setPaintFlags(credit.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        credit.setPadding(0, dp(24), 0, dp(32));
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.linkedin.com/in/ozlevi1/"))));
        root.addView(credit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (prefs.enabled()) startService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLevel();
        prefs.listen(prefListener);
        boolean ok = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
        dndStatus.setText(ok ? "ניתנה ✓" : "לא ניתנה");
        dndStatus.setBackground(pill(ok ? GREEN : ORANGE));
        lastUsage.setText("סוג השמע האחרון שזוהה: " + prefs.lastUsage());
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.unlisten(prefListener);
    }

    private void updateLevel() {
        levelText.setText(prefs.level() + "%");
    }

    private void startService() {
        startForegroundService(new Intent(this, SoundService.class));
    }

    /** Plays a few beeps flagged as navigation guidance, like Waze's voice prompts. */
    private void playTestTone() {
        new Thread(() -> {
            int rate = 22050, n = rate * 2;
            short[] buf = new short[n];
            for (int i = 0; i < n; i++) {
                boolean on = (i / (rate / 4)) % 2 == 0;
                buf[i] = on ? (short) (Math.sin(2 * Math.PI * 660 * i / rate) * 12000) : 0;
            }
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
            t.write(buf, 0, n);
            t.play();
            try { Thread.sleep(2300); } catch (InterruptedException ignored) { }
            t.release();
        }).start();
    }

    // ---------- UI helpers ----------

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(20));
        c.setBackground(g);
        c.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(14), dp(16), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /** Row: main view (fills width) + (i) info icon + optional trailing view. */
    private View row(View main, View trailing, String infoTitle, String info) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(infoIcon(infoTitle, info));
        if (trailing != null) r.addView(trailing);
        return r;
    }

    private View option(String label, boolean checked, CompoundButton.OnCheckedChangeListener l,
                        String infoTitle, String info) {
        Switch s = new Switch(this);
        tint(s);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(l);
        TextView t = text(label, 16, false, TEXT);
        t.setOnClickListener(v -> s.toggle());
        View r = row(t, s, infoTitle, info);
        r.setPadding(0, dp(8), 0, dp(8));
        return r;
    }

    private TextView infoIcon(String title, String info) {
        TextView i = new TextView(this);
        i.setText("ⓘ");
        i.setTextSize(22);
        i.setTextColor(PRIMARY);
        i.setPadding(dp(10), dp(6), dp(10), dp(6));
        i.setContentDescription("הסבר");
        i.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(title).setMessage(info).setPositiveButton("הבנתי", null).show());
        return i;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#E8EDF3"));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private Button button(String label, boolean filled) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (filled) {
            g.setColor(PRIMARY);
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.parseColor("#E8F0FE"));
            b.setTextColor(PRIMARY);
        }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(20));
        return g;
    }

    private void tint(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{PRIMARY, Color.parseColor("#F1F3F4")}));
        s.setTrackTintList(new ColorStateList(states,
                new int[]{Color.parseColor("#9CC1F5"), Color.parseColor("#C4CAD1")}));
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
