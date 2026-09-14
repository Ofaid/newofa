/* Di Buat Oleh Ofaid/Ahmad 12-9-2026 — DIPERSINGKAT 14-9-2026 */
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

        boolean adaIzinKasar = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean adaIzinHalus = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!adaIzinKasar && !adaIzinHalus) {
            return "📍 Izinkan lokasi";
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location lokasi = null;

        if (adaIzinHalus && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (lokasi == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lokasi = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lokasi == null) return "📍 Nyalakan lokasi";

        try {
            Geocoder gcd = new Geocoder(context, new Locale("id", "ID"));
            List<Address> daftarAlamat = gcd.getFromLocation(
                    lokasi.getLatitude(), lokasi.getLongitude(), 1);

            if (daftarAlamat == null || daftarAlamat.isEmpty())
                return "📍 Tidak diketahui";

            Address a = daftarAlamat.get(0);
            StringBuilder hasil = new StringBuilder();

            // ✅ HANYA NAMA TEMPAT — TANPA KATA "KECAMATAN/KABUPATEN"
            String desa = a.getSubLocality();     // Penyabangan
            String kec  = a.getLocality();         // Gerokgak
            String kab  = a.getSubAdminArea();     // Buleleng
            String prov = a.getAdminArea();        // Bali

            if (desa != null && !desa.trim().isEmpty())
                hasil.append(desa.trim()).append(". ");

            if (kec != null && !kec.trim().isEmpty())
                hasil.append(kec.trim()).append(". ");
            else if (kab != null && !kab.trim().isEmpty())
                hasil.append(kab.trim()).append(". ");

            if (prov != null && !prov.trim().isEmpty())
                hasil.append(prov.trim());

            if (hasil.length() == 0)
                return "📍 Tidak diketahui";

            return hasil.toString();
        } catch (Exception e) {
            return "📍 Tidak dapat dibaca";
        }
    }

    public static String formatLokasiTampil(Context context) {
        return getLokasiDaerah(context);
    }
}
