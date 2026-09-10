/*
 * Copyright (C) 2026 — OFA Status Tambahan
 * Berkas BARU — TIDAK MENGUBAH kode asli/library! 🛡️
 */
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;

public class OfaUserStatus {
    // ==============================================
    // ✅ DAFTAR PILIHAN STATUS — TETAP & AMAN
    // ==============================================
    public static final String[] KODE = {
        "siap", "sibuk", "bekerja", "istirahat", "tidakada", "jangan_ganggu", "aktif"
    };

    public static final String[] TAMPILAN = {
        "🟢 Siap / Tersedia",
        "🟡 Sedang Sibuk",
        "🔵 Sedang Bekerja",
        "🟣 Sedang Istirahat / Makan",
        "⚪ Tidak Ada / Keluar Sebentar",
        "🔴 Sibuk — Jangan Diganggu",
        "✅ Siap Berbicara / Aktif"
    };

    // Penyimpanan lokal — pakai SharedPreferences, TIDAK perlu ubah database/model inti!
    private static final String PREFS_NAMA = "OfaStatusPrefs";
    private static final String KUNCI_STATUS = "user_status_";

    // ==============================================
    // ✅ AMBIL & SIMPAN — TIDAK SENTUH MODEL/USER ASLI!
    // ==============================================
    public static String dapatStatus(Context konteks, int idPengguna) {
        SharedPreferences prefs = konteks.getSharedPreferences(PREFS_NAMA, Context.MODE_PRIVATE);
        String kode = prefs.getString(KUNCI_STATUS + idPengguna, "siap");
        return ubahKeTampilan(kode);
    }

    public static String dapatKodeStatus(Context konteks, int idPengguna) {
        SharedPreferences prefs = konteks.getSharedPreferences(PREFS_NAMA, Context.MODE_PRIVATE);
        return prefs.getString(KUNCI_STATUS + idPengguna, "siap");
    }

    public static void simpanStatus(Context konteks, int idPengguna, String kodeStatus) {
        if (kodeStatus == null || kodeStatus.isEmpty()) return;
        
        konteks.getSharedPreferences(PREFS_NAMA, Context.MODE_PRIVATE)
            .edit()
            .putString(KUNCI_STATUS + idPengguna, kodeStatus)
            .apply();
    }

    // ==============================================
    // ✅ UBAH KODE KE TAMPILAN YANG DAPAT DIBACA
    // ==============================================
    public static String ubahKeTampilan(String kode) {
        if (kode == null || kode.isEmpty()) kode = "siap";
        for (int i = 0; i < KODE.length; i++) {
            if (KODE[i].equals(kode)) return TAMPILAN[i];
        }
        return TAMPILAN[0]; // aman — kembali ke siap kalau tidak dikenali
    }

    // Cari posisi untuk daftar pilihan
    public static int cariPosisi(String kode) {
        if (kode == null || kode.isEmpty()) return 0;
        for (int i = 0; i < KODE.length; i++) {
            if (KODE[i].equals(kode)) return i;
        }
        return 0;
    }
    
    // ==============================================
    // ✅ TAMBAHAN: HAPUS / RESET STATUS
    // ==============================================
    public static void hapusStatus(Context konteks, int idPengguna) {
        konteks.getSharedPreferences(PREFS_NAMA, Context.MODE_PRIVATE)
            .edit()
            .remove(KUNCI_STATUS + idPengguna)
            .apply();
    }
    
    public static void resetSemuaStatus(Context konteks) {
        konteks.getSharedPreferences(PREFS_NAMA, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
