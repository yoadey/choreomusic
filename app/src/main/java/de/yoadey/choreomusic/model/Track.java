package de.yoadey.choreomusic.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import lombok.Data;

@Entity(tableName = "TRACK", indices = {@Index(value = "POSITION")})
@Data
public class Track implements Comparable<Track> {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    private Long id;

    @ColumnInfo(name = "FILE_ID")
    private long fileId;

    @ColumnInfo(name = "POSITION")
    private long position;

    @NonNull
    @ColumnInfo(name = "LABEL")
    private String label;

    @ColumnInfo(name = "COLOR")
    private int color = 0;

    @Ignore
    public Track(long position, String label) {
        this.position = position;
        this.label = label;
    }

    @Ignore
    public Track(Long id, long fileId, long position, @NonNull String label, int color) {
        this.id = id;
        this.fileId = fileId;
        this.position = position;
        this.label = label;
        this.color = color;
    }

    public Track() {
    }

    @Override
    public int compareTo(Track o) {
        return Long.compare(this.position, o.position);
    }
}
