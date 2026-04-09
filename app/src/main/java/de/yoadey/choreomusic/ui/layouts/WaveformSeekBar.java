package de.yoadey.choreomusic.ui.layouts;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.yoadey.choreomusic.R;

public class WaveformSeekBar extends View {

    private static final int GRAVITY_CENTER = 0;
    private static final int GRAVITY_TOP = 1;
    private static final int GRAVITY_BOTTOM = 2;

    public interface OnProgressChangedListener {
        void onProgressChanged(WaveformSeekBar seekBar, Float progress, Boolean fromUser);
    }

    private int[] sample = new int[]{0};
    private int maxProgress = 100;
    private int progress = 0;
    private float waveWidth = 4f;
    private float waveGap = 2f;
    private float waveMinHeight = 2f;
    private float waveCornerRadius = 0f;
    private float waveVisibleProgress = 0f;
    private int waveGravity = GRAVITY_CENTER;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

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
            waveMinHeight = a.getDimension(R.styleable.WaveformSeekBar_wave_min_height, waveMinHeight);
            waveCornerRadius = a.getDimension(R.styleable.WaveformSeekBar_wave_corner_radius, waveCornerRadius);
            waveGravity = a.getInt(R.styleable.WaveformSeekBar_wave_gravity, waveGravity);
            progress = a.getInt(R.styleable.WaveformSeekBar_wave_progress, progress);
            waveVisibleProgress = a.getFloat(R.styleable.WaveformSeekBar_wave_visible_progress, 0f);
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
        this.progress = Math.max(0, Math.min(progress, this.maxProgress));
        invalidate();
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, maxProgress));
        fireProgress(false);
        invalidate();
    }

    public void setOnProgressChanged(@Nullable OnProgressChangedListener listener) {
        this.onProgressChangedListener = listener;
    }

    public void setWaveGap(float waveGap) {
        this.waveGap = Math.max(0f, waveGap);
        invalidate();
    }

    public void setWaveWidth(float waveWidth) {
        this.waveWidth = Math.max(1f, waveWidth);
        invalidate();
    }

    public void setWaveCornerRadius(float waveCornerRadius) {
        this.waveCornerRadius = Math.max(0f, waveCornerRadius);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || sample.length == 0) {
            return;
        }

        int bars = Math.max(1, (int) (width / Math.max(1f, waveWidth + waveGap)));
        float progressRatio = maxProgress == 0 ? 0 : (progress / (float) maxProgress);
        int progressX = (int) (width * progressRatio);

        float visibleFraction = (waveVisibleProgress > 0f && maxProgress > 0)
                ? Math.min(1f, waveVisibleProgress / maxProgress)
                : 1f;
        int visibleSamples = Math.max(1, (int) (sample.length * visibleFraction));
        int centerSample = (int) (progressRatio * sample.length);
        int startSample = Math.max(0, centerSample - visibleSamples / 2);
        int endSample = Math.min(sample.length - 1, startSample + visibleSamples - 1);
        if (endSample - startSample + 1 < visibleSamples) {
            startSample = Math.max(0, endSample - visibleSamples + 1);
        }

        for (int i = 0; i < bars; i++) {
            float samplePos = startSample + (i / (float) Math.max(1, bars - 1)) * Math.max(1, (endSample - startSample));
            int sampleIndex = Math.min(sample.length - 1, Math.max(0, Math.round(samplePos)));
            float normalized = Math.min(1f, Math.abs(sample[sampleIndex]) / 255f);
            float barHeight = Math.max(waveMinHeight, normalized * height);

            float left = i * (waveWidth + waveGap);
            float top;
            if (waveGravity == GRAVITY_TOP) {
                top = 0;
            } else if (waveGravity == GRAVITY_BOTTOM) {
                top = height - barHeight;
            } else {
                top = (height - barHeight) / 2f;
            }

            barRect.set(left, top, left + waveWidth, top + barHeight);
            Paint p = left <= progressX ? progressPaint : basePaint;
            canvas.drawRoundRect(barRect, waveCornerRadius, waveCornerRadius, p);
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
            fireProgress(true);
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void fireProgress(boolean fromUser) {
        if (onProgressChangedListener != null) {
            onProgressChangedListener.onProgressChanged(this, (float) progress, fromUser);
        }
    }
}
