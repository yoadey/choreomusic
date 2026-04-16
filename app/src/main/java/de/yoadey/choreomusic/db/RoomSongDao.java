package de.yoadey.choreomusic.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;

@Dao
public interface RoomSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSong(Song song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTrack(Track track);

    @Query("SELECT * FROM SONG WHERE URI = :uri LIMIT 1")
    Song findByUri(String uri);

    @Query("SELECT * FROM SONG")
    List<Song> getAllSongs();

    @Query("DELETE FROM SONG WHERE _id = :songId")
    void deleteById(long songId);

    @Query("DELETE FROM TRACK WHERE FILE_ID = :songId")
    void deleteAllTracksBySongId(long songId);

    @Query("DELETE FROM TRACK WHERE FILE_ID = :songId AND _id NOT IN (:keepTrackIds)")
    void deleteTracksNotIn(long songId, List<Long> keepTrackIds);

    @Transaction
    default long replaceSongWithTracks(Song song, List<Track> tracks) {
        long persistedSongId = insertSong(song);
        if (song.getId() == null || song.getId() <= 0) {
            song.setId(persistedSongId);
        }

        List<Long> keepTrackIds = new ArrayList<>();
        if (tracks != null) {
            for (Track track : tracks) {
                track.setFileId(song.getId());
                long persistedTrackId = insertTrack(track);
                if (track.getId() == null || track.getId() <= 0) {
                    track.setId(persistedTrackId);
                }
                keepTrackIds.add(track.getId());
            }
        }

        if (keepTrackIds.isEmpty()) {
            deleteAllTracksBySongId(song.getId());
        } else {
            deleteTracksNotIn(song.getId(), keepTrackIds);
        }

        return song.getId();
    }
}
