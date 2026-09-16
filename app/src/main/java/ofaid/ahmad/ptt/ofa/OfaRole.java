/*Edit By Ofaid/Ahmd-jr 16-9-2026 — Sistem Peran & Label*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;

public class OfaRole {
    private static final String PREFS_NAME = "ofa_roles_prefs";

    public static final int ROLE_NONE = 0;
    public static final int ROLE_WARGA = 1;
    public static final int ROLE_LURAH = 2;
    public static final int ROLE_PEMIMPIN_CH = 3;

    public static void setPeranUser(Context context, String ofaId, int peran, String channelId) {
        if (context == null || ofaId == null || ofaId.trim().isEmpty()) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putInt("peran_" + ofaId, peran)
                .putString("channel_" + ofaId, channelId)
                .apply();
    }

    public static int getPeranUser(Context context, String ofaId) {
        if (context == null || ofaId == null) return ROLE_NONE;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("peran_" + ofaId, ROLE_NONE);
    }

    public static String getChannelUser(Context context, String ofaId) {
        if (context == null || ofaId == null) return "";
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("channel_" + ofaId, "");
    }

    public static void hapusPeranUser(Context context, String ofaId) {
        if (context == null || ofaId == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .remove("peran_" + ofaId)
                .remove("channel_" + ofaId)
                .apply();
    }

    public static String getNamaPeran(int peran) {
        switch (peran) {
            case ROLE_WARGA:       return "Warga";
            case ROLE_LURAH:       return "Lurah";
            case ROLE_PEMIMPIN_CH: return "Pemimpin CH";
            default:               return "";
        }
    }
}
