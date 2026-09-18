package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Locale;
import java.util.UUID;

public class OfaIdentity {
    private static final String PREFS_NAME = "ofa_identity_prefs";
    private static final String PREF_GLOBAL_OFA_ID = "global_ofa_id";
    private static final String PREF_SUDAH_DILOCK = "id_sudah_dikunci";

    // =============================================
    // ✅ AMBIL ID — TETAP, TIDAK PERNAH BERUBAH
    // =============================================
    public static String getGlobalOfaId(Context context) {
        if (context == null) return null;
        
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Kalau SUDAH ADA → langsung kembalikan yang lama
        if (sp.getBoolean(PREF_SUDAH_DILOCK, false)) {
            return sp.getString(PREF_GLOBAL_OFA_ID, null);
        }
        
        // Belum ada → BUAT BARU SEKALI SAJA
        String idBaru = buatIdBaru();
        
        // SIMPAN & KUNCI — TIDAK AKAN BERUBAH LAGI
        sp.edit()
            .putString(PREF_GLOBAL_OFA_ID, idBaru)
            .putBoolean(PREF_SUDAH_DILOCK, true)
            .apply();
        
        return idBaru;
    }

    // =============================================
    // ✅ BUAT ID BARU — HANYA DIPANGGIL SEKALI
    // =============================================
    private static String buatIdBaru() {
        // Pakai 6 angka acak + huruf → contoh: OFA-782XK
        String acak = UUID.randomUUID().toString()
            .replaceAll("[^A-Z0-9]", "")
            .toUpperCase(Locale.ROOT)
            .substring(0, 5);
        
        return "OFA-" + acak;
    }

    // =============================================
    // ✅ AMBIL ID DARI USER LAIN (tampilan sementara)
    // =============================================
    public static String ambilIdDariUser(int userId) {
        // Untuk orang lain → hitung dari nomor user
        return "OFA-" + (Math.abs((userId * 7591 + userId * 31)) % 90000 + 10000);
    }

    // =============================================
    // ✅ CEK: SUDAH DIKUNCI BELUM?
    // =============================================
    public static boolean sudahDikunci(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_SUDAH_DILOCK, false);
    }

    // =============================================
    // ✅ PAKSA SET ID — KHUSUS UNTUK KAMU (Pemilik)
    // =============================================
    public static void paksaSetIdKhusus(Context context, String idPemilik) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_GLOBAL_OFA_ID, idPemilik)
            .putBoolean(PREF_SUDAH_DILOCK, true)
            .apply();
    }
}
