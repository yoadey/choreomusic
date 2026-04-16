package de.yoadey.choreomusic.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import de.yoadey.choreomusic.model.Track;

@Dao
public interface RoomTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Track track);

    @Query("SELECT * FROM TRACK WHERE POSITION = :position AND FILE_ID = :fileId LIMIT 1")
    Track getTrack(long position, long fileId);

    @Query("SELECT * FROM TRACK WHERE FILE_ID = :fileId ORDER BY POSITION ASC")
    List<Track> getTracksForSong(long fileId);

    @Query("SELECT COUNT(*) FROM TRACK WHERE FILE_ID = :fileId")
    int getTracksCount(long fileId);

    @Query("DELETE FROM TRACK WHERE _id = :trackId")
    void deleteById(long trackId);

    @Query("DELETE FROM TRACK WHERE FILE_ID = :fileId")
    void deleteBySongId(long fileId);
}
