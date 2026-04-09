package de.yoadey.choreomusic.ui.popups;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

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

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, colorLabels) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView textView = row.findViewById(android.R.id.text1);

                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.HORIZONTAL);
                container.setGravity(Gravity.CENTER_VERTICAL);
                int padding = dp(context, 8);
                container.setPadding(padding, padding, padding, padding);

                View swatch = new View(context);
                int size = dp(context, 22);
                LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(size, size);
                swatchParams.rightMargin = dp(context, 12);
                swatch.setLayoutParams(swatchParams);

                GradientDrawable swatchDrawable = new GradientDrawable();
                swatchDrawable.setShape(GradientDrawable.OVAL);
                swatchDrawable.setColor(colors[position]);
                swatchDrawable.setStroke(dp(context, 1), Color.DKGRAY);
                swatch.setBackground(swatchDrawable);

                textView.setText(colorLabels[position]);
                textView.setTextColor(Color.WHITE);
                textView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));

                container.addView(swatch);
                container.addView(textView);
                return container;
            }
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle(de.yoadey.choreomusic.R.string.edit_track_color_dialog)
                .setSingleChoiceItems(adapter, checkedItem, (dialog, which) -> {
                    callback.onSelected(colors[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
