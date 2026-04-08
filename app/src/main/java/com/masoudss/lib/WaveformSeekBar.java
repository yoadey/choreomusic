package com.masoudss.lib;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WaveformSeekBar extends View {

    public interface OnProgressChangedListener {
        void onProgressChanged(WaveformSeekBar seekBar, Float progress, Boolean fromUser);
    }

    private int[] sample = new int[]{0};
    private int maxProgress = 100;
    private int progress = 0;
    @Nullable
    private OnProgressChangedListener onProgressChangedListener;

    public WaveformSeekBar(Context context) {
        super(context);
    }

    public WaveformSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WaveformSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSample(@NonNull int[] sample) {
        this.sample = sample.length == 0 ? new int[]{0} : sample;
        invalidate();
    }

    @NonNull
    public int[] getSample() {
        return sample;
    }

    public void setMaxProgress(int maxProgress) {
        this.maxProgress = Math.max(1, maxProgress);
    }

    public void setMaxProgress(long maxProgress) {
        if (maxProgress > Integer.MAX_VALUE) {
            this.maxProgress = Integer.MAX_VALUE;
        } else {
            this.maxProgress = Math.max(1, (int) maxProgress);
        }
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
        // No-op fallback implementation.
    }

    public void setWaveWidth(float waveWidth) {
        // No-op fallback implementation.
    }

    public void setWaveCornerRadius(float waveCornerRadius) {
        // No-op fallback implementation.
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float ratio = getWidth() > 0 ? (event.getX() / getWidth()) : 0;
            setProgress((int) (ratio * maxProgress));
            if (onProgressChangedListener != null) {
                onProgressChangedListener.onProgressChanged(this, (float) progress, true);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
