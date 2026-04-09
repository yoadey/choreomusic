package de.yoadey.choreomusic.utils;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;

import java.util.List;

import de.yoadey.choreomusic.db.AppDatabase;
import de.yoadey.choreomusic.db.RoomSongDao;
import de.yoadey.choreomusic.db.RoomTrackDao;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.service.PlaybackControl;
import lombok.Getter;
import lombok.Setter;

public class DatabaseHelper implements PlaybackControl.PlaybackListener, Playlist.PlaylistListener {

    private final RoomSongDao songDao;
    private final RoomTrackDao trackDao;

    @Setter
    @Getter
    private long currentFile;

    public DatabaseHelper(Context context) {
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, "tracks-db")
                .addMigrations(
                        AppDatabase.MIGRATION_1_2,
                        AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4
                )
                .allowMainThreadQueries()
                .build();
        songDao = db.songDao();
        trackDao = db.trackDao();
    }

    public void saveTrack(Track track) {
        if (track.getFileId() == 0) {
            track.setFileId(currentFile);
        }
        long id = trackDao.insert(track);
        if (track.getId() == null || track.getId() <= 0) {
            track.setId(id);
        }
    }

    public Track getTrack(int position) {
        return trackDao.getTrack(position, currentFile);
    }

    public List<Track> getAllTracks() {
        return trackDao.getTracksForSong(currentFile);
    }

    public int getTracksCount() {
        return trackDao.getTracksCount(currentFile);
    }

    public void deleteTrack(Track track) {
        if (track.getFileId() != currentFile) {
            return;
        }
        if (track.getId() != null) {
            trackDao.deleteById(track.getId());
        }
    }

    public Song findSongByUri(Uri file) {
        Song song = songDao.findByUri(file.toString());
        attachTracks(song);
        return song;
    }

    public void saveSong(Song song) {
        songDao.replaceSongWithTracks(song, song.getTracks());
    }

    public void saveTracks(List<Track> tracks) {
        tracks.forEach(this::saveTrack);
    }

    public List<Song> getAllSongs() {
        List<Song> songs = songDao.getAllSongs();
        songs.forEach(this::attachTracks);
        return songs;
    }

    @Override
    public void onSongChanged(Song newSong) {
        if (newSong == null) {
            this.currentFile = -1L;
        } else {
            this.currentFile = newSong.getId();
        }
    }

    @Override
    public void onPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        newTracks.forEach(this::saveTrack);
        deletedTracks.forEach(this::deleteTrack);
    }

    public void deleteSong(Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        trackDao.deleteBySongId(song.getId());
        songDao.deleteById(song.getId());
    }

    public void deleteSongByUri(Uri file) {
        Song songToDelete = findSongByUri(file);
        if (songToDelete != null) {
            deleteSong(songToDelete);
        }
    }

    private void attachTracks(Song song) {
        if (song != null && song.getId() != null) {
            song.setTracks(trackDao.getTracksForSong(song.getId()));
        }
    }
}
