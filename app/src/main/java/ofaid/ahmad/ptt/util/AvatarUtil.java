/*Created By Ofaid*/
package se.lublin.mumla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class AvatarUtil {
    private static final int MAKS_UKURAN = 96;
    private static final int KUALITAS = 85;
    
    // === PENYIMPANAN DI HP ===
    private static final String NAMA_SIMPANAN = "ofa_avatar_penyimpanan";
    private static final String KUNCI_DATA = "avatar_data";

    // Simpan setelah dikirim
    public static void simpanAvatar(Context konteks, byte[] data) {
        if (konteks == null || data == null) return;
        String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
        konteks.getSharedPreferences(NAMA_SIMPANAN, Context.MODE_PRIVATE)
               .edit()
               .putString(KUNCI_DATA, base64)
               .apply();
        Log.i("AvatarSimpan", "✅ Tersimpan — " + data.length + " byte");
    }

    // Ambil kembali saat buka aplikasi
    public static byte[] ambilAvatarTersimpan(Context konteks) {
        if (konteks == null) return null;
        String base64 = konteks.getSharedPreferences(NAMA_SIMPANAN, Context.MODE_PRIVATE)
                                .getString(KUNCI_DATA, null);
        if (base64 == null) {
            Log.i("AvatarSimpan", "ℹ️ Belum ada foto tersimpan");
            return null;
        }
        try {
            byte[] data = Base64.decode(base64, Base64.NO_WRAP);
            Log.i("AvatarSimpan", "✅ Diambil kembali — " + data.length + " byte");
            return data;
        } catch (Exception e) {
            Log.e("AvatarSimpan", "🔴 Gagal baca simpanan", e);
            return null;
        }
    }

    // Ubah gambar jadi byte[] (otomatis JPEG)
    public static byte[] olahGambar(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            Bitmap skala = Bitmap.createScaledBitmap(bitmap, MAKS_UKURAN, MAKS_UKURAN, true);
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            skala.compress(Bitmap.CompressFormat.JPEG, KUALITAS, keluar);
            skala.recycle();
            return keluar.toByteArray();
        } catch (Exception e) {
            Log.e("AvatarOlah", "🔴 Gambar gagal diproses", e);
            return null;
        }
    }
}
