package de.mayac.choreomusic.model;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.greenrobot.greendao.annotation.Generated;

@Entity(indexes = { @Index("position") })
@Data
public class Track {

    @Id
    private Long id;

    @NotNull
    private long fileId;

    @NotNull
    private int position;

    @NotNull
    private String label;

    public Track(int position, String label) {
        this.position = position;
        this.label = label;
    }

    @Generated(hash = 1217065924)
    public Track(Long id, long fileId, int position, @NotNull String label) {
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

    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
