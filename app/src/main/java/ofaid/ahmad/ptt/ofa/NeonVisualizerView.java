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
        neonPaint.setColor(Color.CYAN);
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        neonPaint.setMaskFilter(new BlurMaskFilter(12, BlurMaskFilter.Blur.OUTER));

        for (int i = 0; i < BARS_COUNT; i++) {
            barLevels[i] = 0f;
        }
    }

    public void setAudioLevel(float normalizedLevel) {
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

        for (int i = 0; i < BARS_COUNT; i++) {
            float level = barLevels[i];
            float barHeight = viewHeight * level;

            float left = i * (barWidth + (barWidth * gapRatio));
            float top = viewHeight - barHeight;
            float right = left + barWidth;
            float bottom = viewHeight;

            canvas.drawRect(left, top, right, bottom, neonPaint);
        }
    }
}
