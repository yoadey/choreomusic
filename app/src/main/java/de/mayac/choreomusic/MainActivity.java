package de.mayac.choreomusic;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.Date;
import java.util.List;

import de.mayac.choreomusic.model.FileInfo;
import de.mayac.choreomusic.model.PlaybackControl;
import de.mayac.choreomusic.model.Playlist;
import de.mayac.choreomusic.model.Track;
import de.mayac.choreomusic.ui.SettingsActivity;
import de.mayac.choreomusic.ui.TrackViewAdapter;
import de.mayac.choreomusic.ui.popups.PrePostDialogFragment;
import de.mayac.choreomusic.ui.popups.SpeedDialogFragment;
import de.mayac.choreomusic.utils.DatabaseHelper;
import de.mayac.choreomusic.utils.Id3TagsHandler;

public class MainActivity extends AppCompatActivity {

    /**
     * Number of milliseconds to delay, before the background thread is called again to update the UI components and the loop
     */
    public static final int BACKGROUND_THREAD_DELAY = 100;
    private static final int OPEN_FILE_CODE = 1;
    private static final String SP_FILE_KEY = "MUSIC_FILE";
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private Uri musicFile;
    private DatabaseHelper databaseHelper;
    private TrackViewAdapter trackViewAdapter;
    private PlaybackControl playbackControl;
    private Playlist playlist;
    private boolean threadRunning;
    private Id3TagsHandler id3Handler;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_FILE_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri file = data.getData();
                ContentResolver resolver = getContentResolver();
                resolver.takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openFile(file);
            }
        }
    }

    private void openFile(Uri file) {
        // Save currently opened file
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SP_FILE_KEY, file.toString());
        editor.commit();

        // Open file
        this.musicFile = file;
        playbackControl.openFile(this, file);

        Slider slider = findViewById(R.id.progressSlider);
        slider.setValue(0);
        slider.setValueTo(playbackControl.getDuration());

        // Save/update fileinfos
        FileInfo fileInfo = databaseHelper.findFileInfoByUri(file);
        boolean newFile = fileInfo == null;
        if (newFile) {
            fileInfo = new FileInfo();
            fileInfo.setUri(file.toString());
        }
        fileInfo.setLastUsed(new Date());
        databaseHelper.saveFileInfo(fileInfo);
        databaseHelper.setCurrentFile(fileInfo.getId());

        List<Track> tracks;
        if (newFile) {
            tracks = id3Handler.readChapters(file);
        } else {
            tracks = databaseHelper.getAllTracks();
        }
        playlist.reset(tracks);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        databaseHelper = new DatabaseHelper(getApplicationContext());
        id3Handler = new Id3TagsHandler(this, getContentResolver());
        playlist = new Playlist();
        playbackControl = new PlaybackControl(playlist);
        playbackControl.addPlaybackListener(newTrack -> {
            updateSlider();
        });
        playbackControl.setSpeed(1.0f);
        playlist.addPlaylistListener(((newTracks, deletedTracks, playlistAfter) -> {
            newTracks.forEach(databaseHelper::saveTrack);
            deletedTracks.forEach(databaseHelper::deleteTrack);
        }));

        setContentView(R.layout.activity_main);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MaterialButton play = findViewById(R.id.playpause);
        play.setOnClickListener(view -> {
            playbackControl.ifInitialized(() -> {
                if (playbackControl.isPlaying()) {
                    playbackControl.pause();
                    play.setIconResource(R.drawable.baseline_play_arrow_24);
                } else {
                    playbackControl.play();
                    play.setIconResource(R.drawable.baseline_pause_24);
                    startLoopingThread();
                }
            });
        });

        View stop = findViewById(R.id.stop);
        stop.setOnClickListener(view -> {
            playbackControl.ifInitialized(() -> {
                playbackControl.stop();
                Slider seekbar = findViewById(R.id.progressSlider);
                seekbar.setValue(0);
                TextView time = findViewById(R.id.time);
                time.setText("00:00");
                play.setIconResource(R.drawable.baseline_play_arrow_24);
            });
        });

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
        bookmark.setOnClickListener(view -> {
            playbackControl.ifInitialized(() -> {
                int bookmarkPosition = playbackControl.getCurrentPosition();
                Track track = new Track(bookmarkPosition, "Track " + trackViewAdapter.getItemCount());
                playlist.addTrack(track);
            });
        });

        Slider seekBar = findViewById(R.id.progressSlider);
        seekBar.addOnChangeListener((slider, progress, fromUser) -> {
            playbackControl.ifInitialized(() -> {
                if (fromUser) {
                    TextView time = findViewById(R.id.time);
                    time.setText(String.format("%02d:%02d", (int) (progress / 60000), (int) (progress / 1000 % 60)));
                    playbackControl.seekTo((int) progress);
                }
            });
        });

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


        RecyclerView tracksView = (RecyclerView) findViewById(R.id.tracks);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        tracksView.setLayoutManager(layoutManager);
        trackViewAdapter = new TrackViewAdapter(playbackControl, this);
        tracksView.setAdapter(trackViewAdapter);

        reloadLastFile();
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
        switch (item.getItemId()) {
            case R.id.action_open:
                Intent openFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                openFile.setType("audio/mpeg");
                startActivityForResult(openFile, OPEN_FILE_CODE);
                return true;
            case R.id.action_export:
                id3Handler.saveChapters(musicFile, playlist.getTracks());
                return true;
            case R.id.action_reload:
                List<Track> tracks = id3Handler.readChapters(musicFile);
                playlist.reset(tracks);
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_about:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Background thread to update the slider, timer and manage the loop.
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
                // Update position of slider
                updateSlider();

                // Restart handler
                if (playbackControl.isPlaying()) {
                    handler.postDelayed(this, BACKGROUND_THREAD_DELAY);
                } else {
                    synchronized (threadRunningLock) {
                        threadRunning = false;
                    }
                }
            }
        }, BACKGROUND_THREAD_DELAY);
    }

    private void updateSlider() {
        int position = playbackControl.getCurrentPosition();
        Slider slider = findViewById(R.id.progressSlider);
        slider.setValue(position);
        TextView time = findViewById(R.id.time);
        time.setText(String.format("%02d:%02d", position / 60000, position / 1000 % 60));
    }

    private void reloadLastFile() {
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        String fileUri = sharedPreferences.getString(SP_FILE_KEY, null);
        if (fileUri != null) {
            Uri uri = Uri.parse(fileUri);
            openFile(uri);
        }
    }
}