/*
 * OFA: Kotak Pilihan Status — Dialog AndroidX
 */
package ofaid.ahmad.ptt.ofa;

import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class PilihStatusDialog extends DialogFragment {
    public static final String ARG_ID_PENGGUNA = "id_pengguna";
    public static final String ARG_NAMA_PENGGUNA = "nama_pengguna";

    // ✅ Antarmuka — beri tahu pemilik layar saat status berubah
    public interface PadaStatusDiubahListener {
        void padaStatusDiubah(int idPengguna, String kodeStatusBaru);
    }

    public static PilihStatusDialog buat(int idPengguna, String nama) {
        PilihStatusDialog frag = new PilihStatusDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ID_PENGGUNA, idPengguna);
        args.putString(ARG_NAMA_PENGGUNA, nama);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int idPengguna = getArguments() != null ? getArguments().getInt(ARG_ID_PENGGUNA, 0) : 0;
        String namaPengguna = getArguments() != null ? getArguments().getString(ARG_NAMA_PENGGUNA, "") : "";

        String kodeSekarang = OfaUserStatus.dapatKodeStatus(getActivity(), idPengguna);
        int posisiAktif = OfaUserStatus.cariPosisi(kodeSekarang);

        return new AlertDialog.Builder(getActivity())
    .setTitle("Status — " + namaPengguna)
    .setSingleChoiceItems(OfaUserStatus.TAMPILAN, posisiAktif, (dialog, posisiDipilih) -> {
        String kodeBaru = OfaUserStatus.KODE[posisiDipilih];
        // ✅ Langsung simpan status baru
        OfaUserStatus.simpanStatus(getActivity(), idPengguna, kodeBaru);
        
        // ✅ BERITAHU MUMLAACTIVITY — SUPAYA LANGSUNG BERUBAH DI LAYAR! 📢
        if (getActivity() instanceof PilihStatusDialog.PadaStatusDiubahListener) {
            ((PilihStatusDialog.PadaStatusDiubahListener) getActivity()).padaStatusDiubah(idPengguna, kodeBaru);
        }
        
        dialog.dismiss();
    })
    // ... biarkan sisa kode aslimu tetap di bawah sini — tombol Batal dll tetap berfungsi!
    .setNegativeButton(android.R.string.cancel, null)
    .create();

    }
}
