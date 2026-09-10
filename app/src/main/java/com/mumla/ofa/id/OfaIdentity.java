/*Edit By Ofaid/Ahmd-jr 9-9-2026*/
package com.mumla.ofa.id;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Kelas Pengelola Identitas OFA — Terpisah, Aman, Tidak Sentuh Kode Asli!
 * Menghasilkan & menyimpan kode identitas tetap untuk setiap pasangan server.
 * Dihasilkan sekali, tetap sama selamanya, tidak berubah kelaknya.
 */
public class OfaIdentity {
    private static final String PREFS_NAME = "ofa_identity_prefs";
    private static final String PREF_OFA_ID_PREFIX = "ofa_id_";
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;

    private static String generateOfaCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return "OFA" + sb.toString();
    }

    /**
     * Dapatkan kode OFA untuk server tertentu — dihasilkan sekali, tetap sama selamanya!
     * Gunakan alamat + port sebagai kunci agar tetap sama untuk server yang sama.
     * @param context Konteks aplikasi
     * @param host Alamat server (misal: 127.0.0.1 / mumble.contoh.com)
     * @param port Nomor port server (misal: 64738)
     * @return Kode OFA tetap untuk server tersebut
     */
    public static String getOrCreateForServer(Context context, String host, int port) {
        if (context == null || host == null || host.trim().isEmpty()) {
            return "OFA----";
        }
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String existing = prefs.getString(key, null);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing; // ✅ SUDAH ADA — KEMBALIKAN YANG SUDAH ADA! TETAP SAMA!
        }

        // ➡️ BELUM ADA — BUAT BARU & SIMPAN UNTUK KALI BERIKUTNYA!
        String newCode = generateOfaCode();
        prefs.edit().putString(key, newCode).apply();
        return newCode;
    }

    /**
     * Ambil kode OFA yang sudah ada — tidak buat yang baru kalau belum ada.
     * @param context Konteks aplikasi
     * @param host Alamat server
     * @param port Nomor port server
     * @return Kode OFA atau null kalau belum ada
     */
    public static String getExistingForServer(Context context, String host, int port) {
        if (context == null || host == null || host.trim().isEmpty()) return null;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null);
    }

    /**
     * Hapus kode identitas — untuk saat menghapus server dari daftar.
     * @param context Konteks aplikasi
     * @param host Alamat server
     * @param port Nomor port server
     */
    public static void removeForServer(Context context, String host, int port) {
        if (context == null || host == null) return;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(key).apply();
    }
}
