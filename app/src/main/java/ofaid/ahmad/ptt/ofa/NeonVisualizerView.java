package ofaid.ahmad.ptt.ofa; // sesuaikan paketmu

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class NeonVisualizerView extends View {

    private final Paint neonPaint = new Paint();
    private float mLevel = 0f;
    private float mSensitivitas = 1.8f; // ← sudah dinaikkan, lebih peka
    private static final float LENYAP_CEPAT = 0.15f; // ← makin besar makin cepat hilang

    public NeonVisualizerView(Context context) {
        super(context);
        init();
    }

    public NeonVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NeonVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true); // ✅ Garis halus & tajam, TANPA BLUR
        // ❌ Blur sudah dihapus — warna jadi cetak jelas!
    }

    public void setAudioLevel(float level) {
        float target = Math.max(0f, Math.min(1f, level * mSensitivitas));
        
        // ✅ Saat diam → turun cepat ke nol
        if (target >= mLevel) {
            mLevel = target; // Naik cepat
        } else {
            mLevel = Math.max(0f, mLevel - LENYAP_CEPAT); // Turun cepat
        }
        invalidate();
    }

    public void setSensitivitas(float faktor) {
        mSensitivitas = faktor;
    }

   @Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int lebar = getWidth();
    int tinggi = getHeight();
    
    int batasHijau = (int)(lebar * 0.45f);  // Hijau sampai 45%
    int batasKuning = (int)(lebar * 0.70f); // Kuning sampai 70%

    int panjang = (int)(lebar * mLevel);
    float tebal = tinggi * 0.6f;
    float yTengah = tinggi / 2f;

    // Hijau
    if (panjang > 0) {
        neonPaint.setColor(Color.parseColor("#00FF00"));
        if (panjang <= batasHijau) {
            canvas.drawRect(0, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
        } else {
            canvas.drawRect(0, yTengah - tebal/2, batasHijau, yTengah + tebal/2, neonPaint);
        }
    }

    // Kuning
    if (panjang > batasHijau) {
        neonPaint.setColor(Color.parseColor("#FFFF00"));
        int akhir = Math.min(panjang, batasKuning);
        canvas.drawRect(batasHijau, yTengah - tebal/2, akhir, yTengah + tebal/2, neonPaint);
    }

    // Merah
    if (panjang > batasKuning) {
        neonPaint.setColor(Color.parseColor("#FF0000"));
        canvas.drawRect(batasKuning, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
    }
}

