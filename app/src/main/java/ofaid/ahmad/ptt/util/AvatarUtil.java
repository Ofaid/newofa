package ofaid.ahmad.ptt.util;  // ✅ HARUS SAMA PERSIS

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AvatarUtil {
    private static final int MAKS_UKURAN = 96;
    private static final int KUALITAS = 85;
    private static final String NAMA_SIMPANAN = "ofa_avatar_penyimpanan";
    private static final String KUNCI_DATA = "avatar_data";

    // ✅ Pastikan nama fungsi SAMA PERSIS
    public static byte[] olahGambar(Context konteks, Uri uri) {
        if (konteks == null || uri == null) return null;
        try {
            InputStream aliran = konteks.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(aliran);
            if (bitmap == null) return null;
            
            Bitmap skala = Bitmap.createScaledBitmap(bitmap, MAKS_UKURAN, MAKS_UKURAN, true);
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            skala.compress(Bitmap.CompressFormat.JPEG, KUALITAS, keluar);
            skala.recycle();
            bitmap.recycle();
            return keluar.toByteArray();
        } catch (Exception e) {
            Log.e("AvatarUtil", "Gambar gagal diproses", e);
            return null;
        }
    }

    public static void simpanAvatar(Context konteks, byte[] data) {
        if (konteks == null || data == null) return;
        String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
        konteks.getSharedPreferences(NAMA_SIMPANAN, Context.MODE_PRIVATE)
               .edit()
               .putString(KUNCI_DATA, base64)
               .apply();
    }

    public static byte[] ambilAvatarTersimpan(Context konteks) {
        if (konteks == null) return null;
        String base64 = konteks.getSharedPreferences(NAMA_SIMPANAN, Context.MODE_PRIVATE)
                                .getString(KUNCI_DATA, null);
        if (base64 == null) return null;
        try {
            return Base64.decode(base64, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}
