package com.wazesound.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private Prefs prefs;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> runOnUiThread(() -> this.lastUsage.setText("סוג השמע האחרון שזוהה: " + prefs.lastUsage()));
    private TextView levelText, dndStatus, lastUsage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        root.addView(text("Waze Speaker", 26, true));
        root.addView(text("דיבור של Waze תמיד ברמקול הטלפון, בעוצמה שתבחר - גם ברטט, גם עם בלוטוס.", 15, false));

        Switch enable = sw("הפעל", prefs.enabled(), (v, on) -> {
            prefs.enabled(on);
            if (on) startService(); else stopService(new Intent(this, SoundService.class));
        });
        root.addView(withInfo(enable, "הפעל",
                "מדליק את האפליקציה. כשהיא פועלת היא מזהה מתי Waze מדברת ומתאימה את השמע. בהתראה בסרגל העליון תראה שהיא פעילה."));

        levelText = text("", 18, true);
        levelText.setPadding(0, dp(24), 0, 0);
        root.addView(withInfo(levelText, "עוצמת דיבור",
                "העוצמה שאליה תעלה האפליקציה את השמע בזמן שהדיבור של Waze מתנגן. 100% הכי חזק. אם זה חזק מדי, בחר ערך נמוך יותר, למשל 80%."));
        SeekBar seek = new SeekBar(this);
        seek.setMax(9); // 0..9 -> 10%..100%
        seek.setProgress(Math.max(0, prefs.level() / 10 - 1));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                prefs.level((v + 1) * 10);
                updateLevel();
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        root.addView(seek);

        root.addView(withInfo(sw("לנתב לרמקול הטלפון גם כשמחובר בלוטוס/אוזניות", prefs.forceSpeaker(),
                (v, on) -> prefs.forceSpeaker(on)), "ניתוב לרמקול הטלפון",
                "כשמחוברים לבלוטוס של הרכב או לאוזניות, הדיבור של Waze יישמע ברמקול של הטלפון ולא שם. בזמן הדיבור כל השמע עובר לרמקול, ולכן מומלץ להשאיר דלוקה גם את השהיית המוזיקה. אם כבוי, הדיבור יישמע בהתקן המחובר."));
        root.addView(withInfo(sw("להשהות מוזיקה/יוטיוב בזמן הדיבור ולהמשיך אחריו", prefs.pauseMusic(),
                (v, on) -> prefs.pauseMusic(on)), "השהיית מוזיקה",
                "כשמנגן שיר או סרטון והדיבור של Waze מתחיל, האפליקציה עוצרת אותם, ואחרי שהדיבור נגמר היא ממשיכה לנגן. כך השיר לא יישמע ברמקול הטלפון בזמן ההנחיה."));
        root.addView(withInfo(sw("להוציא מרטט/שקט בזמן הדיבור (ולהחזיר אחריו)", prefs.leaveVibrate(),
                (v, on) -> prefs.leaveVibrate(on)), "יציאה מרטט/שקט",
                "אם הטלפון על רטט או שקט, הוא עובר זמנית למצב רגיל בזמן הדיבור, ואחרי הדיבור חוזר למצב הקודם. נדרשת גישה ל\"נא לא להפריע\". אם הדיבור נשמע גם בלי זה, אפשר לכבות את האפשרות ולהשאיר את הטלפון על רטט כל הזמן."));
        root.addView(withInfo(sw("להחזיר עוצמות ומצב צלצול אחרי הדיבור", prefs.restore(),
                (v, on) -> prefs.restore(on)), "החזרת עוצמות",
                "אחרי הדיבור מחזירה את עוצמות המדיה, השיחה, ההתראות והאזעקות לערכים שהיו לפני. כבוי כברירת מחדל, כלומר העוצמות נשארות כפי שהאפליקציה קבעה אותן."));

        dndStatus = text("", 14, false);
        dndStatus.setPadding(0, dp(24), 0, 0);
        root.addView(dndStatus);
        Button dnd = new Button(this);
        dnd.setText("אפשר גישה ל'נא לא להפריע' (נדרש לרטט/שקט)");
        dnd.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        root.addView(withInfo(dnd, "גישה ל'נא לא להפריע'",
                "הרשאה של אנדרואיד שנדרשת כדי שהאפליקציה תוכל להוציא את הטלפון ממצב רטט או שקט. בלעדיה הדיבור לא יישמע כשהטלפון על רטט. לוחצים, מוצאים את Waze Speaker ברשימה ומפעילים."));

        Button test = new Button(this);
        test.setText("בדיקה: נגן צליל ניווט");
        test.setOnClickListener(v -> {
            if (!prefs.enabled()) { prefs.enabled(true); enable.setChecked(true); }
            playTestTone();
        });
        root.addView(withInfo(test, "בדיקה",
                "מנגן כמה צפצופים שהמערכת מזהה כדיבור ניווט, כדי לבדוק שהאפליקציה עובדת בלי צורך לנווט. אפשר לנסות את זה על רטט או עם בלוטוס מחובר."));

        lastUsage = text("", 13, false);
        lastUsage.setTextColor(Color.GRAY);
        lastUsage.setPadding(0, dp(16), 0, 0);
        root.addView(lastUsage);

        TextView credit = text("נוצר ע\"י Oz Levi", 14, true);
        credit.setTextColor(Color.parseColor("#0A66C2"));
        credit.setPaintFlags(credit.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        credit.setPadding(0, dp(32), 0, dp(16));
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.linkedin.com/in/ozlevi1/"))));
        root.addView(credit);

        ScrollView sv = new ScrollView(this);
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
        dndStatus.setText(ok ? "גישה ל'נא לא להפריע': ניתנה ✓"
                : "גישה ל'נא לא להפריע': לא ניתנה ✗ (במצב רטט לא ישמע)");
        lastUsage.setText("סוג השמע האחרון שזוהה: " + prefs.lastUsage());
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.unlisten(prefListener);
    }

    private void updateLevel() {
        levelText.setText("עוצמת דיבור: " + prefs.level() + "%");
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

    /** Wraps a view with an (i) icon on its side that opens an explanation dialog. */
    private View withInfo(View main, String title, String info) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(main, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView i = new TextView(this);
        i.setText("ⓘ");
        i.setTextSize(24);
        i.setTextColor(Color.parseColor("#0A66C2"));
        i.setPadding(dp(12), dp(8), dp(4), dp(8));
        i.setContentDescription("הסבר");
        i.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle(title).setMessage(info).setPositiveButton("הבנתי", null).show());
        row.addView(i);
        return row;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Switch sw(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(16);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, dp(12), 0, dp(12));
        return s;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
