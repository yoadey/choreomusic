package de.mayac.choreomusic.utils;

import android.content.Context;
import android.net.Uri;

import org.greenrobot.greendao.database.Database;

import java.util.List;

import de.mayac.choreomusic.model.DaoMaster;
import de.mayac.choreomusic.model.DaoSession;
import de.mayac.choreomusic.model.FileInfo;
import de.mayac.choreomusic.model.FileInfoDao;
import de.mayac.choreomusic.model.Track;
import de.mayac.choreomusic.model.TrackDao;
import lombok.Getter;
import lombok.Setter;

public class DatabaseHelper {

    private final DaoSession daoSession;

    @Setter
    @Getter
    private long currentFile;

    public DatabaseHelper(Context context) {
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(context, "tracks-db");
        Database db = helper.getWritableDb();
        daoSession = new DaoMaster(db).newSession();
    }

    /**
     * If Track table has no data
     * default, insert 2 records.
     */
    public void createDefaultTracksIfNeed() {
        int count = this.getTracksCount();
        if (count == 0) {
            Track start = new Track(0,
                    "Start");
            Track end = new Track(Integer.MAX_VALUE,
                    "End");
            this.saveTrack(start);
            this.saveTrack(end);
        }
    }

    public void saveTrack(Track track) {
        if (track.getFileId() == 0) {
            track.setFileId(currentFile);
        }

        TrackDao trackDao = daoSession.getTrackDao();
        trackDao.save(track);
    }


    public Track getTrack(int position) {
        TrackDao trackDao = daoSession.getTrackDao();
        Track track = trackDao.queryBuilder() //
                .where(TrackDao.Properties.Position.eq(position), TrackDao.Properties.FileId.eq(currentFile)) //
                .unique();
        return track;
    }

    public List<Track> getAllTracks() {
        TrackDao trackDao = daoSession.getTrackDao();
        return trackDao.queryBuilder() //
                .where(TrackDao.Properties.FileId.eq(currentFile)) //
                .list();
    }

    public int getTracksCount() {
        TrackDao trackDao = daoSession.getTrackDao();
        return (int) trackDao.queryBuilder() //
                .where(TrackDao.Properties.FileId.eq(currentFile)) //
                .count();
    }

    public void deleteTrack(Track track) {
        if(track.getFileId() != currentFile) {
            // Only delete tracks, if they match the current file id!
            return;
        }
        TrackDao trackDao = daoSession.getTrackDao();
        trackDao.delete(track);
    }

    public FileInfo findFileInfoByUri(Uri file) {
        FileInfoDao fileInfoDao = daoSession.getFileInfoDao();
        return fileInfoDao.queryBuilder() //
                .where(FileInfoDao.Properties.Uri.eq(file.toString())) //
                .unique();
    }

    public void saveFileInfo(FileInfo fileInfo) {
        FileInfoDao fileInfoDao = daoSession.getFileInfoDao();
        fileInfoDao.save(fileInfo);
    }

    public void saveTracks(List<Track> tracks) {
        tracks.forEach(this::saveTrack);
    }
}
