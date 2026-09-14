package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.BlurMaskFilter;
import android.util.AttributeSet;
import android.view.View;

public class NeonVisualizerView extends View {
    private static final int BARS_COUNT = 16;
    private final Paint neonPaint = new Paint();
    private float[] barLevels = new float[BARS_COUNT];
    private float barWidth;
    private final float gapRatio = 0.15f;

    public NeonVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 💚 HIJAU TUA MENYALA + EFEK GLOW
        neonPaint.setColor(Color.parseColor("#009933"));
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        neonPaint.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.OUTER));

        for (int i = 0; i < BARS_COUNT; i++) {
            barLevels[i] = 0f;
        }
    }

    public void setAudioLevel(float normalizedLevel) {
        // Geser ke KIRI — data baru masuk di KANAN
        for (int i = 0; i < BARS_COUNT - 1; i++) {
            barLevels[i] = barLevels[i + 1];
        }
        barLevels[BARS_COUNT - 1] = normalizedLevel;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float totalGapWidth = (BARS_COUNT - 1) * gapRatio;
        barWidth = viewWidth / (BARS_COUNT + totalGapWidth);

        // ✅ DIBALIK: Gambar dari KIRI ke KANAN + turun dari ATAS ke BAWAH
        for (int i = 0; i < BARS_COUNT; i++) {
            // Indeks dibalik — yang baru masuk tampil di KIRI
            int idx = BARS_COUNT - 1 - i;
            float level = barLevels[idx];
            float barHeight = viewHeight * level;

            float left = i * (barWidth + (barWidth * gapRatio));
            float right = left + barWidth;
            // ✅ Batang turun dari ATAS, bukan naik dari bawah
            float top = 0;
            float bottom = barHeight;

            canvas.drawRect(left, top, right, bottom, neonPaint);
        }
    }
}
