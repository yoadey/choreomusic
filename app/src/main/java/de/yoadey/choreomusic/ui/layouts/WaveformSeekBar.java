package de.yoadey.choreomusic.ui.layouts;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.yoadey.choreomusic.R;

public class WaveformSeekBar extends View {

    public interface OnProgressChangedListener {
        void onProgressChanged(WaveformSeekBar seekBar, Float progress, Boolean fromUser);
    }

    private int[] sample = new int[]{0};
    private int maxProgress = 100;
    private int progress = 0;
    private float waveWidth = 4f;
    private float waveGap = 2f;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private OnProgressChangedListener onProgressChangedListener;

    public WaveformSeekBar(Context context) {
        this(context, null);
    }

    public WaveformSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveformSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        basePaint.setColor(0xFF666666);
        progressPaint.setColor(0xFFFFFFFF);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WaveformSeekBar);
            basePaint.setColor(a.getColor(R.styleable.WaveformSeekBar_wave_background_color, basePaint.getColor()));
            progressPaint.setColor(a.getColor(R.styleable.WaveformSeekBar_wave_progress_color, progressPaint.getColor()));
            waveWidth = a.getDimension(R.styleable.WaveformSeekBar_wave_width, waveWidth);
            waveGap = a.getDimension(R.styleable.WaveformSeekBar_wave_gap, waveGap);
            progress = a.getInt(R.styleable.WaveformSeekBar_wave_progress, progress);
            a.recycle();
        }
    }

    public void setSample(@NonNull int[] sample) {
        this.sample = sample.length == 0 ? new int[]{0} : sample;
        invalidate();
    }

    @NonNull
    public int[] getSample() {
        return sample;
    }

    public void setMaxProgress(long maxProgress) {
        this.maxProgress = (int) Math.max(1, Math.min(Integer.MAX_VALUE, maxProgress));
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, maxProgress));
        if (onProgressChangedListener != null) {
            onProgressChangedListener.onProgressChanged(this, (float) this.progress, false);
        }
        invalidate();
    }

    public void setOnProgressChanged(@Nullable OnProgressChangedListener listener) {
        this.onProgressChangedListener = listener;
    }

    public void setWaveGap(float waveGap) {
        this.waveGap = waveGap;
        invalidate();
    }

    public void setWaveWidth(float waveWidth) {
        this.waveWidth = waveWidth;
        invalidate();
    }

    public void setWaveCornerRadius(float waveCornerRadius) {
        // drawn with simple lines currently
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int bars = Math.max(1, (int) (width / Math.max(1f, waveWidth + waveGap)));
        float progressRatio = maxProgress == 0 ? 0 : (progress / (float) maxProgress);
        int progressX = (int) (width * progressRatio);

        for (int i = 0; i < bars; i++) {
            int sampleIndex = (int) ((i / (float) bars) * (sample.length - 1));
            int value = Math.max(1, Math.abs(sample[sampleIndex]));
            float normalized = Math.min(1f, value / 255f);
            float barHeight = Math.max(2f, normalized * height);
            float left = i * (waveWidth + waveGap);
            float top = (height - barHeight) / 2f;
            Paint p = left <= progressX ? progressPaint : basePaint;
            canvas.drawRect(left, top, left + waveWidth, top + barHeight, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float ratio = getWidth() > 0 ? event.getX() / getWidth() : 0f;
            progress = (int) (Math.max(0f, Math.min(1f, ratio)) * maxProgress);
            if (onProgressChangedListener != null) {
                onProgressChangedListener.onProgressChanged(this, (float) progress, true);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
