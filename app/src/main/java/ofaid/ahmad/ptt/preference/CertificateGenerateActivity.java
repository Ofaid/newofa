/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
 * Ofaid 2026 — Sistem Cadangan Sertifikat
 */

package ofaid.ahmad.ptt.preference;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.db.DatabaseCertificate;

public class CertificateGenerateActivity extends AppCompatActivity {

    private static final int KODE_IZIN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Minta SEMUA izin SEKALIGUS sebelum mulai
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            String[] izin = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            };
            cekDanMintaIzin(izin);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–12
            String[] izin = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            cekDanMintaIzin(izin);
        } else {
            // Android lama — langsung jalan
            jalankanTugasSertifikat();
        }
    }

    private void cekDanMintaIzin(String[] daftarIzin) {
        boolean semuaSudahIzin = true;
        for (String izin : daftarIzin) {
            if (checkSelfPermission(izin) != PackageManager.PERMISSION_GRANTED) {
                semuaSudahIzin = false;
                break;
            }
        }

        if (semuaSudahIzin) {
            jalankanTugasSertifikat();
        } else {
            requestPermissions(daftarIzin, KODE_IZIN);
        }
    }

    @Override
    public void onRequestPermissionsResult(int kode, @NonNull String[] izin, @NonNull int[] hasil) {
        super.onRequestPermissionsResult(kode, izin, hasil);
        if (kode == KODE_IZIN) {
            jalankanTugasSertifikat(); // Tetap jalan walau ditolak
        }
    }

    private void jalankanTugasSertifikat() {
        // ✅ Tugas akan CEK CADANGAN DULU sebelum buat baru
        MumlaCertificateGenerateTask task = new MumlaCertificateGenerateTask(this) {
            @Override
            protected void onPostExecute(DatabaseCertificate result) {
                super.onPostExecute(result);
                if (result == null) {
                    finish();
                    return;
                }

                Settings settings = Settings.getInstance(CertificateGenerateActivity.this);
                settings.setDefaultCertificateId(result.getId());
                showCompletionDialog(result);
            }
        };
        task.execute();
    }

    private void showCompletionDialog(DatabaseCertificate result) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.generateCertSuccess, result.getName()))
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> finish())
                .show();
    }
}
