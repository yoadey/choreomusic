package de.yoadey.choreomusic.utils.metadata;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.utils.MetadataHandler;

public class DefaultMetadataHandler implements MetadataHandler {

    private final Context context;
    private File file;
    private long duration;

    public DefaultMetadataHandler(Context context, File file) {
        this.context = context;
        this.file = file;
        openFile(file);
    }

    private void openFile(File file) {
        this.file = file;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(context, Uri.fromFile(file));
        String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        duration = Long.parseLong(durationStr);
    }

    public String getTitle() {
        return file.getName();
    }

    public long getLength() {
        return duration;
    }

    @Override
    public boolean supportsChapters() {
        return false;
    }

    public List<Track> readChapters() {
        // Only MP3 supports embedded chapters/tracks
        return new ArrayList<>();
    }

    public void saveChapters(List<Track> tracks) {
        throw new UnsupportedOperationException("Save chapters not supported for this file type!");
    }

    public InputStream getInputStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            Log.w("MetadataHandler", "Exception during opening file: " + e.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @Override
    public void close() {
        file = null;
    }
}