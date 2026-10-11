package vv.utility.vaibhav.handynotes;

import android.content.Context;

public class ApiConfig {
    public static final String DEFAULT_SUPABASE_URL = "https://aumkwlfejrxgiflddpjo.supabase.co";
    public static final String DEFAULT_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_pe5IrJBtGw21N18ATXq11w_urUeKeht";
    public static final String DEFAULT_BASE_URL = "https://notes.craftedalcove.com";

    public static String getBaseUrl(Context context) {
        return DEFAULT_BASE_URL;
    }

    public static String getSupabaseUrl(Context context) {
        return DEFAULT_SUPABASE_URL;
    }

    public static String getSupabasePublishableKey(Context context) {
        return DEFAULT_SUPABASE_PUBLISHABLE_KEY;
    }

    public static boolean isSupabaseConfigured(Context context) {
        return !getSupabaseUrl(context).isEmpty() && !getSupabasePublishableKey(context).isEmpty();
    }

    public static String getNotesEndpointUrl(Context context) {
        String base = getBaseUrl(context);
        if (base.endsWith("/api")) {
            return base + "/note";
        }
        if (base.endsWith("/api/note") || base.endsWith("/note") || base.endsWith("/notes")) {
            return base;
        }
        return base + "/api/note";
    }

    public static String getNoteDetailEndpointUrl(Context context, String id) {
        return getNotesEndpointUrl(context) + "/" + id;
    }
}
