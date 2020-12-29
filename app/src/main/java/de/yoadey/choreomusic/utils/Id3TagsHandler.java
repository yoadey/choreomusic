package de.yoadey.choreomusic.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.mpatric.mp3agic.EncodedText;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v2ChapterFrameData;
import com.mpatric.mp3agic.ID3v2Frame;
import com.mpatric.mp3agic.ID3v2TextFrameData;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;
import com.mpatric.mp3agic.UnsupportedTagException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.yoadey.choreomusic.model.Track;

/**
 * Helper class to write and save the tracks to the mp3 file, so it can be exported.
 */
public class Id3TagsHandler implements AutoCloseable {

    private static final String TITLE_ID = "TIT2";

    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private final Context context;
    private final ContentResolver contentResolver;

    private Mp3File mp3File;
    private File file;

    public Id3TagsHandler(Context context, ContentResolver contentResolver) {
        this.context = context;
        this.contentResolver = contentResolver;
    }

    /**
     * Copies the content of the uri to a local file and opens this local file. The local file is then returned
     *
     * @param uri the uri to load
     * @return the local file, which is created
     */
    public File openUri(Uri uri) {
        file = getLocalFile(uri);
        openFile(file);
        return file;
    }

    public void openFile(File file) {
        try {
            this.file = file;
            mp3File = new Mp3File(file);
        } catch (IOException | UnsupportedTagException | InvalidDataException e) {
            Log.w("Id3Tag", "Error during id3 reading: " + e.getMessage(), e);
        }
    }

    public List<Track> readChapters() {
        List<Track> tracks = new ArrayList<>();
        ID3v2 id3Tag = mp3File.getId3v2Tag();
        ArrayList<ID3v2ChapterFrameData> chapters = id3Tag.getChapters();
        // TODO: check if it is required, to include ChapterTOC also
        if (chapters != null) {
            for (ID3v2ChapterFrameData chapter : chapters) {
                String title = chapter.getSubframes().stream() //
                        .filter(subframe -> TITLE_ID.equals(subframe.getId())) //
                        .map(subframe -> toTextFrame(subframe)) //
                        .filter(textframe -> textframe != null) //
                        .map(textframe -> textframe.getText().toString()) //
                        .findAny().orElse(null);
                int startTime = chapter.getStartTime();
                Track track = new Track(startTime, title);
                tracks.add(track);
            }
        }
        return tracks;
    }

    public void saveChapters(List<Track> tracks) {

        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");
        try {
            ID3v2 id3Tag = mp3File.getId3v2Tag();
            ArrayList<ID3v2ChapterFrameData> chapters = new ArrayList<>();
            for (int i = 0; i < tracks.size() - 1; i++) {
                Track track = tracks.get(i);
                Track nextTrack = tracks.get(i + 1);
                ID3v2ChapterFrameData frameData = new ID3v2ChapterFrameData(false, "" + i, (int) track.getPosition(), (int) nextTrack.getPosition(), -1, -1);

                // Save track label
                EncodedText encodedTitle = new EncodedText(track.getLabel());
                frameData.addSubframe(TITLE_ID, new ID3v2TextFrameData(false, encodedTitle));

                chapters.add(frameData);
            }
            id3Tag.setChapters(chapters);
            mp3File.save(tmpFile.getAbsolutePath());
        } catch (IOException | NotSupportedException e) {
            Log.w("Id3Tag", "Error during id3 reading: " + e.getMessage(), e);
        }
        file.delete();
        tmpFile.renameTo(file);
    }

    @Nullable
    private ID3v2TextFrameData toTextFrame(ID3v2Frame subframe) {
        try {
            return new ID3v2TextFrameData(subframe.hasUnsynchronisation(), subframe.getData());
        } catch (InvalidDataException e) {
            return null;
        }
    }

    @NotNull
    private File getLocalFile(Uri uri) {
        File filesDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (filesDir == null) {
            filesDir = context.getFilesDir();
        }

        String fileName = Utils.getFileName(contentResolver, uri);
        File localFile = new File(filesDir, fileName);
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        try (InputStream is = contentResolver.openInputStream(uri);
             OutputStream os = new FileOutputStream(localFile)) {
            int n;
            while (EOF != (n = is.read(buffer))) {
                os.write(buffer, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return localFile;
    }

    /**
     * Retrieve the title of the file, either from ID3v2, ID3v1 or from filename, in this order.
     *
     * @return the title of the file.
     */
    public String getTitle() {
        assert mp3File != null;
        Optional<Mp3File> mp3FileOpt = Optional.of(this.mp3File);
        return mp3FileOpt
                .map(Mp3File::getId3v2Tag)
                .map(ID3v2::getTitle)
                .orElseGet(() -> mp3FileOpt
                        .map(Mp3File::getId3v1Tag)
                        .map(ID3v1::getTitle)
                        .orElseGet(() -> file.getName()));
    }

    public long getLength() {
        return mp3File.getLengthInMilliseconds();
    }

    @Override
    public void close() {
        try {
            file.delete();
        } finally {
            mp3File = null;
            file = null;
        }
    }
}
