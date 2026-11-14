package com.wazesound.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

/**
 * Watches for active navigation-guidance playback (what Waze uses for its voice prompts).
 * While a prompt plays: raises volume, leaves silent/vibrate, and (optionally) moves the
 * output to the phone speaker. Afterwards everything is put back.
 */
public class SoundService extends Service {
    static final String CHANNEL = "waze_sound_quiet";
    private static final int NOTIF_ID = 1;
    private static final long RESTORE_DELAY_MS = 1000;

    private AudioManager am;
    private NotificationManager nm;
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean boosted;
    private int savedMusic, savedVoice, savedNotif, savedAlarm, savedRinger, savedMode;
    private boolean usedCommunicationRoute;
    private boolean pausedMusic;
    /** Volumes are set once per navigation session; the user's manual changes then stay. */
    private static final long SESSION_GAP_MS = 30 * 60 * 1000;
    private long lastNavEnd; // elapsedRealtime of the end of the last prompt, 0 = none yet

    private final Runnable restoreTask = this::restoreAll;

    private final AudioManager.AudioPlaybackCallback callback = new AudioManager.AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            boolean nav = false, media = false;
            StringBuilder seen = new StringBuilder();
            for (AudioPlaybackConfiguration c : configs) {
                int usage = c.getAudioAttributes().getUsage();
                if (seen.length() > 0) seen.append(", ");
                seen.append(usageName(usage));
                if (usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) nav = true;
                if (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME) media = true;
            }
            if (seen.length() > 0) prefs.lastUsage(seen.toString());

            if (nav) {
                handler.removeCallbacks(restoreTask);
                if (!boosted) boost(media);
            } else if (boosted) {
                handler.removeCallbacks(restoreTask);
                handler.postDelayed(restoreTask, RESTORE_DELAY_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prefs = new Prefs(this);

        nm.deleteNotificationChannel("waze_sound"); // old, louder channel
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Waze Speaker פעיל ברקע", NotificationManager.IMPORTANCE_MIN);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET); // nothing on the lock screen
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentTitle("Waze Speaker פעיל")
                .setContentText("מאזין לדיבור של ניווט")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);

        am.registerAudioPlaybackCallback(callback, handler);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        am.unregisterAudioPlaybackCallback(callback);
        handler.removeCallbacks(restoreTask);
        if (boosted) restoreAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void sendMediaKey(int keyCode) {
        long t = android.os.SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, keyCode, 0));
    }

    private void boost(boolean musicPlaying) {
        boosted = true;
        boolean newSession = prefs.consumeNewSession() || lastNavEnd == 0
                || android.os.SystemClock.elapsedRealtime() - lastNavEnd > SESSION_GAP_MS;
        if (musicPlaying && prefs.pauseMusic()) {
            pausedMusic = true;
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
        savedMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        savedVoice = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        savedNotif = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        savedAlarm = am.getStreamVolume(AudioManager.STREAM_ALARM);
        savedRinger = am.getRingerMode();
        savedMode = am.getMode();

        // Leave silent / vibrate (needs "Do Not Disturb access").
        if (prefs.leaveVibrate() && savedRinger != AudioManager.RINGER_MODE_NORMAL && nm.isNotificationPolicyAccessGranted()) {
            try { am.setRingerMode(AudioManager.RINGER_MODE_NORMAL); } catch (SecurityException ignored) { }
        }

        // Route to phone speaker if something else (car Bluetooth, headset...) is the output.
        if (prefs.forceSpeaker() && externalOutputConnected()) {
            usedCommunicationRoute = true;
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31) {
                for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        am.setCommunicationDevice(d);
                        break;
                    }
                }
            } else {
                am.setSpeakerphoneOn(true);
            }
        }

        int pct = prefs.level();
        if (!newSession && !prefs.restore()) return; // keep whatever the user set during this navigation
        setVolume(AudioManager.STREAM_MUSIC, pct);
        setVolume(AudioManager.STREAM_VOICE_CALL, pct);
        if (prefs.boostNotif()) {
            setVolume(AudioManager.STREAM_NOTIFICATION, pct);
            setVolume(AudioManager.STREAM_ALARM, pct);
        }
    }

    private void restoreAll() {
        if (!boosted) return;
        boosted = false;
        lastNavEnd = android.os.SystemClock.elapsedRealtime();

        if (usedCommunicationRoute) {
            usedCommunicationRoute = false;
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else am.setSpeakerphoneOn(false);
            am.setMode(savedMode);
        }
        if (pausedMusic) {
            pausedMusic = false;
            handler.postDelayed(() -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY), 400);
        }
        // Ringer mode (e.g. vibrate) always goes back; volumes only if the user asked for it.
        if (savedRinger != am.getRingerMode() && nm.isNotificationPolicyAccessGranted()) {
            try { am.setRingerMode(savedRinger); } catch (SecurityException ignored) { }
        }
        if (!prefs.restore()) return;

        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusic, 0);
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoice, 0);
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotif, 0);
            am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarm, 0);
        } catch (SecurityException ignored) { }
    }

    private void setVolume(int stream, int pct) {
        try {
            int max = am.getStreamMaxVolume(stream);
            am.setStreamVolume(stream, Math.round(max * pct / 100f), 0);
        } catch (SecurityException ignored) {
            // Notification stream can be locked by Do Not Disturb.
        }
    }

    private boolean externalOutputConnected() {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return true;
                default:
            }
        }
        return false;
    }

    static String usageName(int u) {
        switch (u) {
            case AudioAttributes.USAGE_MEDIA: return "מדיה";
            case AudioAttributes.USAGE_VOICE_COMMUNICATION: return "שיחה";
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE: return "ניווט";
            case AudioAttributes.USAGE_NOTIFICATION: return "התראה";
            case AudioAttributes.USAGE_ALARM: return "אזעקה";
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION: return "צלילי מערכת";
            default: return "usage " + u;
        }
    }
}
