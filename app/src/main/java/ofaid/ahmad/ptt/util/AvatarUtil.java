/*Created by Ofaid 2026*/
package ofaid.ahmad.ptt.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AvatarUtil {
    private static final int MAKS_UKURAN = 96; // piksel
    private static final int KUALITAS_JPEG = 85; // %

    public static byte[] olahGambar(Context konteks, Uri uriGambar) {
        try {
            // 1. Baca ukuran asli dulu
            InputStream aliranCek = konteks.getContentResolver().openInputStream(uriGambar);
            BitmapFactory.Options opsi = new BitmapFactory.Options();
            opsi.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(aliranCek, null, opsi);
            aliranCek.close();

            int lebarAsli = opsi.outWidth;
            int tinggiAsli = opsi.outHeight;

            // 2. Hitung skala agar tidak terlalu besar
            int skala = 1;
            while ((lebarAsli / skala) > MAKS_UKURAN || (tinggiAsli / skala) > MAKS_UKURAN) {
                skala *= 2;
            }

            // 3. Baca gambar asli dengan skala
            InputStream aliranGambar = konteks.getContentResolver().openInputStream(uriGambar);
            opsi.inJustDecodeBounds = false;
            opsi.inSampleSize = skala;
            Bitmap bitmapAsli = BitmapFactory.decodeStream(aliranGambar, null, opsi);
            aliranGambar.close();

            if (bitmapAsli == null) {
                Log.e("AvatarUtil", "Gagal baca gambar");
                return null;
            }

            // 4. Potong jadi persegi
            int sisiTerkecil = Math.min(bitmapAsli.getWidth(), bitmapAsli.getHeight());
            int mulaiX = (bitmapAsli.getWidth() - sisiTerkecil) / 2;
            int mulaiY = (bitmapAsli.getHeight() - sisiTerkecil) / 2;
            Bitmap bitmapPersegi = Bitmap.createBitmap(
                bitmapAsli, mulaiX, mulaiY, sisiTerkecil, sisiTerkecil
            );

            if (bitmapPersegi != bitmapAsli) {
                bitmapAsli.recycle();
            }

            // 5. Ubah ukuran jadi MAKS_UKURAN x MAKS_UKURAN
            Bitmap bitmapAkhir = Bitmap.createScaledBitmap(
                bitmapPersegi, MAKS_UKURAN, MAKS_UKURAN, true
            );
            bitmapPersegi.recycle();

            // 6. Ubah ke JPEG — ini yang server minta ✅
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            bitmapAkhir.compress(Bitmap.CompressFormat.JPEG, KUALITAS_JPEG, keluar);
            bitmapAkhir.recycle();

            byte[] hasil = keluar.toByteArray();
            Log.i("AvatarUtil", "✅ Berhasil — " + hasil.length + " byte, format: JPEG");
            return hasil;

        } catch (Exception e) {
            Log.e("AvatarUtil", "🔴 Error olah gambar", e);
            return null;
        }
    }

    // Bantu: Ubah Drawable ke byte[] kalau dibutuhkan
    public static byte[] dariDrawable(Drawable gbr) {
        if (gbr instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) gbr).getBitmap();
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, KUALITAS_JPEG, keluar);
            return keluar.toByteArray();
        }
        Bitmap bmp = Bitmap.createBitmap(
            gbr.getIntrinsicWidth(), gbr.getIntrinsicHeight(),
            gbr.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565
        );
        Canvas kanvas = new Canvas(bmp);
        gbr.setBounds(0, 0, kanvas.getWidth(), kanvas.getHeight());
        gbr.draw(kanvas);
        ByteArrayOutputStream keluar = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, KUALITAS_JPEG, keluar);
        return keluar.toByteArray();
    }
}
