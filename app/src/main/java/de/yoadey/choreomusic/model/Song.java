package de.yoadey.choreomusic.model;

import android.net.Uri;

import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.ToMany;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Date;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(of = {"id", "uri"})
public class Song {
    @Id
    private Long id;

    @NotNull
    private String uri;

    @ToMany(referencedJoinProperty = "fileId")
    private List<Track> tracks;

    private String title;

    private long length;

    private byte[] amplitudes;

    @NotNull
    private Date lastUsed;

    private boolean fileSupportsTracks;

    /**
     * Used to resolve relations
     */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;

    /**
     * Used for active entity operations.
     */
    @Generated(hash = 1369727947)
    private transient SongDao myDao;

    @Generated(hash = 1396130442)
    public Song(Long id, @NotNull String uri, String title, long length, byte[] amplitudes,
            @NotNull Date lastUsed, boolean fileSupportsTracks) {
        this.id = id;
        this.uri = uri;
        this.title = title;
        this.length = length;
        this.amplitudes = amplitudes;
        this.lastUsed = lastUsed;
        this.fileSupportsTracks = fileSupportsTracks;
    }

    @Generated(hash = 87031450)
    public Song() {
    }

    public Uri getParsedUri() {
        return Uri.parse(uri);
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getLength() {
        return this.length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public Date getLastUsed() {
        return this.lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }

    public byte[] getAmplitudes() {
        return this.amplitudes;
    }

    public void setAmplitudes(byte[] amplitudes) {
        this.amplitudes = amplitudes;
    }

    public boolean getFileSupportsTracks() {
        return fileSupportsTracks;
    }
    
    public void setFileSupportsTracks(boolean fileSupportsTracks) {
        this.fileSupportsTracks = fileSupportsTracks;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1825482532)
    public List<Track> getTracks() {
        if (tracks == null) {
            final DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            TrackDao targetDao = daoSession.getTrackDao();
            List<Track> tracksNew = targetDao._querySong_Tracks(id);
            synchronized (this) {
                if (tracks == null) {
                    tracks = tracksNew;
                }
            }
        }
        return tracks;
    }

    /**
     * Resets a to-many relationship, making the next get call to query for a fresh result.
     */
    @Generated(hash = 1878244390)
    public synchronized void resetTracks() {
        tracks = null;
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.delete(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#refresh(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 1942392019)
    public void refresh() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.refresh(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#update(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 713229351)
    public void update() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    public int[] getIntAmplitudes() {
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

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 767980484)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getSongDao() : null;
    }
}
