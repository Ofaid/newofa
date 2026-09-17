/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified By OFAID 2026 — Cek Cadangan Sebelum Buat Baru
 */

package ofaid.ahmad.ptt.preference;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import se.lublin.humla.net.HumlaCertificateGenerator;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.db.DatabaseCertificate;
import ofaid.ahmad.ptt.db.MumlaDatabase;
import ofaid.ahmad.ptt.db.MumlaSQLiteDatabase;

public class MumlaCertificateGenerateTask extends AsyncTask<Void, Void, DatabaseCertificate> {
    private static final String BACKUP_FOLDER = "OFAID_PTT";
    private static final String BACKUP_FILE = "cert_backup.p12"; // ✅ GANTI JADI .p12
    private static final String BACKUP_NAME_FILE = "cert_name.txt";
    private static final String TAG = "CertBackup";

    private Context context;
    private AlertDialog loadingDialog;

    public MumlaCertificateGenerateTask(Context context) {
        this.context = context;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        loadingDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.generateCertProgress)
                .setView(R.layout.dialog_progress)
                .setCancelable(false)
                .create();
        loadingDialog.show();
    }

    @Override
    protected DatabaseCertificate doInBackground(Void... params) {
        try {
            // ==============================================
            // ✅ SAKLAR: CEK CADANGAN DULU — JANGAN BUAT BARU
            // ==============================================
            DatabaseCertificate dariCadangan = cekDanPulihkan();
            if (dariCadangan != null) {
                Log.i(TAG, "✅ DARI CADANGAN — TIDAK BUAT BARU");
                return dariCadangan; // KELUAR — Generator TIDAK dipanggil
            }

            // ==============================================
            // ❌ TIDAK ADA CADANGAN → BARU BUAT BARU
            // ==============================================
            Log.i(TAG, "Tidak ada cadangan → buat baru...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HumlaCertificateGenerator.generateCertificate(baos);
            byte[] data = baos.toByteArray();

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String nama = context.getString(R.string.certificate_export_format, df.format(new Date()));

            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate hasil = db.addCertificate(nama, data);
            db.close();

            // Simpan cadangan untuk masa depan
            simpanCadangan(nama, data);
            Log.i(TAG, "✅ Baru dibuat & dicadangkan");

            return hasil;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ==================================================
    // CEK & PULIHKAN DARI FILE .p12
    // ==================================================
    private DatabaseCertificate cekDanPulihkan() {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            File fileData = new File(folder, BACKUP_FILE);
            File fileNama = new File(folder, BACKUP_NAME_FILE);

            if (!fileData.exists() || !fileNama.exists()) {
                Log.i(TAG, "Tidak ada file cadangan");
                return null;
            }

            // Baca nama asli
            FileInputStream fisNama = new FileInputStream(fileNama);
            byte[] bNama = new byte[(int) fileNama.length()];
            fisNama.read(bNama);
            fisNama.close();
            String namaAsli = new String(bNama, "UTF-8");

            // Baca data sertifikat .p12
            FileInputStream fisData = new FileInputStream(fileData);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int baca;
            while ((baca = fisData.read(buf)) != -1) {
                baos.write(buf, 0, baca);
            }
            fisData.close();
            byte[] data = baos.toByteArray();

            // Masukkan ke database — jalur asli aplikasinya
            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate dc = db.addCertificate(namaAsli, data);
            db.close();

            Log.i(TAG, "✅ Dipulihkan — Nama: " + namaAsli);
            return dc;
        } catch (Exception e) {
            Log.w(TAG, "Gagal pulihkan: " + e.getMessage());
            return null;
        }
    }

    // ==================================================
    // SIMPAN CADANGAN KE FOLDER DOKUMEN
    // ==================================================
    private void simpanCadangan(String nama, byte[] data) {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            // Simpan sertifikat .p12
            FileOutputStream fosData = new FileOutputStream(new File(folder, BACKUP_FILE));
            fosData.write(data);
            fosData.close();

            // Simpan nama
            FileOutputStream fosNama = new FileOutputStream(new File(folder, BACKUP_NAME_FILE));
            fosNama.write(nama.getBytes("UTF-8"));
            fosNama.close();

            Log.i(TAG, "✅ Tersimpan — Nama: " + nama);
        } catch (Exception e) {
            Log.e(TAG, "Gagal simpan: " + e.getMessage());
        }
    }

    @Override
    protected void onPostExecute(DatabaseCertificate result) {
        super.onPostExecute(result);
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}
