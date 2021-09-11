package de.yoadey.choreomusic.utils;

import android.content.Context;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.utils.metadata.DefaultMetadataHandler;
import de.yoadey.choreomusic.utils.metadata.Mp3MetadataHandler;

/**
 * Helper class to read, write and save the tracks to the mp3 file, so it can be exported.
 */
public interface MetadataHandler extends AutoCloseable {

    static MetadataHandler open(Context context, File file) {
        if(file.getName().endsWith(".mp3")) {
            return new Mp3MetadataHandler(file);
        }

        return new DefaultMetadataHandler(context, file);
    }

    /**
     * Retrieve the title of the file, either from ID3v2, ID3v1 or from filename, in this order.
     *
     * @return the title of the file.
     */
    String getTitle();

    long getLength();

    InputStream getInputStream();

    boolean supportsChapters();

    List<Track> readChapters();

    void saveChapters(List<Track> tracks);

    void close();
}
