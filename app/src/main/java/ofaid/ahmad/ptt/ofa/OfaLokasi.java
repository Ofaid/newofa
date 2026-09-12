
/* Di Buat Oleh Ofaid/Ahmad 12-9-2026
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
        if (context == null) return "Tidak diketahui";

        // Cek izin lokasi
        boolean adaIzinKasar = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        
        boolean adaIzinHalus = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!adaIzinKasar && !adaIzinHalus) {
            return "📍 Izinkan lokasi";
        }

        // Ambil lokasi
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location lokasi = null;

        // Coba GPS dulu — lebih akurat
        if (adaIzinHalus && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        // Kalau GPS tidak ada, pakai internet
        if (lokasi == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (lokasi == null) return "📍 Nyalakan lokasi";

        // Ubah koordinat jadi nama tempat
        try {
            Geocoder gcd = new Geocoder(context, new Locale("id", "ID"));
            List<Address> daftarAlamat = gcd.getFromLocation(
                    lokasi.getLatitude(),
                    lokasi.getLongitude(),
                    1);

            if (daftarAlamat == null || daftarAlamat.isEmpty())
                return "📍 Tidak diketahui";

            Address a = daftarAlamat.get(0);
            StringBuilder hasil = new StringBuilder();

            String desa = a.getSubLocality();   // Desa / Kelurahan
            String kota = a.getLocality();       // Kota / Kabupaten
            String kab  = a.getSubAdminArea();   // Cadangan kalau kota kosong
            String prov = a.getAdminArea();      // Provinsi

            if (desa != null && !desa.trim().isEmpty())
                hasil.append(desa.trim()).append(", ");

            if (kota != null && !kota.trim().isEmpty())
                hasil.append(kota.trim()).append(", ");
            else if (kab != null && !kab.trim().isEmpty())
                hasil.append(kab.trim()).append(", ");

            if (prov != null && !prov.trim().isEmpty())
                hasil.append(prov.trim());

            if (hasil.length() == 0)
                return "📍 Tidak diketahui";

            return hasil.toString();
        } catch (Exception e) {
            return "📍 Tidak dapat dibaca";
        }
    }

    // Tampilkan siap pakai dengan ikon
    public static String formatLokasiTampil(Context context) {
        String daerah = getLokasiDaerah(context);
        return daerah;
    }
}
