package ofaid.ahmad.ptt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OfaVisualizerBar extends View {

    private byte[] mBytes;
    private Paint mPaint = new Paint();

    public OfaVisualizerBar(Context context) {
        super(context);
        init();
    }
    public OfaVisualizerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public OfaVisualizerBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mPaint.setColor(Color.parseColor("#39FF14"));
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBytes == null || mBytes.length == 0) return;

        int jumlahBatang = 16;
        float lebarTotal = getWidth();
        float tinggiTotal = getHeight();
        float lebarBatang = lebarTotal / (jumlahBatang * 1.4f);

        for (int i = 0; i < jumlahBatang; i++) {
            int posisiData = i * mBytes.length / jumlahBatang;
            int nilai = (mBytes[posisiData] & 0xFF) - 128;
            if (nilai < 0) nilai = -nilai;

            float tinggiBatang = (nilai / 128f) * tinggiTotal * 0.9f;
            float x = i * lebarTotal / jumlahBatang + lebarBatang * 0.2f;
            float yAtas = (tinggiTotal - tinggiBatang) / 2f;

            canvas.drawRect(x, yAtas, x + lebarBatang, yAtas + tinggiBatang, mPaint);
        }
    }
}
