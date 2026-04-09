package de.yoadey.choreomusic.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import de.yoadey.choreomusic.model.Song;

@Dao
public interface RoomSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Song song);

    @Query("SELECT * FROM SONG WHERE URI = :uri LIMIT 1")
    Song findByUri(String uri);

    @Query("SELECT * FROM SONG")
    List<Song> getAllSongs();

    @Query("DELETE FROM SONG WHERE _id = :songId")
    void deleteById(long songId);
}
