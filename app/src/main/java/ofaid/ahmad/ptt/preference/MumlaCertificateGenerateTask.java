/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified By OFAID 2026 — Sistem Cadangan Sertifikat Tetap
 */

package ofaid.ahmad.ptt.preference;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

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
            // ✅ LANGKAH 1: Cek Cadangan Aman — Pulihkan Jika Ada
            DatabaseCertificate pulihDariCadangan = cekDanPulihkanCadangan();
            if (pulihDariCadangan != null) {
                Log.i(TAG, "✅ Sertifikat dipulihkan dari cadangan!");
                return pulihDariCadangan;
            }

            // ✅ LANGKAH 2: Tidak Ada Cadangan → Buat Baru
            Log.i(TAG, "Tidak ada cadangan, buat sertifikat baru...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HumlaCertificateGenerator.generateCertificate(baos);
            byte[] dataSertifikat = baos.toByteArray();

            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            String fileName = context.getString(R.string.certificate_export_format, dateFormat.format(new Date()));

            MumlaDatabase database = new MumlaSQLiteDatabase(context);
            DatabaseCertificate dc = database.addCertificate(fileName, dataSertifikat);
            database.close();

            // ✅ LANGKAH 3: Simpan Cadangan ke Tempat Aman
            simpanCadangan(dataSertifikat);
            Log.i(TAG, "✅ Sertifikat baru dibuat & dicadangkan");

            return dc;
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal proses sertifikat: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ========== SISTEM CADANGAN & PULIHKAN ==========
    private DatabaseCertificate cekDanPulihkanCadangan() {
        try {
            File folderCadangan = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            File fileCadangan = new File(folderCadangan, BACKUP_FILE);

            if (!fileCadangan.exists()) {
                Log.i(TAG, "Tidak ada file cadangan");
                return null;
            }

            // Baca data dari cadangan
            FileInputStream fis = new FileInputStream(fileCadangan);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int baca;
            while ((baca = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, baca);
            }
            fis.close();
            byte[] dataCadangan = baos.toByteArray();

            // Masukkan ke database seolah-olah baru dibuat
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            String namaPulih = "Dipulihkan-" + dateFormat.format(new Date());

            MumlaDatabase database = new MumlaSQLiteDatabase(context);
            DatabaseCertificate dc = database.addCertificate(namaPulih, dataCadangan);
            database.close();

            return dc;
        } catch (Exception e) {
            Log.w(TAG, "Gagal pulihkan cadangan: " + e.getMessage());
            return null;
        }
    }

    private void simpanCadangan(byte[] data) {
        try {
            File folderCadangan = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);

            if (!folderCadangan.exists()) {
                folderCadangan.mkdirs();
            }

            File fileCadangan = new File(folderCadangan, BACKUP_FILE);
            FileOutputStream fos = new FileOutputStream(fileCadangan);
            fos.write(data);
            fos.flush();
            fos.close();

            Log.i(TAG, "✅ Cadangan tersimpan di: " + fileCadangan.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal simpan cadangan: " + e.getMessage());
        }
    }
    // ========== AKHIR SISTEM CADANGAN ==========

    @Override
    protected void onPostExecute(DatabaseCertificate result) {
        super.onPostExecute(result);
        if (result == null) {
            Toast.makeText(context, R.string.generateCertFailure, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "✅ Sertifikat siap dipakai!", Toast.LENGTH_SHORT).show();
        }

        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
    }
}
