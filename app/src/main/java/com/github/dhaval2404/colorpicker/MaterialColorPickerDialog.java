package com.github.dhaval2404.colorpicker;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.Nullable;

import com.github.dhaval2404.colorpicker.listener.ColorListener;
import com.github.dhaval2404.colorpicker.model.ColorShape;

import java.util.Locale;

public class MaterialColorPickerDialog {

    private final Context context;
    private final int[] colors;
    private final int defaultColor;
    @Nullable
    private final ColorListener colorListener;

    private MaterialColorPickerDialog(Context context, int[] colors, int defaultColor, @Nullable ColorListener colorListener) {
        this.context = context;
        this.colors = colors;
        this.defaultColor = defaultColor;
        this.colorListener = colorListener;
    }

    public void show() {
        int selectedColor = defaultColor;
        if (selectedColor == 0 && colors.length > 0) {
            selectedColor = colors[0];
        }
        if (colorListener != null) {
            colorListener.onColorSelected(selectedColor, String.format(Locale.US, "#%08X", selectedColor));
        }
    }

    public static class Builder {
        private final Context context;
        private int[] colors = new int[0];
        private int defaultColor = 0;
        @Nullable
        private ColorListener colorListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(int titleResId) {
            return this;
        }

        public Builder setColorShape(ColorShape colorShape) {
            return this;
        }

        public Builder setColorRes(@ArrayRes int[] colors) {
            this.colors = colors;
            return this;
        }

        public Builder setDefaultColor(int defaultColor) {
            this.defaultColor = defaultColor;
            return this;
        }

        public Builder setColorListener(ColorListener colorListener) {
            this.colorListener = colorListener;
            return this;
        }

        public MaterialColorPickerDialog build() {
            return new MaterialColorPickerDialog(context, colors, defaultColor, colorListener);
        }

        public MaterialColorPickerDialog show() {
            MaterialColorPickerDialog dialog = build();
            dialog.show();
            return dialog;
        }
    }
}
