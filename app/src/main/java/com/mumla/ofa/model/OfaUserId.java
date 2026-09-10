package com.mumla.ofa.model; // Sesuaikan dengan struktur folder asli

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.Random;

public class OfaUserId {
    private static final String TAG = "OFA_USER_ID";
    
    // === KONFIGURASI ID PATEN ===
    private static final String APP_PREFIX = "OFA";
    private static final int SUFFIX_LENGTH = 5;
    private static final String DEV_OVERRIDE_ID = "OFA-DEV001";
    
    // ⚠️ GANTI INI SESUAI KEBUTUHAN
    // true = pakai ID developer (untuk testing)
    // false = pakai Android ID asli (untuk production)
    private static final boolean IS_DEBUG_MODE = false; 

    /**
     * Ambil ID inti dari sistem Android
     * Fallback ke FIXEDSEED jika ANDROID_ID invalid/null
     */
    private static String getCoreSystemId(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            
            // Cek ID dummy bawaan emulator/ROM tertentu
            if ("9774d56d682e549c".equals(androidId) || 
                androidId == null || 
                androidId.isEmpty()) {
                Log.w(TAG, "ANDROID_ID invalid, fallback ke FIXEDSEED");
                return "FIXEDSEED";
            }
            return androidId;
        } catch (Exception e) {
            Log.e(TAG, "Gagal baca ANDROID_ID", e);
            return "FIXEDSEED";
        }
    }

    /**
     * Generate ID Paten Tirai Bambu
     * Format: OFA-XXXXX (5 karakter alfanumerik)
     */
    public static String getPermanentUserId(Context context) {
        // Mode Developer: langsung kembalikan ID spesial
        if (IS_DEBUG_MODE) {
            Log.i(TAG, "🔧 DEBUG MODE: Menggunakan ID Developer " + DEV_OVERRIDE_ID);
            return DEV_OVERRIDE_ID;
        }

        // Production: generate ID unik berdasarkan ANDROID_ID
        String baseSeed = getCoreSystemId(context) + APP_PREFIX;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        long seedValue = baseSeed.hashCode();
        Random randomFixed = new Random(seedValue);

        StringBuilder suffixBuilder = new StringBuilder();
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            int index = randomFixed.nextInt(chars.length());
            suffixBuilder.append(chars.charAt(index));
        }

        String finalId = APP_PREFIX + "-" + suffixBuilder.toString();
        Log.i(TAG, "✅ ID Paten Dihasilkan: " + finalId);
        return finalId;
    }
}
