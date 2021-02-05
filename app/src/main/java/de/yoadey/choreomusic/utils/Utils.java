package de.yoadey.choreomusic.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.ColorInt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Some static helper methods, which don't really belong into a specific other class
 */
public class Utils {

    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    /**
     * Returns the real filename for a given uri
     *
     * @param contentResolver the contentResolver
     * @param uri             the uri for which the filename should be searched
     * @return the filename of the file behind the uri
     */
    public static String getFileName(ContentResolver contentResolver, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }


    public static int getColor(Context context, int style, int attr) {
        @ColorInt int resultColor;
        int[] attrs = {attr};
        TypedArray ta = context.obtainStyledAttributes(style, attrs);

        if (ta != null) {
            resultColor = ta.getColor(0, Color.TRANSPARENT);
            ta.recycle();
            return resultColor;
        }
        return Color.TRANSPARENT;
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        int n;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while (EOF != (n = in.read(buffer))) {
            out.write(buffer, 0, n);
        }
    }
}
