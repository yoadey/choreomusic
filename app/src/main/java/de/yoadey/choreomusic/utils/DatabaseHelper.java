package de.yoadey.choreomusic.utils;

import android.content.Context;
import android.net.Uri;

import org.greenrobot.greendao.database.Database;

import java.util.List;

import de.yoadey.choreomusic.model.DaoMaster;
import de.yoadey.choreomusic.model.DaoSession;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.SongDao;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.model.TrackDao;
import lombok.Getter;
import lombok.Setter;

public class DatabaseHelper implements PlaybackControl.PlaybackListener, Playlist.PlaylistListener {

    private final DaoSession daoSession;

    @Setter
    @Getter
    private long currentFile;

    public DatabaseHelper(Context context) {
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(context, "tracks-db");
        Database db = helper.getWritableDb();
        //helper.onUpgrade(db, 1, 1);
        daoSession = new DaoMaster(db).newSession();
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

    public Song findFileInfoByUri(Uri file) {
        SongDao songDao = daoSession.getSongDao();
        return songDao.queryBuilder() //
                .where(SongDao.Properties.Uri.eq(file.toString())) //
                .unique();
    }

    public void saveSong(Song song) {
        SongDao songDao = daoSession.getSongDao();
        songDao.save(song);
    }

    public void saveTracks(List<Track> tracks) {
        tracks.forEach(this::saveTrack);
    }

    public List<Song> getAllSongs() {
        SongDao songDao = daoSession.getSongDao();
        return songDao.queryBuilder().list();
    }

    @Override
    public void songChanged(Song newSong) {
        this.currentFile = newSong.getId();
    }

    @Override
    public void notifyPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        newTracks.forEach(this::saveTrack);
        deletedTracks.forEach(this::deleteTrack);
    }
}
