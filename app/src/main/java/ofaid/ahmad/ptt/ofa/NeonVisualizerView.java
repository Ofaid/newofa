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
        // 💚 HIJAU MENYALA — terlihat di latar apapun
        neonPaint.setColor(Color.parseColor("#00FF41"));
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        neonPaint.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.OUTER));

        for (int i = 0; i < BARS_COUNT; i++) {
            barLevels[i] = 0f;
        }
    }

    // Sumber suara: kita sendiri atau teman
    public void setAudioLevel(float normalizedLevel) {
        // Masuk dari KIRI → geser ke KANAN
        for (int i = BARS_COUNT - 1; i > 0; i--) {
            barLevels[i] = barLevels[i - 1];
        }
        barLevels[0] = normalizedLevel; // Data baru di KIRI
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float totalGapWidth = (BARS_COUNT - 1) * gapRatio;
        barWidth = viewWidth / (BARS_COUNT + totalGapWidth);

        // Turun dari ATAS, KIRI → KANAN
        for (int i = 0; i < BARS_COUNT; i++) {
            float level = barLevels[i];
            float barHeight = viewHeight * level;

            float left = i * (barWidth + (barWidth * gapRatio));
            float right = left + barWidth;
            float top = 0;
            float bottom = barHeight;

            canvas.drawRect(left, top, right, bottom, neonPaint);
        }
    }
}
