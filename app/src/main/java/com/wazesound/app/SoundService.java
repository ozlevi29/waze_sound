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
    private static final String CHANNEL = "waze_sound";
    private static final int NOTIF_ID = 1;
    private static final long RESTORE_DELAY_MS = 1000;

    private AudioManager am;
    private NotificationManager nm;
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean boosted;
    private int savedMusic, savedVoice, savedNotif, savedAlarm, savedRinger, savedMode;
    private boolean usedCommunicationRoute;

    private final Runnable restoreTask = this::restoreAll;

    private final AudioManager.AudioPlaybackCallback callback = new AudioManager.AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            boolean nav = false;
            StringBuilder seen = new StringBuilder();
            for (AudioPlaybackConfiguration c : configs) {
                int usage = c.getAudioAttributes().getUsage();
                if (seen.length() > 0) seen.append(", ");
                seen.append(usageName(usage));
                if (usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) nav = true;
            }
            if (seen.length() > 0) prefs.lastUsage(seen.toString());

            if (nav) {
                handler.removeCallbacks(restoreTask);
                if (!boosted) boost();
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

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Waze Speaker", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
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

    private void boost() {
        boosted = true;
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
        setVolume(AudioManager.STREAM_MUSIC, pct);
        setVolume(AudioManager.STREAM_VOICE_CALL, pct);
        setVolume(AudioManager.STREAM_NOTIFICATION, pct);
        setVolume(AudioManager.STREAM_ALARM, pct);
    }

    private void restoreAll() {
        if (!boosted) return;
        boosted = false;

        if (usedCommunicationRoute) {
            usedCommunicationRoute = false;
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else am.setSpeakerphoneOn(false);
            am.setMode(savedMode);
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
