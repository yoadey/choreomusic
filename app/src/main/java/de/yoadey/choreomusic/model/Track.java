package de.yoadey.choreomusic.model;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;

import lombok.Data;

@Entity(indexes = { @Index("position") })
@Data
public class Track {

    @Id
    private Long id;

    @NotNull
    private long fileId;

    @NotNull
    private long position;

    @NotNull
    private String label;

    public Track(long position, String label) {
        this.position = position;
        this.label = label;
    }

    @Generated(hash = 2121863005)
    public Track(Long id, long fileId, long position, @NotNull String label) {
        this.id = id;
        this.fileId = fileId;
        this.position = position;
        this.label = label;
    }

    @Generated(hash = 1672506944)
    public Track() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getFileId() {
        return this.fileId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public long getPosition() {
        return this.position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
