package com.wazesound.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("waze_sound", Context.MODE_PRIVATE);
    }

    boolean enabled() { return sp.getBoolean("enabled", false); }
    void enabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    /** Target volume in percent, 10..100. */
    int level() { return sp.getInt("level", 100); }
    void level(int v) { sp.edit().putInt("level", v).apply(); }

    /** Force phone speaker even when Bluetooth / headset is connected. */
    boolean forceSpeaker() { return sp.getBoolean("speaker", true); }
    void forceSpeaker(boolean v) { sp.edit().putBoolean("speaker", v).apply(); }

    /** Restore previous volumes / ringer mode after the voice prompt ends. */
    boolean restore() { return sp.getBoolean("restore", false); }
    void restore(boolean v) { sp.edit().putBoolean("restore", v).apply(); }

    void listen(SharedPreferences.OnSharedPreferenceChangeListener l) { sp.registerOnSharedPreferenceChangeListener(l); }
    void unlisten(SharedPreferences.OnSharedPreferenceChangeListener l) { sp.unregisterOnSharedPreferenceChangeListener(l); }

    String lastUsage() { return sp.getString("last_usage", "-"); }
    void lastUsage(String s) { sp.edit().putString("last_usage", s).apply(); }
}
