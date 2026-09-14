/* Di Buat Oleh Ofaid/Ahmad 14-9-2026 */
package ofaid.ahmad.ptt.ofa;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import java.util.List;
import java.util.Locale;

public class OfaLokasi {

    public static String getLokasiDaerah(Context context) {
        if (context == null) return null;

        // Cek izin lokasi
        boolean adaIzinKasar = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        
        boolean adaIzinHalus = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!adaIzinKasar && !adaIzinHalus) {
            return null; // belum izin = tidak tampil apa-apa
        }

        // Ambil lokasi
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location lokasi = null;

        // Coba GPS dulu
        if (adaIzinHalus && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        // Kalau GPS tidak ada, pakai internet
        if (lokasi == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (lokasi == null) return null;

        // Ubah koordinat jadi nama tempat — FORMAT PENDEK
        try {
            Geocoder gcd = new Geocoder(context, new Locale("id", "ID"));
            List<Address> daftarAlamat = gcd.getFromLocation(
                    lokasi.getLatitude(),
                    lokasi.getLongitude(),
                    1);

            if (daftarAlamat == null || daftarAlamat.isEmpty())
                return null;

            Address a = daftarAlamat.get(0);
            StringBuilder hasil = new StringBuilder();

            // === FORMAT BARU: NAMA SAJA, DIPISAH TITIK ===
            String desa = a.getSubLocality();    // Desa
            String kec  = a.getLocality();        // Kecamatan
            String kab  = a.getSubAdminArea();    // Kabupaten
            String prov = a.getAdminArea();       // Provinsi

            if (desa != null && !desa.trim().isEmpty())
                hasil.append(desa.trim()).append(". ");

            if (kec != null && !kec.trim().isEmpty())
                hasil.append(kec.trim()).append(". ");

            if (kab != null && !kab.trim().isEmpty())
                hasil.append(kab.trim()).append(". ");

            if (prov != null && !prov.trim().isEmpty())
                hasil.append(prov.trim());

            if (hasil.length() == 0)
                return null;

            return hasil.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatLokasiTampil(Context context) {
        return getLokasiDaerah(context);
    }
}
