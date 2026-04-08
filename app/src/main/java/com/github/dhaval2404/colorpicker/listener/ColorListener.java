package com.github.dhaval2404.colorpicker.listener;

import org.jetbrains.annotations.NotNull;

public interface ColorListener {
    void onColorSelected(int color, @NotNull String colorHex);
}
