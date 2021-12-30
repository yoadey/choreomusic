package de.yoadey.choreomusic;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.masoudss.lib.WaveformSeekBar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.service.PlaybackControl;
import de.yoadey.choreomusic.ui.AboutActivity;
import de.yoadey.choreomusic.ui.OnboardingActivity;
import de.yoadey.choreomusic.ui.popups.PrePostDialogFragment;
import de.yoadey.choreomusic.ui.popups.SpeedDialogFragment;
import de.yoadey.choreomusic.ui.settings.SettingsActivity;
import de.yoadey.choreomusic.ui.tracks.SongsTracksAdapter;
import de.yoadey.choreomusic.utils.AmplitudesHelper;
import de.yoadey.choreomusic.utils.Constants;
import de.yoadey.choreomusic.utils.DatabaseHelper;
import de.yoadey.choreomusic.utils.MetadataHandler;
import de.yoadey.choreomusic.utils.Utils;
import lombok.Getter;

public class MainActivity extends AppCompatActivity implements PlaybackControl.PlaybackListener, ServiceConnection {

    private DatabaseHelper databaseHelper;
    private SongsTracksAdapter songsTracksAdapter;

    @Getter
    private PlaybackControl playbackControl;

    private ObservableList<Song> songs;
    private Playlist playlist;
    // A file which should be opened once the PlaybackControl service is connected
    private Uri fileToOpen;

    private ActivityResultLauncher<String[]> openFileLauncher;
    private ActivityResultLauncher<String> saveFileLauncher;

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
        initializeSongs();
        sendCommandToService(PlaybackControl.START_ACTION);
        bindService(new Intent(this, PlaybackControl.class), this, Context.BIND_AUTO_CREATE);

        setContentView(R.layout.activity_main);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        waveformSeekBar.setSample(new int[]{0});
        AsyncTask.execute(() -> initializeWaveform(waveformSeekBar));
        waveformSeekBar.setOnProgressChanged(this::onSliderChanged);

        MaterialButton play = findViewById(R.id.playpause);
        play.setOnClickListener(v -> onPlayChanged());

        View stop = findViewById(R.id.stop);
        stop.setOnClickListener(view -> ifPlaybackControlInitialized(() -> playbackControl.stop()));

        View loop = findViewById(R.id.loop);
        loop.setOnClickListener(this::openLoopDialog);

        View speed = findViewById(R.id.speed);
        speed.setOnClickListener(v -> openSpeedDialog());

        View bookmark = findViewById(R.id.add_track);
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

        registerActivityResults();

