package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;

public class OfaRole {
    // === TINGKAT PERAN ===
    public static final int ROLE_WARGA = 1;
    public static final int ROLE_LURAH = 2;
    public static final int ROLE_PEMIMPIN_CH = 3;
    public static final int ROLE_PEMILIK_UTAMA = 99; // ✅ HANYA KAMU

    // === ID PEMILIK UTAMA — GANTI DENGAN ID-MU ===
    private static final String ID_PEMILIK_UTAMA = "OFA-10000-SU";

    private static final String PREF_NAMA = "OfaRolePrefs";
    private static final String KUNCI_PERAN = "peran_";
    private static final String KUNCI_CHANNEL = "channel_";

    // =============================================
    // ✅ CEK PEMILIK UTAMA
    // =============================================
    public static boolean adalahPemilikUtama(String ofaId) {
        return ID_PEMILIK_UTAMA.equals(ofaId);
    }

    // =============================================
    // ✅ SIMPAN PERAN
    // =============================================
    public static void setPeranUser(Context ctx, String ofaId, int peran, String channel) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAMA, Context.MODE_PRIVATE);
        sp.edit()
            .putInt(KUNCI_PERAN + ofaId, peran)
            .putString(KUNCI_CHANNEL + ofaId, channel)
            .apply();
    }

    // =============================================
    // ✅ BACA PERAN
    // =============================================
    public static int getPeranUser(Context ctx, String ofaId) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAMA, Context.MODE_PRIVATE);
        return sp.getInt(KUNCI_PERAN + ofaId, ROLE_WARGA);
    }

    // =============================================
    // ✅ NAMA PERAN
    // =============================================
    public static String getNamaPeran(int peran) {
        switch (peran) {
            case ROLE_PEMILIK_UTAMA: return "Pemilik";
            case ROLE_PEMIMPIN_CH: return "Pemimpin CH";
            case ROLE_LURAH: return "Lurah";
            case ROLE_WARGA: return "Warga";
            default: return "Tamu";
        }
    }

    // =============================================
    // ✅ WARNA PERAN — DITAMBAHKAN UNTUK PERBAIKI ERROR
    // =============================================
    public static int getWarnaPeran(int peran) {
        switch (peran) {
            case ROLE_PEMILIK_UTAMA: return 0xFFFFD700;   // Emas
            case ROLE_PEMIMPIN_CH:   return 0xFFFF9800;   // Oranye
            case ROLE_LURAH:         return 0xFF4CAF50;   // Hijau
            case ROLE_WARGA:         return 0xFF2196F3;   // Biru
            default:                 return 0xFFBBBBBB;   // Abu-abu
        }
    }

    // =============================================
    // ✅ NAMA CHANNEL
    // =============================================
    public static String getChannelUser(Context ctx, String ofaId) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAMA, Context.MODE_PRIVATE);
        return sp.getString(KUNCI_CHANNEL + ofaId, "");
    }

    // =============================================
    // ✅ HAPUS DATA PERAN
    // =============================================
    public static void hapusPeranUser(Context ctx, String ofaId) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAMA, Context.MODE_PRIVATE);
        sp.edit()
            .remove(KUNCI_PERAN + ofaId)
            .remove(KUNCI_CHANNEL + ofaId)
            .apply();
    }
}
