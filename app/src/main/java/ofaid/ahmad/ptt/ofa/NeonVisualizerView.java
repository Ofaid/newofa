/*Dibuat Oleh Ofaid/Ahmad 15-9-2026*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class OfaVisualizer extends View {

    private static final float TINGGI_BAR_DP = 4f;
    private static final float SKALA_MAKS = 100f;

    private float nilaiSaatIni = 0f;
    private float nilaiLancar = 0f;
    private static final float LANCAR = 0.85f; // halus, tidak menyentak

    private final Paint catHijau = new Paint();
    private final Paint catKuning = new Paint();
    private final Paint catMerah = new Paint();
    private final Paint catAngka = new Paint();
    private final RectF kotak = new RectF();
    private float dp;

    public OfaVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWarna();
    }

    public OfaVisualizer(Context context) {
        super(context);
        initWarna();
    }

    private void initWarna() {
        dp = getResources().getDisplayMetrics().density;

        // 🟢 Hijau — 0 s/d 50
        catHijau.setColor(0xFF00E676);
        catHijau.setAntiAlias(true);

        // 🟡 Kuning — 51 s/d 60
        catKuning.setColor(0xFFFFC107);
        catKuning.setAntiAlias(true);

        // 🔴 Merah — 61 s/d 100
        catMerah.setColor(0xFFFF5252);
        catMerah.setAntiAlias(true);

        // Angka kecil di bawah
        catAngka.setColor(0xFF888888);
        catAngka.setTextSize(9 * dp);
        catAngka.setAntiAlias(true);
    }

    // Panggil dari luar — nilai 0.0 s/d 1.0
    public void setAudioLevel(float levelNormal) {
        // Ubah 0..1 → 0..100
        nilaiSaatIni = Math.max(0f, Math.min(1f, levelNormal)) * SKALA_MAKS;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Haluskan gerakan
        nilaiLancar = nilaiLancar * LANCAR + nilaiSaatIni * (1f - LANCAR);

        float lebar = getWidth();
        float tinggiBar = TINGGI_BAR_DP * dp;
        float yTengah = getHeight() / 2f;
        float yAtas = yTengah - tinggiBar / 2f;
        float yBawah = yTengah + tinggiBar / 2f;

        float batasKuning = lebar * 0.50f; // 50%
        float batasMerah = lebar * 0.60f;  // 60%
        float ujung = (nilaiLancar / SKALA_MAKS) * lebar;

        // === GAMBAR BATANG ===
        if (ujung <= 0) return;

        if (ujung <= batasKuning) {
            // Hanya hijau
            kotak.set(0, yAtas, ujung, yBawah);
            canvas.drawRect(kotak, catHijau);
        } else if (ujung <= batasMerah) {
            // Hijau penuh + kuning sebagian
            kotak.set(0, yAtas, batasKuning, yBawah);
            canvas.drawRect(kotak, catHijau);
            kotak.set(batasKuning, yAtas, ujung, yBawah);
            canvas.drawRect(kotak, catKuning);
        } else {
            // Hijau + kuning penuh + merah
            kotak.set(0, yAtas, batasKuning, yBawah);
            canvas.drawRect(kotak, catHijau);
            kotak.set(batasKuning, yAtas, batasMerah, yBawah);
            canvas.drawRect(kotak, catKuning);
            kotak.set(batasMerah, yAtas, ujung, yBawah);
            canvas.drawRect(kotak, catMerah);
        }

        // === TANDA ANGKA 0 — 50 — 100 ===
        float pos50 = lebar / 2f;
        float pos100 = lebar - catAngka.measureText("100");
        float yAngka = yTengah + tinggiBar + (5 * dp);

        canvas.drawText("0", 0, yAngka, catAngka);
        canvas.drawText("50", pos50 - (catAngka.measureText("50") / 2f), yAngka, catAngka);
        canvas.drawText("100", pos100, yAngka, catAngka);
    }
}
