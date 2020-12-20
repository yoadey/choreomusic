package de.mayac.choreomusic.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.mpatric.mp3agic.EncodedText;
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
import java.util.Date;
import java.util.List;

import de.mayac.choreomusic.model.Track;

public class Id3TagsHandler {

    private static final String TITLE_ID = "TIT2";

    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private final Context context;
    private final ContentResolver contentResolver;

    public Id3TagsHandler(Context context, ContentResolver contentResolver) {
        this.context = context;
        this.contentResolver = contentResolver;
    }

    public List<Track> readChapters(Uri uri) {
        // Copy the file to the local directory for chapter extraction
        File localFile = getLocalFile(uri);
        try {
            List<Track> tracks = readChapters(localFile);
            return tracks;
        } finally {
            localFile.delete();
        }
    }

    public List<Track> readChapters(File file) {
        List<Track> tracks = new ArrayList<>();
        try {
            Mp3File mp3File = new Mp3File(file);
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
        } catch (UnsupportedTagException | IOException | InvalidDataException e) {
            Log.w("Id3Tag", "Error during id3 reading: " + e.getMessage(), e);
        }
        return tracks;
    }


    public void saveChapters(Uri uri, List<Track> tracks) {
        // Copy the file to the local directory for chapter extraction
        File localFile = getLocalFile(uri);
        try {
            saveChapters(localFile, tracks);
            // Save chapters to the original location
            String filename = getFileName(uri);
            File renamedFile = new File(localFile.getParent(), filename);
            localFile.renameTo(renamedFile);
            Uri songUri = FileProvider.getUriForFile(context, "de.mayac.choreomusic.provider",
                    renamedFile);

            Intent intentShareFile = new Intent(Intent.ACTION_SEND);
            intentShareFile.setType("audio/mpeg");
            intentShareFile.putExtra(Intent.EXTRA_STREAM, songUri);
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing File...");

            context.startActivity(Intent.createChooser(intentShareFile, "Share File"));
        } finally {
            localFile.delete();
        }
    }

    public void saveChapters(File file, List<Track> tracks) {
        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");
        try {
            Mp3File mp3File = new Mp3File(file);
            ID3v2 id3Tag = mp3File.getId3v2Tag();
            ArrayList<ID3v2ChapterFrameData> chapters = new ArrayList<>();
            for (int i = 0; i < tracks.size() - 1; i++) {
                Track track = tracks.get(i);
                Track nextTrack = tracks.get(i + 1);
                ID3v2ChapterFrameData frameData = new ID3v2ChapterFrameData(false, "" + i, track.getPosition(), nextTrack.getPosition(), -1, -1);

                // Save track label
                EncodedText encodedTitle = new EncodedText(track.getLabel());
                frameData.addSubframe(TITLE_ID, new ID3v2TextFrameData(false, encodedTitle));

                chapters.add(frameData);
            }
            id3Tag.setChapters(chapters);
            mp3File.save(tmpFile.getAbsolutePath());
        } catch (UnsupportedTagException | IOException | InvalidDataException | NotSupportedException e) {
            e.printStackTrace();
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

        File localFile = new File(filesDir, "toExtractChapters.mp3");
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

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
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
}
