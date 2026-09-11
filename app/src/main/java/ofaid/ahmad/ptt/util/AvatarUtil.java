package ofaid.ahmad.ptt.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AvatarUtil {
    private static final String TAG = "AvatarUtil";

    // Ukuran standar avatar Mumble
    private static final int UKURAN_MAX = 128;
    // Batas aman ukuran berkas
    private static final int BATAS_BYTE = 60000;

    /**
     * Olah gambar dari galeri jadi data siap kirim ke server.
     * @return byte[] siap pakai, atau null kalau gagal
     */
    public static byte[] olahGambar(Context konteks, Uri uriGambar) {
        Log.i(TAG, "🟢 Mulai memproses gambar...");

        try {
            // Buka berkas gambar
            InputStream aliran = konteks.getContentResolver().openInputStream(uriGambar);
            if (aliran == null) {
                Log.e(TAG, "🔴 Tidak bisa membuka berkas gambar");
                return null;
            }

            Bitmap asli = BitmapFactory.decodeStream(aliran);
            aliran.close();

            if (asli == null) {
                Log.e(TAG, "🔴 Bukan format gambar yang didukung");
                return null;
            }

            Log.i(TAG, "🟢 Gambar terbaca: " + asli.getWidth() + "×" + asli.getHeight());

            // Ubah ukuran jadi 128×128
            Bitmap disesuaikan = potongTengah(asli, UKURAN_MAX, UKURAN_MAX);
            asli.recycle(); // bersihkan memori

            // Kompres ke PNG
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            disesuaikan.compress(Bitmap.CompressFormat.PNG, 100, keluar);
            byte[] hasil = keluar.toByteArray();

            // Kalau masih terlalu besar → turunkan kualitas jadi JPEG
            if (hasil.length > BATAS_BYTE) {
                Log.w(TAG, "🟡 Terlalu besar, ganti ke format JPEG...");
                keluar.reset();
                disesuaikan.compress(Bitmap.CompressFormat.JPEG, 80, keluar);
                hasil = keluar.toByteArray();
            }

            disesuaikan.recycle(); // bersihkan memori
            Log.i(TAG, "✅ Selesai — Ukuran akhir: " + hasil.length + " byte");

            return hasil;

        } catch (Exception e) {
            Log.e(TAG, "🔴 Kesalahan saat memproses gambar", e);
            return null;
        }
    }

    /**
     * Ubah ukuran gambar, potong bagian tengah biar kotak.
     */
    private static Bitmap potongTengah(Bitmap sumber, int lebarBaru, int tinggiBaru) {
        int lebarAsli = sumber.getWidth();
        int tinggiAsli = sumber.getHeight();

        // Kalau sudah kecil, pakai apa adanya
        if (lebarAsli <= lebarBaru && tinggiAsli <= tinggiBaru) {
            Log.d(TAG, "ℹ️ Ukuran sudah pas, tidak diubah");
            return sumber;
        }

        // Hitung skala perbesaran
        float skala = Math.max(
                (float) lebarBaru / lebarAsli,
                (float) tinggiBaru / tinggiAsli
        );

        int lebarSkalasi = Math.round(skala * lebarAsli);
        int tinggiSkalasi = Math.round(skala * tinggiAsli);

        Bitmap diubah = Bitmap.createScaledBitmap(sumber, lebarSkalasi, tinggiSkalasi, true);

        // Potong dari tengah
        int sisaX = lebarSkalasi - lebarBaru;
        int sisaY = tinggiSkalasi - tinggiBaru;

        Bitmap hasil = Bitmap.createBitmap(diubah, sisaX / 2, sisaY / 2, lebarBaru, tinggiBaru);
        diubah.recycle();

        Log.d(TAG, "✅ Disesuaikan jadi: " + lebarBaru + "×" + tinggiBaru);
        return hasil;
    }
}
