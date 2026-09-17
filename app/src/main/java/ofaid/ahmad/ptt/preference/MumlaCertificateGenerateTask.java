/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified By OFAID 2026 — Pulihkan Nama & ID Asli
 */

package ofaid.ahmad.ptt.preference;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private static final String BACKUP_FILE_DATA = "cert_data.bin";
    private static final String BACKUP_FILE_NAME = "cert_name.txt";
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
            // ✅ LANGKAH 1: CEK CADANGAN — PULIHKAN NAMA ASLI
            // ==============================================
            DatabaseCertificate dariCadangan = cekDanPulihkan();
            if (dariCadangan != null) {
                Log.i(TAG, "✅ Dipulihkan — Nama: " + dariCadangan.getName());
                return dariCadangan;
            }

            // ==============================================
            // ✅ LANGKAH 2: TIDAK ADA CADANGAN → BUAT BARU
            // ==============================================
            Log.i(TAG, "Tidak ada cadangan, buat baru...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HumlaCertificateGenerator.generateCertificate(baos);
            byte[] dataSertifikat = baos.toByteArray();

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String namaAsli = context.getString(R.string.certificate_export_format, df.format(new Date()));

            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate hasil = db.addCertificate(namaAsli, dataSertifikat);
            db.close();

            // ==============================================
            // ✅ LANGKAH 3: SIMPAN — NAMA & DATA BERSAMAAN
            // ==============================================
            simpanCadangan(namaAsli, dataSertifikat);
            Log.i(TAG, "✅ Baru dibuat & dicadangkan — Nama: " + namaAsli);

            return hasil;
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ==================================================
    // PULIHKAN — NAMA ASLI DIPAKAI KEMBALI
    // ==================================================
    private DatabaseCertificate cekDanPulihkan() {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            File fileData = new File(folder, BACKUP_FILE_DATA);
            File fileNama = new File(folder, BACKUP_FILE_NAME);

            if (!fileData.exists() || !fileNama.exists()) {
                Log.i(TAG, "Tidak ada file cadangan lengkap");
                return null;
            }

            // Baca NAMA ASLI yang pertama kali dipakai
            FileInputStream fisNama = new FileInputStream(fileNama);
            byte[] bacaNama = new byte[(int) fileNama.length()];
            fisNama.read(bacaNama);
            fisNama.close();
            String namaAsli = new String(bacaNama, "UTF-8");

            // Baca data sertifikat
            FileInputStream fisData = new FileInputStream(fileData);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int baca;
            while ((baca = fisData.read(buffer)) != -1) {
                baos.write(buffer, 0, baca);
            }
            fisData.close();
            byte[] data = baos.toByteArray();

            // Masukkan ke database DENGAN NAMA YANG SAMA PERSIS
            MumlaDatabase db = new MumlaSQLiteDatabase(context);
            DatabaseCertificate dc = db.addCertificate(namaAsli, data);
            db.close();

            return dc;
        } catch (Exception e) {
            Log.w(TAG, "Gagal pulihkan: " + e.getMessage());
            return null;
        }
    }

    // ==================================================
    // SIMPAN — NAMA & DATA DIPISAH DUA FILE
    // ==================================================
    private void simpanCadangan(String nama, byte[] data) {
        try {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    BACKUP_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            // Simpan data sertifikat
            FileOutputStream fosData = new FileOutputStream(new File(folder, BACKUP_FILE_DATA));
            fosData.write(data);
            fosData.flush();
            fosData.close();

            // Simpan NAMA ASLI secara terpisah
            FileOutputStream fosNama = new FileOutputStream(new File(folder, BACKUP_FILE_NAME));
            fosNama.write(nama.getBytes("UTF-8"));
            fosNama.flush();
            fosNama.close();

            Log.i(TAG, "✅ Tersimpan — Nama: " + nama);
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
