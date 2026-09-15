package ofaid.ahmad.ptt.channel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.BlurMaskFilter;
import android.util.AttributeSet;
import android.view.View;

public class NeonVisualizerView extends View {

    private final Paint neonPaint = new Paint();
    private float mLevel = 0f;
    private float mSensitivitas = 1.0f;

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
        neonPaint.setMaskFilter(new BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setAudioLevel(float level) {
        mLevel = Math.max(0f, Math.min(1f, level * mSensitivitas));
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
        int batasHijau = (int)(lebar * 0.60f);
        int batasKuning = (int)(lebar * 0.85f);

        int panjang = (int)(lebar * mLevel);
        float tebal = tinggi * 0.6f;
        float yTengah = tinggi / 2f;

        // Hijau — 0% sampai 60%
        if (panjang > 0) {
            neonPaint.setColor(Color.parseColor("#00FF00"));
            if (panjang <= batasHijau) {
                canvas.drawRect(0, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
            } else {
                canvas.drawRect(0, yTengah - tebal/2, batasHijau, yTengah + tebal/2, neonPaint);
            }
        }

        // Kuning — 60% sampai 85%
        if (panjang > batasHijau) {
            neonPaint.setColor(Color.parseColor("#FFFF00"));
            int akhir = Math.min(panjang, batasKuning);
            canvas.drawRect(batasHijau, yTengah - tebal/2, akhir, yTengah + tebal/2, neonPaint);
        }

        // Merah — 85% sampai 100%
        if (panjang > batasKuning) {
            neonPaint.setColor(Color.parseColor("#FF0000"));
            canvas.drawRect(batasKuning, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
        }
    }
}
