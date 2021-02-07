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

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.masoudss.lib.WaveformSeekBar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
import lombok.Getter;

public class MainActivity extends AppCompatActivity implements PlaybackListener, ServiceConnection {

    /**
     * Number of milliseconds to delay, before the background thread is called again to update the UI components and the loop
     */
    public static final int BACKGROUND_THREAD_DELAY = 100;
    /**
     * Code to identify the open file intent in the callback
     */
    private static final int OPEN_FILE_CODE = 1;
    /**
     * Code to identify the save file intent in the callback
     */
    private static final int SAVE_FILE_CODE = 2;
    /**
     * Shared property name for the last opened music file
     */
    private static final String SP_FILE_KEY = "MUSIC_FILE";
    /**
     * Background thread handler to update the UI when the music is running
     */
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private boolean threadRunning;

    private DatabaseHelper databaseHelper;
    private SongsTracksAdapter songsTracksAdapter;

    @Getter
    private PlaybackControl playbackControl;

    private List<Song> songs;
    private Playlist playlist;

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    public int[] getWaveformData() {
        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        return waveformSeekBar.getSample();
    }

    /*-----------------------------------------------------
     *
     * Lifecycle methods
     *
     *-----------------------------------------------------*/

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        databaseHelper = new DatabaseHelper(getApplicationContext());
        songs = new ArrayList<>();
        songs.addAll(databaseHelper.getAllSongs());
        sendCommandToService(PlaybackControl.START_ACTION);
        bindService(new Intent(this, PlaybackControl.class), this, Context.BIND_AUTO_CREATE);

        setContentView(R.layout.activity_main);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        waveformSeekBar.setSample(new int[] {0});
        handler.postDelayed(() -> initializeWaveform(waveformSeekBar), 10);
        waveformSeekBar.setOnProgressChanged(this::onSliderChanged);

        MaterialButton play = findViewById(R.id.playpause);
        play.setOnClickListener(v -> onPlayChanged());

        View stop = findViewById(R.id.stop);
        stop.setOnClickListener(view -> ifPlaybackControlInitialized(() -> playbackControl.stop()));

        View loop = findViewById(R.id.loop);
        loop.setOnClickListener(this::openLoopDialog);

        View speed = findViewById(R.id.speed);
        speed.setOnClickListener(v -> openSpeedDialog());

        View bookmark = findViewById(R.id.addMark);
        bookmark.setOnClickListener(v -> addTrack());

        View next = findViewById(R.id.next);
        next.setOnClickListener(v -> nextTrack());
        View previous = findViewById(R.id.previous);
        previous.setOnClickListener(v -> previousTrack());

