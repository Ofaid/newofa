package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.provider.Settings;
import java.util.Random;

public class OfaIdentity {
    private static final String AWAL = "DL";
    private static final int PANJANG = 5;

    private static String ambilIdSistem(Context ctx) {
        String id = Settings.Secure.getString(
            ctx.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
        return (id == null || id.isEmpty()) ? "OFA2026" : id;
    }

    public static String getGlobalOfaId(Context ctx) {
        String benih = ambilIdSistem(ctx) + AWAL;
        Random acakTetap = new Random(benih.hashCode());
        String huruf = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder b = new StringBuilder(AWAL + "-");
        for (int i = 0; i < PANJANG; i++) {
            b.append(huruf.charAt(acakTetap.nextInt(huruf.length())));
        }
        return b.toString();
    }

    public static String getSingkat(Context ctx) {
        return getGlobalOfaId(ctx);
    }
}