        checkOnBoarding();
    }

    private void initializeSongs() {
        songs = new ObservableArrayList<>();
        songs.addAll(databaseHelper.getAllSongs());
        new Thread(() -> {
            List<Song> deletedSongs = songs.stream()
                    .filter(song -> !Utils.checkUriExists(getContentResolver(), song.getParsedUri()))
                    .peek(databaseHelper::deleteSong)
                    .collect(Collectors.toList());
            if (deletedSongs.size() > 0) {
                String deletedSongTitles = deletedSongs.stream()
                        // songs.removeAll does not work, as ObservableList does not notify then
                        .peek(songs::remove)
                        .map(song -> song.getParsedUri().getLastPathSegment().replaceFirst("^.+[:/]", ""))
                        .collect(Collectors.joining(", "));
                showError(getString(R.string.warn_track_deleted_title),
                        MessageFormat.format(getString(R.string.warn_track_deleted_message), deletedSongTitles));
            }
        }).start();
    }

    private void registerActivityResults() {
        openFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::openFile);
        saveFileLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::saveFile);
    }

    /**
     * Checks whether this is the first time the user starts the app and if yes, shows an onBoarding
     * screen.
     */
    private void checkOnBoarding() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        if (!sharedPreferences.getBoolean(
                OnboardingActivity.COMPLETED_ONBOARDING_PREF_NAME, false)) {
            SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
            sharedPrefsEditor.putBoolean(OnboardingActivity.COMPLETED_ONBOARDING_PREF_NAME, true);
            sharedPrefsEditor.apply();
            startActivity(new Intent(this, OnboardingActivity.class));
        }
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
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
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
        openFile(fileToOpen);
        reloadLastFile();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        playbackControl.deletePlaybackListener(MainActivity.this);
        playbackControl.deletePlaybackListener(databaseHelper);
        playlist.deletePlaylistListener(databaseHelper);
        playbackControl = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        configureWaveform(waveformSeekBar);
    }

    public synchronized void openFile(Uri file) {
        // playbackControl might not yet be connected, but is required. Only execute, once
        // playbackControl is connected
        if (playbackControl == null || file == null) {
            if (file != null) {
                fileToOpen = file;
            }
            return;
        }

        boolean stillExists = takePermissions(file);
        if (!stillExists) {
            String filename = file.getLastPathSegment();
            filename = filename.replaceFirst("^.+[:/]", "");
            showError(getString(R.string.warn_track_deleted_title),
                    MessageFormat.format(getString(R.string.warn_track_deleted_message), filename));
            databaseHelper.deleteSongByUri(file);
            return;
        }

        // Save/update/delete file info
        Song song = getOrCreateSong(file);

        if (!songs.contains(song)) {
            songs.add(song);
            songs.sort(Comparator.comparing(Song::getTitle));
        }

        // Open file
        if (playbackControl != null) {
            playbackControl.openSong(song);
        }
    }

    /**
     * Checks whether the permissions can be taken for the file. If not or the file does no longer
     * exist in the list of files, it is deleted from the list of open songs
     *
     * @param file the file to open
     * @return true, if permissions could be taken or exist, false otherwise.
     */
    private boolean takePermissions(Uri file) {
        try {
            boolean alreadyPermissions = getContentResolver().getPersistedUriPermissions()
                    .stream()
                    .anyMatch(element -> Objects.equals(element.getUri(), file));
            boolean stillExists = true;
            if (!alreadyPermissions) {
                getContentResolver().takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // Check whether file permissions could really be taken
                stillExists = getContentResolver().getPersistedUriPermissions()
                        .stream()
                        .anyMatch(element -> Objects.equals(element.getUri(), file));
            }
            stillExists &= Utils.checkUriExists(getContentResolver(), file);
            return stillExists;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Find the song information in the DB or create them, if they are not yet there
     *
     * @param file the file for which the song information should be returned
     * @return the found or created song
     */
    private Song getOrCreateSong(Uri file) {
        Song song = databaseHelper.findSongByUri(file);
        boolean isNew = song == null;
        if (isNew) {
            song = new Song();
            song.setUri(file.toString());
            File localFile = playbackControl.getLocalFile(file);
            try (MetadataHandler metadataHandler = MetadataHandler.open(getApplicationContext(), localFile)) {
                song.setTitle(metadataHandler.getTitle());
                song.setLength(metadataHandler.getLength());
                song.setTracks(metadataHandler.readChapters());
                song.setFileSupportsTracks(metadataHandler.supportsChapters());
            } finally {
                if (!localFile.delete()) {
                    Log.w("MainActivity", "Could not delete temporary file");
                }
            }
        }
        song.setLastUsed(new Date());
        databaseHelper.saveSong(song);
        if (isNew) {
            databaseHelper.saveTracks(song.getTracks());
        }
        return song;
    }

    /**
     * Loads the last opened file from the preferences or from the playback control, if they are
     * initialized
     */
    private void reloadLastFile() {
        // Reloading of last file should happen asynchronously, so it doesn't block the UI thread
        AsyncTask.execute(() -> {
            if (playbackControl.getCurrentSong() != null) {
                openFile(playbackControl.getCurrentSong().getParsedUri());
                onSongChanged(playbackControl.getCurrentSong());
                onIsPlayingChanged(playbackControl.isPlaying());
            } else {
                SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
                String fileUri = sharedPreferences.getString(Constants.SP_KEY_FILE, null);
                if (fileUri != null) {
                    Uri uri = Uri.parse(fileUri);
                    if (Utils.checkUriExists(getContentResolver(), uri)) {
                        openFile(uri);
                    }
                }
            }
        });
    }

    private void initializeWaveform(WaveformSeekBar waveformSeekBar) {
        ifPlaybackControlInitialized(() -> {
            TextView textView = findViewById(R.id.waveformCalcText);
            runOnUiThread(() -> {
                int[] sample = new int[(int) (playbackControl.getCurrentSong().getLength() / 100)];
                Arrays.fill(sample, 0);
                waveformSeekBar.setSample(sample);
                textView.setText(R.string.waveview_calculating);
            });

            Song song = playbackControl.getCurrentSong();
            int[] sample;
            if (song.getAmplitudes() == null) {
                File localFile = playbackControl.getLocalFile();
                sample = AmplitudesHelper.extractAmplitudes(this, localFile);
                runOnUiThread(() -> {
                    waveformSeekBar.setSample(sample);
                    textView.setText("");
                });
                song.setIntAmplitudes(sample);
                song.update();
            } else {
                sample = song.getIntAmplitudes();
            }
            runOnUiThread(() -> {
                waveformSeekBar.setMaxProgress(song.getLength());
                waveformSeekBar.setSample(sample);
                textView.setText("");
            });
        });
    }

    private void configureWaveform(WaveformSeekBar waveformSeekBar) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (preferences.getBoolean(Constants.SP_KEY_DETAILED_WAVEFORM, false)) {
            waveformSeekBar.setWaveGap(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 0, displayMetrics));
            waveformSeekBar.setWaveWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 1, displayMetrics));
            waveformSeekBar.setWaveCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 0, displayMetrics));
        } else {
            waveformSeekBar.setWaveGap(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, displayMetrics));
            waveformSeekBar.setWaveWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, displayMetrics));
            waveformSeekBar.setWaveCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, displayMetrics));
        }
    }

    /*-----------------------------------------------------
     *
     * Button actions
     *
     *-----------------------------------------------------*/

    private void previousTrack() {
        ifPlaybackControlInitialized(() -> playbackControl.previousTrack());
    }

    private void nextTrack() {
        ifPlaybackControlInitialized(() -> playbackControl.nextTrack());
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

    @SuppressLint("DefaultLocale")
    private void addTrack() {
        ifPlaybackControlInitialized(() -> {
            long bookmarkPosition = playbackControl.getCurrentPosition();
            // Check that there doesn't already exists a track at the same position
            Track existingTrack = playlist.getTracks().stream()
                    .filter(track -> Math.abs(track.getPosition() - bookmarkPosition) < 100)
                    .findAny().orElse(null);
            if (existingTrack != null) {

                showError(getString(R.string.warn_track_exists_title),
                        getString(R.string.warn_track_exists_message,
                                existingTrack.getPosition() / 60000, existingTrack.getPosition() / 1000 % 60,
                                existingTrack.getLabel()));
            } else {
                // Find the highest track number and set the title of the new track to the next value
                int nextTrack = playlist.getTracks().stream()
                        .map(Track::getLabel)
                        .filter(track -> track.matches("Track \\d+"))
                        .map(track -> Integer.parseInt(track.replaceAll("Track (\\d+)", "$1")))
                        .max(Integer::compareTo).orElse(0) + 1;
                Track track = new Track(bookmarkPosition, "Track " + nextTrack);
                playlist.addTrack(track);
            }
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

    private void onSliderChanged(WaveformSeekBar seekbar, Float progress, Boolean fromUser) {
        ifPlaybackControlInitialized(() -> {
            if (fromUser) {
                TextView time = findViewById(R.id.time);
                time.setText(String.format(Locale.GERMAN, "%02.0f:%02.0f", progress / 60000, progress / 1000 % 60));
                playbackControl.seekTo(Math.round(progress));
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean supportsTracks = Optional.ofNullable(playbackControl)
                .map(PlaybackControl::getCurrentSong)
                .map(Song::getFileSupportsTracks)
                .orElse(false);
        // Those options are only useful, if the file type supports tracks
        menu.findItem(R.id.action_share).setEnabled(supportsTracks);
        menu.findItem(R.id.action_save).setEnabled(supportsTracks);
        menu.findItem(R.id.action_reload).setEnabled(supportsTracks);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.action_open) {
            openFileLauncher.launch(new String[]{"*/*"});
            return true;
        } else if (item.getItemId() == R.id.action_share) {
            saveAndShareFile();
            return true;
        } else if (item.getItemId() == R.id.action_save) {
            saveFile();
            return true;
        } else if (item.getItemId() == R.id.action_reload) {
            reloadFile();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_onboarding) {
            startActivity(new Intent(this, OnboardingActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void reloadFile() {
        ifPlaybackControlInitialized(() -> {
            try (MetadataHandler mdHandler = MetadataHandler.open(getApplicationContext(), playbackControl.getLocalFile())) {
                List<Track> tracks = mdHandler.readChapters();
                playlist.reset(tracks, playbackControl.getCurrentSong().getLength());
            }
        });
    }

    private void saveAndShareFile() {
        ifPlaybackControlInitialized(() -> {
            try (MetadataHandler mdHandler = MetadataHandler.open(getApplicationContext(), playbackControl.getLocalFile())) {
                mdHandler.saveChapters(playlist.getTracks());

                Uri songUri = FileProvider.getUriForFile(this, "de.yoadey.choreomusic.provider",
                        playbackControl.getLocalFile());

                Intent intentShareFile = new Intent(Intent.ACTION_SEND);
                intentShareFile.setType("audio/mpeg");
                intentShareFile.putExtra(Intent.EXTRA_STREAM, songUri);
                intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
                intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing File...");

                startActivity(Intent.createChooser(intentShareFile, "Share File"));
            }
        });
    }

    /**
     * Initiates the save activity to select the target location to save the file
     */
    private void saveFile() {
        ifPlaybackControlInitialized(() -> {
            if (!playbackControl.getCurrentSong().getFileSupportsTracks()) {
                // User should not be able to reach this point, but better save than sorry
                showError(getString(R.string.warn_save_not_supported_title),
                        getString(R.string.warn_save_not_supported_message));
                return;
            }

            String filename = Utils.getFileName(getContentResolver(), playbackControl.getCurrentSong().getParsedUri());
            saveFileLauncher.launch(filename);
        });
    }

    /**
     * Saves the file to the target location
     *
     * @param file the file to which the output should be written.
     */
    private void saveFile(Uri file) {
        ContentResolver contentResolver = getContentResolver();
        File localFile = playbackControl.getLocalFile();
        try (MetadataHandler mdHandler = MetadataHandler.open(getApplicationContext(), localFile)) {
            mdHandler.saveChapters(playlist.getTracks());
            try (InputStream in = mdHandler.getInputStream();
                 OutputStream out = contentResolver.openOutputStream(file)) {
                Utils.copyStream(in, out);
            }
        } catch (IOException e) {
            Log.w(MainActivity.class.getName(), "Error during saving file: ", e);
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateSlider() {
        long position;
        if (playbackControl.getCurrentSong() != null) {
            position = playbackControl.getCurrentPosition();
        } else {
            position = 0;
        }
        WaveformSeekBar slider = findViewById(R.id.waveformSeekBar);
        TextView time = findViewById(R.id.time);
        runOnUiThread(() -> {
            slider.setProgress((int) position);
            time.setText(String.format("%02d:%02d", position / 60000, position / 1000 % 60));
        });
    }

    private void sendCommandToService(String action) {
        Intent stopIntent = new Intent(getApplicationContext(), PlaybackControl.class);
        stopIntent.setAction(action);
        ContextCompat.startForegroundService(this, stopIntent);
    }

    @Override
    public void onSpeedChanged(float newSpeed) {
        MaterialButton button = findViewById(R.id.speed);
        TextView textView = findViewById(R.id.speedText);
        runOnUiThread(() -> {
            if (newSpeed != 1F) {
                button.setIconTintResource(R.color.secondary);
                textView.setText(String.format(Locale.ENGLISH, "%.2f", newSpeed));
            } else {
                button.setIconTintResource(R.color.primary);
                textView.setText("");
            }
        });
    }

    @Override
    public void onSongChanged(Song newSong) {
        WaveformSeekBar waveformSeekBar = findViewById(R.id.waveformSeekBar);
        if (newSong == null) {
            runOnUiThread(() -> {
                waveformSeekBar.setSample(new int[]{0});
                waveformSeekBar.setProgress(0);
            });
            return;
        }

        // Switch to the tracks view when a song was opened
        ViewPager2 viewPager = findViewById(R.id.main_area);
        runOnUiThread(() ->
                viewPager.setCurrentItem(1, true));

        AsyncTask.execute(() -> initializeWaveform(waveformSeekBar));
        updateSlider();

        // Save currently opened file to be opened again next time the application starts
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.SP_KEY_FILE, newSong.getUri());
        editor.apply();
    }

    @Override
    public void onTrackChanged(Track newTrack) {
        updateSlider();
    }

    @Override
    public void onProgressChanged(int progress) {
        updateSlider();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        MaterialButton play = findViewById(R.id.playpause);
        runOnUiThread(() -> {
            if (isPlaying) {
                play.setIconResource(R.drawable.baseline_pause_24);
            } else {
                play.setIconResource(R.drawable.baseline_play_arrow_24);
            }
        });
    }

    /**
     * Shows in error in case a not applicable action was tried
     *
     * @param title       the title of the error
     * @param description the description of the error
     */
    private void showError(String title, String description) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(description)
                // A null listener allows the button to dismiss the dialog and take no further action.
                .setNegativeButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show());
    }
}