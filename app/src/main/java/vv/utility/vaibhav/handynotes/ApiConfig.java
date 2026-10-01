package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.content.SharedPreferences;

public class ApiConfig {
    public static final String DEFAULT_SUPABASE_URL = "https://aumkwlfejrxgiflddpjo.supabase.co";
    public static final String DEFAULT_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_pe5IrJBtGw21N18ATXq11w_urUeKeht";
    public static final String DEFAULT_BASE_URL = DEFAULT_SUPABASE_URL;

    private static final String PREF_NAME = "api_config_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_SUPABASE_URL = "supabase_url";
    private static final String KEY_SUPABASE_PUBLISHABLE_KEY = "supabase_publishable_key";

    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        if (url == null || url.trim().isEmpty() || url.contains("notes.craftedalcove.in")) {
            return getSupabaseUrl(context);
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static void setBaseUrl(Context context, String baseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (baseUrl != null) {
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        } else {
            baseUrl = DEFAULT_BASE_URL;
        }
        editor.putString(KEY_BASE_URL, baseUrl);
        editor.apply();
    }

    public static String getSupabaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL);
        return (url == null || url.trim().isEmpty()) ? DEFAULT_SUPABASE_URL : url.trim();
    }

    public static void setSupabaseUrl(Context context, String supabaseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SUPABASE_URL, supabaseUrl != null ? supabaseUrl.trim() : "").apply();
    }

    public static String getSupabasePublishableKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = prefs.getString(KEY_SUPABASE_PUBLISHABLE_KEY, DEFAULT_SUPABASE_PUBLISHABLE_KEY);
        return (key == null || key.trim().isEmpty()) ? DEFAULT_SUPABASE_PUBLISHABLE_KEY : key.trim();
    }

    public static void setSupabasePublishableKey(Context context, String publishableKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SUPABASE_PUBLISHABLE_KEY, publishableKey != null ? publishableKey.trim() : "")
                .remove("api_key")
                .apply();
    }

    public static boolean isSupabaseConfigured(Context context) {
        return !getSupabaseUrl(context).isEmpty() && !getSupabasePublishableKey(context).isEmpty();
    }

    public static String getNotesEndpointUrl(Context context) {
        String base = getBaseUrl(context);
        if (base.contains("supabase.co")) {
            return base + "/rest/v1/notes";
        }
        if (base.endsWith("/api")) {
            return base + "/note";
        }
        if (base.endsWith("/note") || base.endsWith("/notes")) {
            return base;
        }
        return base + "/api/note";
    }

    public static String getNoteDetailEndpointUrl(Context context, String id) {
        String base = getBaseUrl(context);
        if (base.contains("supabase.co")) {
            return base + "/rest/v1/notes?id=eq." + id;
        }
        if (base.endsWith("/api")) {
            return base + "/note/" + id;
        }
        if (base.endsWith("/note") || base.endsWith("/notes")) {
            return base + "/" + id;
        }
        return base + "/api/note/" + id;
    }
}