        ViewPager2 viewPager = findViewById(R.id.main_area);
        songsTracksAdapter = new SongsTracksAdapter(this, songs);
        viewPager.setAdapter(songsTracksAdapter);
        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(pos == 0 ? R.string.songs : R.string.tracks)
        ).attach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.playbackControl != null) {
            unbindService(this);
        }
        if (songsTracksAdapter != null) {
            songsTracksAdapter.onDestroy();
        }
        sendCommandToService(PlaybackControl.STOP_ACTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            ContentResolver contentResolver = getContentResolver();
            assert data != null;
            Uri file = data.getData();
            if (requestCode == OPEN_FILE_CODE) {
                contentResolver.takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openFile(file);
            } else if (requestCode == SAVE_FILE_CODE) {
                File localFile = playbackControl.getLocalFile();
                try (Id3TagsHandler id3Handler = new Id3TagsHandler(localFile)) {
                    id3Handler.saveChapters(playlist.getTracks());
                    try(InputStream in = id3Handler.getInputStream();
                        OutputStream out = contentResolver.openOutputStream(file)) {
                        Utils.copyStream(in, out);
                    }
                } catch (IOException e) {
                    Log.w(MainActivity.class.getName(), "Error during saving file: ", e);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this, R.style.Popup)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_description)
                .setNegativeButton(R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    MainActivity.this.finish();
                })
                .setPositiveButton(R.string.no, (dialog, which) -> dialog.dismiss())
                .show();
    }

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

    public void openFile(Uri file) {
        // Check whether file still exists
        boolean stillExists = getContentResolver().getPersistedUriPermissions()
                .stream()
                .anyMatch(element -> Objects.equals(element.getUri(), file));

        // Save/update/delete fileinfos
        Song song = databaseHelper.findFileInfoByUri(file);
        if (!stillExists) {
            if (song != null) {
                databaseHelper.deleteSong(song);
            }
            return;
        }
        boolean isNew = song == null;
        if (isNew) {
            song = new Song();
            song.setUri(file.toString());
            File localFile = playbackControl.getLocalFile(file);
            try (Id3TagsHandler id3Handler = new Id3TagsHandler(localFile)) {
                song.setTitle(id3Handler.getTitle());
                song.setLength(id3Handler.getLength());
                song.setTracks(id3Handler.readChapters());
            } finally {
                localFile.delete();
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
        if (playbackControl != null) {
            playbackControl.openSong(song);
        }
    }

    /**
     * Loads the last opened file from the preferences or from the playback control, if they are
     * initialized
     */
    private void reloadLastFile() {
        // Reloading of last file should happen asynchronously, so it doesn't block the UI thread
        handler.post(() -> {
            if (playbackControl.getCurrentSong() != null) {
                openFile(playbackControl.getCurrentSong().getParsedUri());
                onSongChanged(playbackControl.getCurrentSong());
                onIsPlayingChanged(playbackControl.isPlaying());
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

    private void initializeWaveform(WaveformSeekBar waveformSeekBar) {
        ifPlaybackControlInitialized(() -> {
            File localFile = playbackControl.getLocalFile();
            try {
                waveformSeekBar.setSampleFrom(localFile);
            } catch(Exception e) {
                waveformSeekBar.setSample(new int[] {1});
            }
        });
    }

    /*-----------------------------------------------------
     *
     * Button actions
     *
     *-----------------------------------------------------*/

    private void previousTrack() {
        ifPlaybackControlInitialized(() -> {
            Track currentTrack = playbackControl.getCurrentTrack();
            Track previousTrack = playlist.getPreviousTrack(currentTrack);
            playbackControl.seekTo(previousTrack);
        });
    }

    private void nextTrack() {
        ifPlaybackControlInitialized(() -> {
            Track currentTrack = playbackControl.getCurrentTrack();
            Track nextTrack = playlist.getNextTrack(currentTrack);
            playbackControl.seekTo(nextTrack);
        });
    }

    private void onPlayChanged() {
        ifPlaybackControlInitialized(() -> {
            if (playbackControl.isPlaying()) {
                playbackControl.pause();
            } else {
                playbackControl.play();
            }
        });
    }

    private void addTrack() {
        ifPlaybackControlInitialized(() -> {
            long bookmarkPosition = playbackControl.getCurrentPosition();
            // Find the highest track number and set the title of the new track to the next value
            int nextTrack = playlist.getTracks().stream()
                    .map(Track::getLabel)
                    .filter(track -> track.matches("Track \\d+"))
                    .map(track -> Integer.parseInt(track.replaceAll("Track (\\d+)", "$1")))
                    .max(Integer::compareTo).orElse(0) + 1;
            Track track = new Track(bookmarkPosition, "Track " + nextTrack);
            playlist.addTrack(track);
        });
    }

    private void openLoopDialog(View view) {
        PrePostDialogFragment dialogFragment = new PrePostDialogFragment(playbackControl);
        dialogFragment.show(getSupportFragmentManager(), "OpenPopup");
    }

    private void openSpeedDialog() {
        SpeedDialogFragment dialogFragment = new SpeedDialogFragment(playbackControl);
        dialogFragment.show(getSupportFragmentManager(), "OpenPopup");
    }

    private void onSliderChanged(WaveformSeekBar seekbar, Integer progressPercent, Boolean fromUser) {
        ifPlaybackControlInitialized(() -> {
            if (fromUser) {
                long progress = playbackControl.getCurrentSong().getLength() * progressPercent / 100;
                TextView time = findViewById(R.id.time);
                time.setText(String.format(Locale.GERMAN, "%02d:%02d", (int) (progress / 60000), (int) (progress / 1000 % 60)));
                playbackControl.seekTo((int) progress);
            }
        });
    }

    private void ifPlaybackControlInitialized(Runnable runnable) {
        if (playbackControl != null && playbackControl.isInitialized()) {
            runnable.run();
        }
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
        } else if (item.getItemId() == R.id.action_share) {
            saveAndShareFile();
            return true;
        } else if (item.getItemId() == R.id.action_save) {
            saveFile();
            return true;
        } else if (item.getItemId() == R.id.action_reload) {
            try (Id3TagsHandler id3Handler = new Id3TagsHandler(playbackControl.getLocalFile())) {
                List<Track> tracks = id3Handler.readChapters();
                playlist.reset(tracks);
            }
            return true;
//      } else if (item.getItemId() == R.id.action_settings) {
//                startActivity(new Intent(this, SettingsActivity.class));
//                return true;
        } else if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_licenses) {
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void saveAndShareFile() {
        try (Id3TagsHandler id3Handler = new Id3TagsHandler(playbackControl.getLocalFile())) {
            id3Handler.saveChapters(playlist.getTracks());

            Uri songUri = FileProvider.getUriForFile(this, "de.yoadey.choreomusic.provider",
                    playbackControl.getLocalFile());

            Intent intentShareFile = new Intent(Intent.ACTION_SEND);
            intentShareFile.setType("audio/mpeg");
            intentShareFile.putExtra(Intent.EXTRA_STREAM, songUri);
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing File...");

            startActivity(Intent.createChooser(intentShareFile, "Share File"));
        }
    }

    private void saveFile() {
        String filename = Utils.getFileName(getContentResolver(), playbackControl.getCurrentSong().getParsedUri());

        Intent intentSaveFile = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intentSaveFile.setType("audio/mpeg");
        intentSaveFile.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intentSaveFile, SAVE_FILE_CODE);
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
        long position;
        long length;
        if(playbackControl.getCurrentSong() != null) {
            position = playbackControl.getCurrentPosition();
            length = playbackControl.getCurrentSong().getLength();
        } else {
            position = 0;
            length = 100;
        }
        WaveformSeekBar slider = findViewById(R.id.waveformSeekBar);
        int progress = (int) (position * 100 / length);
        progress = Math.min(Math.max(0, progress), 100);
        slider.setProgress(progress);
        TextView time = findViewById(R.id.time);
        time.setText(String.format("%02d:%02d", position / 60000, position / 1000 % 60));
    }

    private void sendCommandToService(String action) {
        Intent stopIntent = new Intent(getApplicationContext(), PlaybackControl.class);
        stopIntent.setAction(action);
        startService(stopIntent);
    }

    @Override
    public void onSongChanged(Song newSong) {
        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        if (newSong == null) {
            waveformSeekBar.setSample(new int[] {0} );
            waveformSeekBar.setProgress(0);
            return;
        }

        // Save currently opened file to be opened again next time the application starts
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SP_FILE_KEY, newSong.getUri());
        editor.apply();

        handler.postDelayed(() -> initializeWaveform(waveformSeekBar), 10);
        updateSlider();
    }

    @Override
    public void onTrackChanged(Track newTrack) {
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