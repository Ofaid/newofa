/*Edit By Ofaid/Ahmd-jr 9-9-2026 — SISTEM ID TETAP TERKUNCI + ANDROID_ID*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public class OfaIdentity {
    private static final String PREFS_NAME = "ofa_identity_prefs";
    private static final String PREF_OFA_ID_PREFIX = "ofa_id_";
    private static final String PREF_DEVICE_FINGERPRINT = "device_fingerprint";
    private static final String PREF_GLOBAL_OFA_ID = "global_ofa_id";
    private static final String PREF_ID_LOCKED = "id_locked";
    private static final String PREF_IS_OWNER = "is_owner_device";
    private static final String NAMA_FILE_CADANGAN = "ofa_id_backup.dat";
    private static String sCachedOwnerId = null;

    // === LOKASI FILE CADANGAN ===
    private static File getFileCadangan(Context context) {
        File folder = context.getExternalFilesDir(null);
        if (folder == null || !folder.canWrite()) {
            folder = context.getFilesDir();
        }
        return new File(folder, NAMA_FILE_CADANGAN);
    }

    // === SIMPAN ID KE FILE CADANGAN ===
    private static void simpanKeCadangan(Context context, String ofaId) {
        File file = getFileCadangan(context);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(ofaId.getBytes());
            fos.getFD().sync();
            Log.i("OfaIdentity", "✅ ID tersimpan di cadangan: " + ofaId);
        } catch (IOException e) {
            Log.w("OfaIdentity", "⚠️ Gagal simpan cadangan", e);
        }
    }

    // === BACA ID DARI FILE CADANGAN ===
    private static String bacaDariCadangan(Context context) {
        File file = getFileCadangan(context);
        if (!file.exists() || file.length() == 0) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int dibaca = fis.read(data);
            if (dibaca > 0) {
                String idPulih = new String(data).trim();
                Log.i("OfaIdentity", "✅ ID dipulihkan dari cadangan: " + idPulih);
                return idPulih;
            }
        } catch (IOException e) {
            Log.w("OfaIdentity", "⚠️ Gagal baca cadangan", e);
        }
        return null;
    }

    // === 🔒 SUMBER UTAMA: ANDROID_ID — TETAP UNTUK HP INI ===
    private static String getAndroidIdHash(Context context) {
        String androidId = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = "OFAFIXEDDEFAULT";
        }
        // Buat angka tetap dari ANDROID_ID → SELALU SAMA
        long nilaiTetap = Math.abs(androidId.hashCode() * 7591L + 31L);
        int kodeUtama = (int) (nilaiTetap % 90000) + 10000;
        return String.format(Locale.ROOT, "%05d", kodeUtama);
    }

    // === 2. ID UTAMA — TERKUNCI + PULIH DARI CADANGAN ===
    public static String getGlobalOfaId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // ✅ Langkah 1: Sudah dikunci? Langsung pakai
        if (sp.getBoolean(PREF_ID_LOCKED, false) && sp.contains(PREF_GLOBAL_OFA_ID)) {
            return sp.getString(PREF_GLOBAL_OFA_ID, null);
        }

        // ✅ Langkah 2: Cek cadangan luar
        String idDariCadangan = bacaDariCadangan(context);
        if (idDariCadangan != null && !idDariCadangan.trim().isEmpty()) {
            sp.edit()
                .putString(PREF_GLOBAL_OFA_ID, idDariCadangan)
                .putBoolean(PREF_ID_LOCKED, true)
                .apply();
            Log.i("OfaIdentity", "🔒 ID dipulihkan dari cadangan: " + idDariCadangan);
            return idDariCadangan;
        }

        // ✅ Langkah 3: Buat DARI ANDROID_ID → TIDAK PERNAH BERUBAH 🔒
        String kodeTetap = getAndroidIdHash(context);
        String idBaru = "OFA-" + kodeTetap;
        
        sp.edit()
            .putString(PREF_GLOBAL_OFA_ID, idBaru)
            .putBoolean(PREF_ID_LOCKED, true)
            .apply();
        
        simpanKeCadangan(context, idBaru);
        
        Log.i("OfaIdentity", "🆔 ID BARU DIBUAT & DIKUNCI: " + idBaru);
        return idBaru;
    }

    // === 3. SINKRON ID KE SERVER ===
    public static void saveForServer(Context context, String host, int port, String ofaId) {
        if (context == null || host == null || host.trim().isEmpty() || ofaId == null) return;
        String idBenar = getGlobalOfaId(context);
        if (!ofaId.equals(idBenar)) ofaId = idBenar;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, ofaId).apply();
    }

    public static String getExistingForServer(Context context, String host, int port) {
        if (context == null || host == null || host.trim().isEmpty()) return null;
        String key = PREF_OFA_ID_PREFIX + host.toLowerCase(Locale.ROOT) + "_" + port;
        String existing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, null);
        if (existing == null || existing.trim().isEmpty()) {
            String globalId = getGlobalOfaId(context);
            saveForServer(context, host, port, globalId);
            return globalId;
        }
        String idBenar = getGlobalOfaId(context);
        if (!existing.equals(idBenar)) {
            saveForServer(context, host, port, idBenar);
            return idBenar;
        }
        return existing;
    }

    // === 4. TANDA PEMILIK ===
    public static void tetapkanSebagaiPemilik(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (sp.getBoolean(PREF_IS_OWNER, false)) return;
        sp.edit().putBoolean(PREF_IS_OWNER, true).apply();
        sCachedOwnerId = getGlobalOfaId(context);
    }

    public static boolean isPerangkatPemilik(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean ditetapkan = sp.getBoolean(PREF_IS_OWNER, false);
        if (!ditetapkan) return false;
        String idSaatIni = getGlobalOfaId(context);
        if (sCachedOwnerId == null) sCachedOwnerId = idSaatIni;
        return sCachedOwnerId.equals(idSaatIni);
    }

    public static boolean isIdLocked(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_ID_LOCKED, false);
    }

    // === ✅ TAMPILAN SINGKAT ===
    public static String getSingkat(Context context) {
        String penuh = getGlobalOfaId(context);
        if (penuh == null) return "OFA-00000";
        if (penuh.contains("-")) {
            String[] bagian = penuh.split("-");
            if (bagian.length >= 2) {
                return bagian[0] + "-" + bagian[1];
            }
        }
        return penuh;
    }
}
