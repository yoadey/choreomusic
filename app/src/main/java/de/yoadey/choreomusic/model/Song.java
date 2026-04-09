package de.yoadey.choreomusic.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Date;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(tableName = "SONG")
@Data
@EqualsAndHashCode(of = {"id", "uri"})
public class Song {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    private Long id;

    @NonNull
    @ColumnInfo(name = "URI")
    private String uri;

    @Ignore
    private List<Track> tracks;

    @ColumnInfo(name = "TITLE")
    private String title;

    @ColumnInfo(name = "LENGTH")
    private long length;

    @ColumnInfo(name = "AMPLITUDES")
    private byte[] amplitudes;

    @NonNull
    @ColumnInfo(name = "LAST_USED")
    private Date lastUsed;

    @ColumnInfo(name = "FILE_SUPPORTS_TRACKS")
    private boolean fileSupportsTracks;

    public Song() {
    }

    @Ignore
    public Song(Long id, @NonNull String uri, String title, long length, byte[] amplitudes,
                @NonNull Date lastUsed, boolean fileSupportsTracks) {
        this.id = id;
        this.uri = uri;
        this.title = title;
        this.length = length;
        this.amplitudes = amplitudes;
        this.lastUsed = lastUsed;
        this.fileSupportsTracks = fileSupportsTracks;
    }

    public Uri getParsedUri() {
        return Uri.parse(uri);
    }

    public boolean getFileSupportsTracks() {
        return fileSupportsTracks;
    }

    public int[] getIntAmplitudes() {
        if (amplitudes == null) {
            return new int[0];
        }
        IntBuffer intBuf = ByteBuffer.wrap(amplitudes)
                .order(ByteOrder.BIG_ENDIAN)
                .asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        return array;
    }

    public void setIntAmplitudes(int[] data) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);
        amplitudes = byteBuffer.array();
    }

    public synchronized void resetTracks() {
        tracks = null;
    }

    public void update() {
        // Compatibility no-op: persistence is handled through DatabaseHelper/Room DAOs.
    }
}
