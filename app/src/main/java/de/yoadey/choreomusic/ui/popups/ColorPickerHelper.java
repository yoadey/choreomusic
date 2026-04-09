package de.yoadey.choreomusic.ui.popups;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ColorPickerHelper {

    public interface OnColorSelected {
        void onSelected(int color);
    }

    private ColorPickerHelper() {
    }

    public static void show(@NonNull Context context,
                            @NonNull int[] colors,
                            int currentColor,
                            @NonNull OnColorSelected callback) {
        String[] colorLabels = new String[colors.length];
        int checkedItem = -1;
        for (int i = 0; i < colors.length; i++) {
            colorLabels[i] = String.format("#%08X", colors[i]);
            if (colors[i] == currentColor) {
                checkedItem = i;
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(de.yoadey.choreomusic.R.string.edit_track_color_dialog)
                .setSingleChoiceItems(colorLabels, checkedItem, (dialog, which) -> {
                    callback.onSelected(colors[which]);
                    dialog.dismiss();
                })
                .show();
    }
}
