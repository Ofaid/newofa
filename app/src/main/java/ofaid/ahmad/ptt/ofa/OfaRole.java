package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;

public class OfaRole {
    // === TINGKAT PERAN ===
    public static final int ROLE_TAMU = 0;
    public static final int ROLE_WARGA = 1;
    public static final int ROLE_LURAH = 2;
    public static final int ROLE_PEMIMPIN_CH = 3;
    public static final int ROLE_PEMILIK_UTAMA = 99; // 👑 HANYA KAMU

 //=== 🔒 ID PEMILIK UTAMA
private static final String ID_PEMILIK_UTAMA = "OFA-67206";

    // === PENYIMPANAN ===
    private static final String PREF_NAMA = "OfaRolePrefs";
    private static final String PREF_DAFTAR_USER = "ofa_daftar_user"; // Untuk Register Otomatis
    private static final String KUNCI_PERAN = "peran_";
    private static final String KUNCI_CHANNEL = "channel_";

    // =============================================
    // ✅ CEK: APAKAH INI PEMILIK UTAMA?
    // =============================================
    public static boolean adalahPemilikUtama(String ofaId) {
        return ID_PEMILIK_UTAMA.equals(ofaId);
    }

    // =============================================
    // ✅ CEK: SUDAH TERDAFTAR BELUM?
    // =============================================
    public static boolean sudahTerdaftar(Context ctx, String ofaId) {
        if (ctx == null || ofaId == null) return false;
        return ctx.getSharedPreferences(PREF_DAFTAR_USER, Context.MODE_PRIVATE)
                .contains(ofaId + "_nama");
    }

    // =============================================
    // ✅ DAFTARKAN OTOMATIS — SAAT USER MASUK
    // =============================================
    public static void daftarkanOtomatis(Context ctx, String ofaId, String nama) {
        if (ctx == null || ofaId == null || nama == null) return;
        if (sudahTerdaftar(ctx, ofaId)) return; // Sudah ada → lewati

        // Belum ada → simpan sebagai Tamu
        ctx.getSharedPreferences(PREF_DAFTAR_USER, Context.MODE_PRIVATE)
            .edit()
            .putString(ofaId + "_nama", nama)
            .putLong(ofaId + "_waktu_masuk", System.currentTimeMillis())
            .putInt(ofaId + "_peran_sementara", ROLE_TAMU)
            .apply();
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
        // Kalau ini Pemilik Utama → langsung kembalikan peran tertinggi
        if (adalahPemilikUtama(ofaId)) {
            return ROLE_PEMILIK_UTAMA;
        }
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAMA, Context.MODE_PRIVATE);
        return sp.getInt(KUNCI_PERAN + ofaId, ROLE_TAMU);
    }

    // =============================================
    // ✅ BACA NAMA USER DARI DAFTAR
    // =============================================
    public static String getNamaUser(Context ctx, String ofaId) {
        if (ctx == null || ofaId == null) return "";
        return ctx.getSharedPreferences(PREF_DAFTAR_USER, Context.MODE_PRIVATE)
                .getString(ofaId + "_nama", "");
    }

    // =============================================
    // ✅ NAMA PERAN UNTUK DITAMPILKAN
    // =============================================
    public static String getNamaPeran(int peran) {
        switch (peran) {
            case ROLE_PEMILIK_UTAMA:   return "👑 Pemilik";
            case ROLE_PEMIMPIN_CH:     return "Pemimpin CH";
            case ROLE_LURAH:           return "Lurah";
            case ROLE_WARGA:           return "Warga";
            default:                   return "Tamu";
        }
    }

    // =============================================
    // ✅ WARNA PERAN
    // =============================================
    public static int getWarnaPeran(int peran) {
        switch (peran) {
            case ROLE_PEMILIK_UTAMA:   return 0xFFFFD700;   // Emas
            case ROLE_PEMIMPIN_CH:     return 0xFFFF9800;   // Oranye
            case ROLE_LURAH:           return 0xFF4CAF50;   // Hijau
            case ROLE_WARGA:           return 0xFF2196F3;   // Biru
            default:                   return 0xFFBBBBBB;   // Abu-abu
        }
    }

    // =============================================
    // ✅ NAMA CHANNEL YANG DIIKUTI
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
