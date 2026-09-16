/*Edit By Ofaid/Ahmd-jr 9-9-2026 — SISTEM ID TETAP TERKUNCI*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Locale;
import java.util.UUID;

public class OfaIdentity {
    private static final String PREFS_NAME = "ofa_identity_prefs";
    private static final String PREF_OFA_ID_PREFIX = "ofa_id_";
    private static final String PREF_DEVICE_FINGERPRINT = "device_fingerprint";
    private static final String PREF_GLOBAL_OFA_ID = "global_ofa_id";
    private static final String PREF_ID_LOCKED = "id_locked"; // 🔒 KUNCI

    // === 1. SIDIK JARI PERANGKAT — TETAP SEUMUR HIDUP ===
    private static String getDeviceFingerprint(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        if (sp.contains(PREF_DEVICE_FINGERPRINT)) {
            return sp.getString(PREF_DEVICE_FINGERPRINT, null);
        }
        
        // Buat sekali, tidak pernah berubah
        String fingerprint = Build.BRAND + "_" + Build.MODEL + "_" +
                             (Build.SERIAL != null ? Build.SERIAL : "NO_SERIAL") + "_" +
                             UUID.randomUUID().toString().substring(0, 8);
        
        sp.edit().putString(PREF_DEVICE_FINGERPRINT, fingerprint).apply();
        return fingerprint;
    }

    // === 2. DAPATKAN ID UTAMA — TERKUNCI, TIDAK BISA DIUBAH ===
    public static String getGlobalOfaId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 🔒 SUDAH DIKUNCI → KEMBALIKAN YANG LAMA
        if (sp.getBoolean(PREF_ID_LOCKED, false) && sp.contains(PREF_GLOBAL_OFA_ID)) {
            return sp.getString(PREF_GLOBAL_OFA_ID, null);
        }
        
        // BELUM ADA → BUAT BARU & KUNCI SEKARANG
        String perangkat = getDeviceFingerprint(context);
        String idBaru = "OFA-" +
                        String.format(Locale.ROOT, "%05d", Math.abs(perangkat.hashCode() % 90000 + 10000)) + "-" +
                        UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        
        // 🔒 SIMPAN & KUNCI — TIDAK BISA DIUBAH LAGI
        sp.edit()
                .putString(PREF_GLOBAL_OFA_ID, idBaru)
                .putBoolean(PREF_ID_LOCKED, true) // DIKUNCI!
                .apply();
        
        return idBaru;
    }

    // === 3. ID PER SERVER — MENGIKUTI ID UTAMA YANG TERKUNCI ===
    public static void saveForServer(Context context, String host, int port, String ofaId) {
        if (context == null || host == null || host.trim().isEmpty() || ofaId == null) return;
        
        // 🔒 TIDAK BOLEH SIMPAN ID YANG BEDA DARI MILIK PERANGKAT INI
        String idBenar = getGlobalOfaId(context);
        if (!ofaId.equals(idBenar)) {
            // Dipaksa balik ke ID yang benar — tidak bisa pakai ID lain!
            ofaId = idBenar;
        }
        
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
        
        // Belum ada → pakai ID utama yang terkunci
        if (existing == null || existing.trim().isEmpty()) {
            String globalId = getGlobalOfaId(context);
            saveForServer(context, host, port, globalId);
            return globalId;
        }
        
        // 🔒 PASTIKAN TETAP ID YANG BENAR — KOREKSI OTOMATIS JIKA SALAH
        String idBenar = getGlobalOfaId(context);
        if (!existing.equals(idBenar)) {
            saveForServer(context, host, port, idBenar);
            return idBenar;
        }
        
        return existing;
    }

    public static void removeForServer(Context context, String host, int port) {
        if (context == null || host == null) return;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(key).apply();
        // ⚠️ Catatan: ID utama TIDAK dihapus — tetap terkunci milik perangkat ini
    }

    // === 🔒 CEK APAKAH SUDAH TERKUNCI ===
    public static boolean isIdLocked(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_ID_LOCKED, false);
    }

    // === 🔒 COCOKKAN APAKAH ID INI MILIK PERANGKAT INI ===
    public static boolean isMyId(Context context, String ofaId) {
        String idSaya = getGlobalOfaId(context);
        return idSaya != null && idSaya.equals(ofaId);
    }
}
