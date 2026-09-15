/*Dibuat Oleh Ofaid/Ahmad 14-9-2026*/
package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class NeonVisualizerView extends View {
    private static final int BARS_COUNT = 10;
    private final Paint neonPaint = new Paint();
    private float[] barLevels = new float[BARS_COUNT];
    private float barWidth;
    private final float gapRatio = 0.15f;
    
    // 🔧 SENSITIVITAS DINAJKAN — tangkap suara kecil sekalipun
    private static final float FAKTOR_PENGKUAT = 4.0f;    // 4x lebih kuat
    private static final float BATAS_TERENDAH = 0.03f;   // tangkap suara lembut
    private static final float LANCAR = 0.75f;           // halus tapi cepat respons

    public NeonVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // 💙 BIRU LAUT TEGAS — TIDAK BURAM, TIDAK BLUR
        neonPaint.setColor(Color.parseColor("#00CCFF"));
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        // ❌ HAPUS BlurMaskFilter — biar TEGAS & JELAS
        // neonPaint.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.OUTER));

        for (int i = 0; i < BARS_COUNT; i++) {
            barLevels[i] = 0f;
        }
    }

    public void setAudioLevel(float normalizedLevel) {
        // ✅ PERKUAT SUARA
        float levelTerkuat = normalizedLevel * FAKTOR_PENGKUAT;
        
        // Jangan melebihi batas
        if (levelTerkuat > 1.0f) levelTerkuat = 1.0f;
        
        // Hanya tampilkan jika melewati batas minimal
        if (levelTerkuat < BATAS_TERENDAH) {
            levelTerkuat = 0f;
        }

        // ✅ Geser: masuk dari KIRI → ke KANAN
        for (int i = BARS_COUNT - 1; i > 0; i--) {
            barLevels[i] = barLevels[i - 1] * LANCAR;
        }
        barLevels[0] = levelTerkuat;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float totalGapWidth = (BARS_COUNT - 1) * gapRatio;
        barWidth = viewWidth / (BARS_COUNT + totalGapWidth);

        // ✅ Batang naik dari BAWAH ke ATAS — terlihat jelas
        for (int i = 0; i < BARS_COUNT; i++) {
            float level = barLevels[i];
            float barHeight = viewHeight * level;

            float left = i * (barWidth + (barWidth * gapRatio));
            float right = left + barWidth;
            float top = viewHeight - barHeight;  // naik dari bawah
            float bottom = viewHeight;

            canvas.drawRect(left, top, right, bottom, neonPaint);
        }
    }
}