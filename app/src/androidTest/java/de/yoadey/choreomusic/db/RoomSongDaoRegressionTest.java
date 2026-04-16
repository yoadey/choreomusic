package de.yoadey.choreomusic.db;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;

@RunWith(AndroidJUnit4.class)
public class RoomSongDaoRegressionTest {

    private AppDatabase db;
    private RoomSongDao songDao;
    private RoomTrackDao trackDao;

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        songDao = db.songDao();
        trackDao = db.trackDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void replaceChildren_removesDeletedTracks() {
        Song song = createSong("content://song/1");
        Track a = new Track(1000, "A");
        Track b = new Track(2000, "B");
        song.setTracks(Arrays.asList(a, b));

        songDao.replaceSongWithTracks(song, song.getTracks());

        song.setTracks(Arrays.asList(a));
        songDao.replaceSongWithTracks(song, song.getTracks());

        List<Track> tracks = trackDao.getTracksForSong(song.getId());
        assertEquals(1, tracks.size());
        assertEquals("A", tracks.get(0).getLabel());
    }

    @Test
    public void replaceChildren_keepsReorderedTracks() {
        Song song = createSong("content://song/2");
        Track first = new Track(1000, "first");
        Track second = new Track(2000, "second");
        song.setTracks(Arrays.asList(first, second));
        songDao.replaceSongWithTracks(song, song.getTracks());

        first.setPosition(3000);
        second.setPosition(1000);
        song.setTracks(Arrays.asList(second, first));
        songDao.replaceSongWithTracks(song, song.getTracks());

        List<Track> tracks = trackDao.getTracksForSong(song.getId());
        assertEquals(2, tracks.size());
        assertEquals("second", tracks.get(0).getLabel());
        assertEquals("first", tracks.get(1).getLabel());
    }

    @Test
    public void replaceChildren_multipleSaves_doNotDuplicateSongOrTracks() {
        Song song = createSong("content://song/3");
        Track a = new Track(1000, "A");
        Track b = new Track(2000, "B");

        song.setTracks(Arrays.asList(a, b));
        songDao.replaceSongWithTracks(song, song.getTracks());
        songDao.replaceSongWithTracks(song, song.getTracks());

        assertEquals(1, songDao.getAllSongs().size());
        assertEquals(2, trackDao.getTracksForSong(song.getId()).size());
    }

    private Song createSong(String uri) {
        Song song = new Song();
        song.setUri(uri);
        song.setTitle("test");
        song.setLength(10_000);
        song.setLastUsed(new Date());
        song.setFileSupportsTracks(true);
        return song;
    }
}
