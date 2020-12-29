package de.yoadey.choreomusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.PlaybackControl.PlaybackListener;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.ui.AboutActivity;
import de.yoadey.choreomusic.ui.SongsTracksAdapter;
import de.yoadey.choreomusic.ui.popups.PrePostDialogFragment;
import de.yoadey.choreomusic.ui.popups.SpeedDialogFragment;
import de.yoadey.choreomusic.utils.DatabaseHelper;
import de.yoadey.choreomusic.utils.Id3TagsHandler;
import de.yoadey.choreomusic.utils.Utils;

public class MainActivity extends AppCompatActivity implements PlaybackListener {

    /**
     * Number of milliseconds to delay, before the background thread is called again to update the UI components and the loop
     */
    public static final int BACKGROUND_THREAD_DELAY = 100;
    private static final int OPEN_FILE_CODE = 1;
    private static final String SP_FILE_KEY = "MUSIC_FILE";
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private MediaBrowserCompat mMediaBrowser;
    private DatabaseHelper databaseHelper;
    private PlaybackControl playbackControl;
    private SongsTracksAdapter songsTracksAdapter;
    private Playlist playlist;
    private List<Song> songs;
    private boolean threadRunning;
    private Id3TagsHandler id3Handler;

    private final ServiceConnection playbackControlConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            playbackControl = ((PlaybackControl.LocalBinder) iBinder).getInstance();
            playbackControl.addPlaybackListener(MainActivity.this);
            playbackControl.addPlaybackListener(databaseHelper);
            playbackControl.setSpeed(1.0f);
            playlist = playbackControl.getPlaylist();
            playlist.addPlaylistListener(databaseHelper);
            reloadLastFile();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            playbackControl.deletePlaybackListener(MainActivity.this);
            playbackControl.deletePlaybackListener(databaseHelper);
            playlist.deletePlaylistListener(databaseHelper);
            playbackControl = null;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_FILE_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                assert data != null;
                Uri file = data.getData();
                ContentResolver resolver = getContentResolver();
                resolver.takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openFile(file);
            }
        }
    }

    public void openFile(Uri file) {
        // Save currently opened file
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SP_FILE_KEY, file.toString());
        editor.apply();

        // Save/update fileinfos
        Song song = databaseHelper.findFileInfoByUri(file);
        boolean isNew = song == null;
        if (isNew) {
            song = new Song();
            song.setUri(file.toString());
            try {
                id3Handler.openUri(file);
                song.setTitle(id3Handler.getTitle());
                song.setLength(id3Handler.getLength());
                song.setTracks(id3Handler.readChapters());
            } finally {
                id3Handler.close();
            }
        }
        song.setLastUsed(new Date());
        databaseHelper.saveSong(song);
        if (isNew) {
            databaseHelper.saveTracks(song.getTracks());
        }

        if (!songs.contains(song)) {
            songs.add(song);
            songs.sort((s1, s2) -> s1.getTitle().compareTo(s2.getTitle()));
        }

        // Open file
        playbackControl.openSong(song);
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        databaseHelper = new DatabaseHelper(getApplicationContext());
        id3Handler = new Id3TagsHandler(this, getContentResolver());
        songs = new ArrayList<>();
        songs.addAll(databaseHelper.getAllSongs());
        startForegroundService(new Intent(getApplicationContext(), PlaybackControl.class));
        bindService(new Intent(this, PlaybackControl.class), playbackControlConnection, Context.BIND_AUTO_CREATE);

        setContentView(R.layout.activity_main);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Slider seekBar = findViewById(R.id.progressSlider);
        seekBar.addOnChangeListener((slider, progress, fromUser) -> playbackControl.ifInitialized(() -> {
            if (fromUser) {
                TextView time = findViewById(R.id.time);
                time.setText(String.format("%02d:%02d", (int) (progress / 60000), (int) (progress / 1000 % 60)));
                playbackControl.seekTo((int) progress);
            }
        }));

        MaterialButton play = findViewById(R.id.playpause);
        play.setOnClickListener(view -> playbackControl.ifInitialized(() -> {
            if (playbackControl.isPlaying()) {
                playbackControl.pause();
            } else {
                playbackControl.play();
            }
        }));

        View stop = findViewById(R.id.stop);
        stop.setOnClickListener(view -> playbackControl.ifInitialized(() -> playbackControl.stop()));

        View loop = findViewById(R.id.loop);
        loop.setOnClickListener(view -> {
            PrePostDialogFragment dialogFragment = new PrePostDialogFragment(playbackControl);
            dialogFragment.show(getSupportFragmentManager(), "OpenPopup");
        });

        View speed = findViewById(R.id.speed);
        speed.setOnClickListener(view -> {
            SpeedDialogFragment dialogFragment = new SpeedDialogFragment(playbackControl);
            dialogFragment.show(getSupportFragmentManager(), "OpenPopup");
        });

        View bookmark = findViewById(R.id.addMark);
        bookmark.setOnClickListener(view -> playbackControl.ifInitialized(() -> {
            long bookmarkPosition = playbackControl.getCurrentPosition();
            Track track = new Track(bookmarkPosition, "Track " + (playlist.getTracks().size() - 1));
            playlist.addTrack(track);
        }));

        View next = findViewById(R.id.next);
        next.setOnClickListener(e -> {
            Track currentTrack = playbackControl.getCurrentTrack();
            Track nextTrack = playlist.getNextTrack(currentTrack);
            playbackControl.seekTo(nextTrack);
        });
        View previous = findViewById(R.id.previous);
        previous.setOnClickListener(e -> {
            Track currentTrack = playbackControl.getCurrentTrack();
            Track previousTrack = playlist.getPreviousTrack(currentTrack);
            playbackControl.seekTo(previousTrack);
        });

        ViewPager2 viewPager = findViewById(R.id.main_area);
        songsTracksAdapter = new SongsTracksAdapter(this, songs);
        viewPager.setAdapter(songsTracksAdapter);
        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText(R.string.songs);
                    } else {
                        tab.setText(R.string.tracks);
                    }
                }
        ).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.action_open) {
            Intent openFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openFile.setType("audio/mpeg");
            startActivityForResult(openFile, OPEN_FILE_CODE);
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            saveAndShareFile();
            return true;
        } else if (item.getItemId() == R.id.action_reload) {
            try {
                id3Handler.openUri(playbackControl.getCurrentSong().getParsedUri());
                List<Track> tracks = id3Handler.readChapters();
                playlist.reset(tracks);
            } finally {
                id3Handler.close();
            }
            return true;
//      } else if (item.getItemId() == R.id.action_settings) {
//                startActivity(new Intent(this, SettingsActivity.class));
//                return true;
        } else if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void saveAndShareFile() {
        Uri uri = playbackControl.getCurrentSong().getParsedUri();
        File file = id3Handler.openUri(uri);
        try {
            id3Handler.saveChapters(playlist.getTracks());
            // Save chapters to the original location
            String filename = Utils.getFileName(getContentResolver(), uri);
            File renamedFile = new File(file.getParent(), filename);
            if (file.renameTo(renamedFile)) {
                Uri songUri = FileProvider.getUriForFile(this, "de.yoadey.choreomusic.provider",
                        renamedFile);

                Intent intentShareFile = new Intent(Intent.ACTION_SEND);
                intentShareFile.setType("audio/mpeg");
                intentShareFile.putExtra(Intent.EXTRA_STREAM, songUri);
                intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
                intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing File...");

                startActivity(Intent.createChooser(intentShareFile, "Share File"));
            } else {
                Log.w("MAIN", "Could not rename file for sharing in path " + file.getAbsolutePath());
            }
        } finally {
            if (!file.delete()) {
                Log.w("MAIN", "Could not delete file " + file.getAbsolutePath());
            }
        }
    }


    /**
     * Background thread to update the slider and timer
     */
    private void startLoopingThread() {
        synchronized (threadRunningLock) {
            if (threadRunning) {
                return;
            }

            threadRunning = true;
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                updateSlider();

                // Restart handler
                synchronized (threadRunningLock) {
                    if (playbackControl.isPlaying()) {
                        handler.postDelayed(this, BACKGROUND_THREAD_DELAY);
                    } else {
                        threadRunning = false;
                    }
                }
            }
        }, BACKGROUND_THREAD_DELAY);
    }

    @SuppressLint("DefaultLocale")
    private void updateSlider() {
        long position = playbackControl.getCurrentPosition();
        Slider slider = findViewById(R.id.progressSlider);
        position = Math.min(Math.max(0l, position), (long) slider.getValueTo());
        slider.setValue(position);
        TextView time = findViewById(R.id.time);
        time.setText(String.format("%02d:%02d", position / 60000, position / 1000 % 60));
    }

    /**
     * Loads the last opened file from the preferences
     */
    private void reloadLastFile() {
        // Reloading of last file should happen asynchronously, so it doesn't block the UI thread
        handler.post(() -> {
            if (playbackControl.getCurrentSong() != null) {
                openFile(playbackControl.getCurrentSong().getParsedUri());
            } else {
                SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
                String fileUri = sharedPreferences.getString(SP_FILE_KEY, null);
                if (fileUri != null) {
                    Uri uri = Uri.parse(fileUri);
                    openFile(uri);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackControl != null) {
            unbindService(playbackControlConnection);
        }
        if (songsTracksAdapter != null) {
            songsTracksAdapter.onDestroy();
        }
    }

    @Override
    public void songChanged(Song newSong) {
        Slider slider = findViewById(R.id.progressSlider);
        slider.setValueTo(newSong.getLength());
        updateSlider();
    }

    @Override
    public void trackChanged(Track newTrack) {
        updateSlider();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        MaterialButton play = findViewById(R.id.playpause);
        if (isPlaying) {
            play.setIconResource(R.drawable.baseline_pause_24);
            startLoopingThread();
        } else {
            play.setIconResource(R.drawable.baseline_play_arrow_24);
        }
    }
}