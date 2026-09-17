package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class OfaCertificateBackup {
    private static final String TAG = "OfaCertBackup";
    
    // Folder aman — TIDAK IKUT TERHAPUS saat hapus data aplikasi
    private static final String FOLDER_AMAN = "OFA_PTT_DATA";
    private static final String FILE_CADANGAN = "mumla_cert_backup.p12";

    // Dapatkan lokasi folder aman
    private static File getFolderAman() {
        File folder = new File(Environment.getExternalStorageDirectory(), FOLDER_AMAN);
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    // =============================================
    // ✅ SIMPAN — dipanggil saat sertifikat dibuat pertama kali
    // =============================================
    public static boolean simpanSertifikat(Context context, byte[] data) {
        if (data == null || data.length == 0) return false;
        
        File file = new File(getFolderAman(), FILE_CADANGAN);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            fos.getFD().sync(); // Pastikan benar-benar tersimpan
            Log.i(TAG, "✅ Sertifikat disimpan di: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "❌ Gagal simpan: " + e.getMessage());
            return false;
        } finally {
            if (fos != null) try { fos.close(); } catch (IOException ignored) {}
        }
    }

    // =============================================
    // ✅ PULIHKAN — dipanggil saat aplikasi dibuka & sertifikat hilang
    // =============================================
    public static byte[] pulihkanSertifikat(Context context) {
        File file = new File(getFolderAman(), FILE_CADANGAN);
        if (!file.exists()) {
            Log.d(TAG, "Belum ada cadangan");
            return null;
        }

        FileInputStream fis = null;
        try {
            byte[] data = new byte[(int) file.length()];
            fis = new FileInputStream(file);
            int dibaca = fis.read(data);
            if (dibaca == data.length) {
                Log.i(TAG, "✅ Sertifikat dipulihkan dari cadangan");
                return data;
            }
            return null;
        } catch (IOException e) {
            Log.e(TAG, "❌ Gagal pulihkan: " + e.getMessage());
            return null;
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignored) {}
        }
    }

    // =============================================
    // ✅ CEK: SUDAH ADA CADANGAN?
    // =============================================
    public static boolean adaCadangan(Context context) {
        return new File(getFolderAman(), FILE_CADANGAN).exists();
    }
}
