package tv.kaya.turksat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChannelUserData {
    static final String PREFS = "tv_settings";
    private static final String FAVORITES = "favorite_channels_v1";
    private static final String RECENT = "recent_channels_v1";
    private static final String LOCKED = "locked_channels_v1";
    private static final String PIN_SALT = "parental_pin_salt_v1";
    private static final String PIN_HASH = "parental_pin_hash_v1";
    private static final int MAX_RECENT = 20;
    private static final List<String> BACKUP_KEYS = Arrays.asList(
            "last_channel", "startup_mode", "startup_autoplay", "auto_launch_on_boot",
            "info_seconds", "aspect_mode", "quality_mode", "audio_language",
            "subtitle_language", "pip_enabled", FAVORITES, RECENT, LOCKED, PIN_SALT, PIN_HASH);

    private ChannelUserData() {
    }

    static boolean isFavorite(Context context, Channel channel) {
        return stringSet(context, FAVORITES).contains(channel.key());
    }

    static boolean toggleFavorite(Context context, Channel channel) {
        Set<String> values = stringSet(context, FAVORITES);
        boolean enabled = values.add(channel.key());
        if (!enabled) {
            values.remove(channel.key());
        }
        preferences(context).edit().putStringSet(FAVORITES, values).apply();
        return enabled;
    }

    static void recordRecent(Context context, Channel channel) {
        ArrayList<String> values = new ArrayList<>(recent(context));
        values.remove(channel.key());
        values.add(0, channel.key());
        if (values.size() > MAX_RECENT) {
            values.subList(MAX_RECENT, values.size()).clear();
        }
        preferences(context).edit().putString(RECENT, new JSONArray(values).toString()).apply();
    }

    static List<String> recent(Context context) {
        ArrayList<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences(context).getString(RECENT, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty() && !values.contains(value)) {
                    values.add(value);
                }
            }
        } catch (Exception ignored) {
            values.clear();
        }
        return values;
    }

    static boolean isLocked(Context context, Channel channel) {
        return stringSet(context, LOCKED).contains(channel.key());
    }

    static boolean toggleLocked(Context context, Channel channel) {
        Set<String> values = stringSet(context, LOCKED);
        boolean enabled = values.add(channel.key());
        if (!enabled) {
            values.remove(channel.key());
        }
        preferences(context).edit().putStringSet(LOCKED, values).apply();
        return enabled;
    }

    static boolean hasPin(Context context) {
        SharedPreferences preferences = preferences(context);
        return !preferences.getString(PIN_SALT, "").isEmpty()
                && !preferences.getString(PIN_HASH, "").isEmpty();
    }

    static boolean setPin(Context context, String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            return false;
        }
        byte[] salt = new byte[24];
        new SecureRandom().nextBytes(salt);
        byte[] hash = hashPin(pin, salt);
        preferences(context).edit()
                .putString(PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        return true;
    }

    static boolean verifyPin(Context context, String pin) {
        try {
            SharedPreferences preferences = preferences(context);
            byte[] salt = Base64.decode(preferences.getString(PIN_SALT, ""), Base64.DEFAULT);
            byte[] expected = Base64.decode(preferences.getString(PIN_HASH, ""), Base64.DEFAULT);
            return pin != null && MessageDigest.isEqual(expected, hashPin(pin, salt));
        } catch (Exception ignored) {
            return false;
        }
    }

    static String exportJson(Context context) throws Exception {
        SharedPreferences preferences = preferences(context);
        Map<String, ?> all = preferences.getAll();
        JSONObject root = new JSONObject();
        root.put("format", "turkiye-tv-settings");
        root.put("version", 1);
        JSONObject settings = new JSONObject();
        for (String key : BACKUP_KEYS) {
            Object value = all.get(key);
            if (value instanceof Set) {
                settings.put(key, new JSONArray((Set<?>) value));
            } else if (value != null) {
                settings.put(key, value);
            }
        }
        root.put("settings", settings);
        return root.toString(2);
    }

    static void importJson(Context context, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"turkiye-tv-settings".equals(root.optString("format"))) {
            throw new IllegalArgumentException("Geçersiz yedek biçimi");
        }
        JSONObject settings = root.getJSONObject("settings");
        SharedPreferences.Editor editor = preferences(context).edit();
        for (String key : BACKUP_KEYS) {
            if (!settings.has(key)) {
                continue;
            }
            Object value = settings.get(key);
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                HashSet<String> set = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    set.add(array.getString(i));
                }
                editor.putStringSet(key, set);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Number) {
                editor.putInt(key, ((Number) value).intValue());
            } else {
                editor.putString(key, String.valueOf(value));
            }
        }
        editor.apply();
    }

    private static byte[] hashPin(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (pin + ":turkiye-tv").getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 50_000; i++) {
                digest.update(salt);
                value = digest.digest(value);
                digest.reset();
            }
            return value;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Set<String> stringSet(Context context, String key) {
        return new HashSet<>(preferences(context).getStringSet(key, Collections.emptySet()));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
