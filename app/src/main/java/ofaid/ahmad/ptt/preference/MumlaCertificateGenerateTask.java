/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified By OFAID 2026 — Sistem Cadangan Tetap
 */

package ofaid.ahmad.ptt.preference;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import se.lublin.humla.net.HumlaCertificateGenerator;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.db.DatabaseCertificate;
import ofaid.ahmad.ptt.db.MumlaDatabase;
import ofaid.ahmad.ptt.db.MumlaSQLiteDatabase;

public class MumlaCertificateGenerateTask extends AsyncTask<Void, Void, DatabaseCertificate> {
    private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss";
    private static final String BACKUP_FOLDER = "OFAID_PTT";
    private static final String BACKUP_FILE = "ofaid_cert_backup.bin";
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
            // ✅ LANGKAH 1: CEK CADANGAN DULU — PULIHKAN JIKA ADA
            // ==============================================
            DatabaseCertificate dariCadangan = cekDanPulihkan();
            if (dariCadangan != null) {
                Log.i(TAG, "✅ Dipulihkan dari cadangan!");
                return dariCadangan; // Selesai, TIDAK buat baru
            }

            // ==============================================
            // ✅ LANGKAH 2: TIDAK ADA CADANGAN → BUAT BARU
            // ==============================================
            Log.i(TAG, "Tidak ada cadangan, buat baru...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HumlaCertificateGenerator.generateCertificate(baos);
            byte[] dataSertifikat = baos.toByteArray();

            SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            String nama = context.getString(R.string.certificate_export_format, df.format(new Date()));

            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate hasil = db.addCertificate(nama, dataSertifikat);
            db.close();

            // ==============================================
            // ✅ LANGKAH 3: SIMPAN CADANGAN — UNTUK MASA DEPAN
            // ==============================================
            simpanCadangan(dataSertifikat);
            Log.i(TAG, "✅ Baru dibuat & dicadangkan");

            return hasil;
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ==================================================
    // PULIHKAN DARI CADANGAN
    // ==================================================
    private DatabaseCertificate cekDanPulihkan() {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            File file = new File(folder, BACKUP_FILE);

            if (!file.exists()) {
                Log.i(TAG, "Tidak ada file cadangan");
                return null;
            }

            // Baca data cadangan
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int baca;
            while ((baca = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, baca);
            }
            fis.close();
            byte[] data = baos.toByteArray();

            // Masukkan ke database
            String namaPulih = "Dipulihkan-" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date());
            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate dc = db.addCertificate(namaPulih, data);
            db.close();

            return dc;
        } catch (Exception e) {
            Log.w(TAG, "Gagal pulihkan: " + e.getMessage());
            return null;
        }
    }

    // ==================================================
    // SIMPAN CADANGAN
    // ==================================================
    private void simpanCadangan(byte[] data) {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);

            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, BACKUP_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();

            Log.i(TAG, "✅ Cadangan disimpan: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal simpan: " + e.getMessage());
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
