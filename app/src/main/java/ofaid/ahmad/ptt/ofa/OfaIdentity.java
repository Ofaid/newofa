/*Edit By Ofaid/Ahmd-jr 9-9-2026 — HANYA IKUTI YANG SUDAH ADA!*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public class OfaIdentity {
    private static final String PREFS_NAME = "ofa_identity_prefs";
    private static final String PREF_OFA_ID_PREFIX = "ofa_id_";

    public static void saveForServer(Context context, String host, int port, String ofaId) {
        if (context == null || host == null || host.trim().isEmpty() || ofaId == null) return;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, ofaId)
                .apply();
    }

    public static String getExistingForServer(Context context, String host, int port) {
        if (context == null || host == null || host.trim().isEmpty()) return null;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        String existing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, null);
        return (existing != null && !existing.trim().isEmpty()) ? existing : null;
    }

    public static void removeForServer(Context context, String host, int port) {
        if (context == null || host == null) return;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(key).apply();
    }
}
