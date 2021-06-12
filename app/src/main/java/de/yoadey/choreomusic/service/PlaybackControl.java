package de.yoadey.choreomusic.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.utils.Constants;
import de.yoadey.choreomusic.utils.Utils;
import lombok.Getter;

/**
 * Service to handle the player and manage the current state, independent from the UI.
 * Creates a notification with MediaPlayer controls (previous, back 5 seconds, play, forward 5 seconds).
 */
public class PlaybackControl extends Service implements Playlist.PlaylistListener {
    public static final String PLAYBACK_CHANNEL_ID = "PlaybackServiceForegroundServiceChannel";
    public static final int PLAYBACK_NOTIFICATION_ID = 1;
    public static final String MEDIA_SESSION_TAG = "ChoreoMusic";
    /**
     * Action for an Intent. Should be called to start the service.
     */
    public static final String START_ACTION = "StartService";
    /**
     * Action for an Intent. Should be called to stop the service.
     */
    public static final String STOP_ACTION = "StopService";
    /**
     * Time in ms in which the previous button jumps the previous track instead to the beginning
     * of this track
     */
    private static final long PREVIOUS_TRACK_DISTANCE = 5000;
    /**
     * Background thread for handling the loop
     */
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private final Set<PlaybackListener> listeners;
    @Getter
    private final Playlist playlist;
    private boolean threadRunning;
    // Media player and notification stuff
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private PlayerNotificationManager playerNotificationManager;
    private boolean initialized;
    /**
     * The currently playing song.
     */
    @Getter
    private Song currentSong;

    /**
     * The start track of the current loop.
     */
    @Getter
    private Track loopStart;
    /**
     * The end track of the current loop.
     */
    @Getter
    private Track loopEnd;

    /**
     * Defines, how much time in ms should be played from the track before the loop start track when a loop is activated.
     */
    @Getter
    private long leadInTime;
    /**
     * Defines, how much time in ms should be played from the track after the loop end track when a loop is activated.
     */
    @Getter
    private long leadOutTime;

    @Getter
    private float speed;

    // Current and next track are needed to calculate, whether the current track changed.
    // Current track only knows about its start time, not its end time
    @Getter
    private Track currentTrack;
    private Track nextTrack;

    private File localFile;

    public PlaybackControl() {
        listeners = new HashSet<>();
        this.playlist = new Playlist();
        playlist.addPlaylistListener(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        leadInTime = sharedPreferences.getLong(Constants.SP_KEY_LEAD_IN_TIME, 0);
        leadOutTime = sharedPreferences.getLong(Constants.SP_KEY_LEAD_OUT_TIME, 0);

        player = new SimpleExoPlayer.Builder(this).build();
        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);

        player.addListener(new Player.EventListener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    startLoopingThread();
                }
                fireEvent(l -> l.onIsPlayingChanged(isPlaying));
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && isLoopActive()) {
                    seekTo(loopStart);
                }
            }
        });

        mediaSession = new MediaSessionCompat(this, MEDIA_SESSION_TAG);

        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);
        mediaSession.setActive(true);
    }

    private void initializeNotification() {
        if (playerNotificationManager != null) {
            return;
        }
        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                getApplicationContext(),
                PLAYBACK_CHANNEL_ID,
                R.string.playback_channel_name,
                R.string.playback_channel_name,
                PLAYBACK_NOTIFICATION_ID,
                new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @NotNull
                    @Override
                    public String getCurrentContentTitle(@NotNull Player player) {
                        if (currentSong == null) {
                            return "Nothing loaded";
                        }
                        return currentSong.getTitle();
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(@NotNull Player player) {
                        return null;
                    }

                    @Nullable
                    @Override
                    public String getCurrentContentText(@NotNull Player player) {
                        return Optional.ofNullable(currentTrack)
                                .map(Track::getLabel)
                                .orElse(null);
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(@NotNull Player player, @NotNull PlayerNotificationManager.BitmapCallback callback) {
                        return null;//((BitmapDrawable) getApplicationContext().getResources().getDrawable(R.drawable.ic_stat_name, null)).getBitmap();
                    }
                }
        );
        playerNotificationManager.setPlayer(player);

        playerNotificationManager.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchPrevious(Player player) {
                previousTrack();
                return true;
            }

            @Override
            public boolean dispatchNext(Player player) {
                nextTrack();
                return true;
            }
        });
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        mediaSessionConnector.setPlayer(null);
        playerNotificationManager.setPlayer(null);
        player.release();
        player = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Objects.equals(intent.getAction(), START_ACTION)) {
            initializeNotification();
        } else if (Objects.equals(intent.getAction(), STOP_ACTION)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    public void addPlaybackListener(PlaybackListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void deletePlaybackListener(PlaybackListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void fireEvent(Consumer<? super PlaybackListener> consumer) {
        synchronized (listeners) {
            listeners.forEach(consumer);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public boolean isInitialized() {
        return initialized;
    }

    @NotNull
    public File getLocalFile() {
        if (localFile != null && localFile.exists()) {
            return localFile;
        }
        localFile = getLocalFile(currentSong.getParsedUri());
        return localFile;
    }

    @NotNull
    public File getLocalFile(Uri uri) {
        File filesDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (filesDir == null) {
            filesDir = getFilesDir();
        }

        String fileName = Utils.getFileName(getContentResolver(), uri);
        File localFile = new File(filesDir, fileName);
        localFile.deleteOnExit();
        try (InputStream is = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(localFile)) {
            Utils.copyStream(is, os);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return localFile;
    }

    public void play() {
        setSpeed(speed == 0.0f ? 1.0f : speed);
        player.play();
        startLoopingThread();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        player.pause();
        seekTo(0);
    }

    public void seekTo(int progress) {
        player.seekTo(progress);
        checkTrack();
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public boolean isLoopActive() {
        return loopStart != null && loopEnd != null;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        player.setPlaybackParameters(new PlaybackParameters(speed));
    }

    public void setLoopStart(Track loopStart) {
        this.loopStart = loopStart;
        if (isLoopActive()) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    public long getLoopStartPosition() {
        if (!isLoopActive()) {
            return 0;
        }
        return Math.max(0, loopStart.getPosition() - leadInTime);
    }

    public void setLoopEnd(Track loopEnd) {
        this.loopEnd = loopEnd;
        if (isLoopActive()) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    public long getLoopEndPosition() {
        if (!isLoopActive()) {
            return Integer.MAX_VALUE;
        }
        return loopEnd.getPosition() + leadOutTime;
    }

    public void setLeadInTime(long leadInTime) {
        this.leadInTime = leadInTime;

        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(Constants.SP_KEY_LEAD_IN_TIME, leadInTime);
        editor.apply();
    }

    public void setLeadOutTime(long leadOutTime) {
        this.leadOutTime = leadOutTime;

        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(Constants.SP_KEY_LEAD_OUT_TIME, leadOutTime);
        editor.apply();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void previousTrack() {
        long position = player.getCurrentPosition();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean preTimeSwitchTrack = prefs.getBoolean(Constants.SP_KEY_SWITCH_TRACK, true);
        if (preTimeSwitchTrack) {
            position += leadInTime + 100;
        }
        if (position > currentTrack.getPosition() + PREVIOUS_TRACK_DISTANCE) {
            seekTo(currentTrack);
        } else {
            Track track = playlist.getTrackForPosition(position);
            seekTo(playlist.getPreviousTrack(track));
        }
    }

    public void nextTrack() {
        long position = player.getCurrentPosition();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean preTimeSwitchTrack = prefs.getBoolean("preTimeSwitchTrack", true);
        if (preTimeSwitchTrack) {
            position += leadInTime + 100;
        }
        Track track = playlist.getTrackForPosition(position);
        seekTo(playlist.getNextTrack(track));
    }

    public void seekTo(Track track) {
        long position = track.getPosition();

        // Use leadInTime for track switch if set, as probably mostly it is not wanted to start
        // directly at the track but start dancing and you need some time of preparation for this
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean preTimeSwitchTrack = prefs.getBoolean("preTimeSwitchTrack", true);
        if (preTimeSwitchTrack) {
            position -= leadInTime;
        }

        player.seekTo(position);
        currentTrack = playlist.getTrackForPosition(position);
        nextTrack = playlist.getNextTrack(currentTrack);
        synchronized (listeners) {
            fireEvent(l -> l.onTrackChanged(currentTrack));
        }
    }

    /**
     * Background thread to update the seekbar, timer and manage the loop.
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
                checkLoop();
                checkTrack();

                // Restart handler
                if (player.isPlaying()) {
                    // If the loop cycle should end before normal delay, then update it earlier
                    long delay = Math.min(MainActivity.BACKGROUND_THREAD_DELAY, getLoopEndPosition() - player.getCurrentPosition());
                    delay = Math.max(0, delay);
                    handler.postDelayed(this, delay);
                } else {
                    synchronized (threadRunningLock) {
                        threadRunning = false;
                    }
                }
            }
        }, MainActivity.BACKGROUND_THREAD_DELAY);
    }

    /**
     * Check, whether a loop is active and if yes, keeps the song in the area of this loop.
     */
    private void checkLoop() {
        // Update loop
        if (isLoopActive()) {
            if (player.getCurrentPosition() < getLoopStartPosition() || player.getCurrentPosition() >= getLoopEndPosition()) {
                player.seekTo(getLoopStartPosition());
            }
        }
    }

    /**
     * Check, whether the current track is still the current track or whether it should be changed
     * to another one.
     */
    private void checkTrack() {
        if (currentTrack == null || player.getCurrentPosition() < currentTrack.getPosition() ||
                player.getCurrentPosition() > nextTrack.getPosition()) {
            currentTrack = playlist.getTrackForPosition(player.getCurrentPosition());
            nextTrack = playlist.getNextTrack(currentTrack);
            fireEvent(l -> l.onTrackChanged(currentTrack));
        }
    }

    public synchronized void openSong(Song song) {
        if (Objects.equals(song, currentSong)) {
            return;
        }
        if (localFile != null) {
            localFile.delete();
        }
        if (song == null) {
            this.currentSong = null;
            playlist.reset(Collections.emptyList());
            fireEvent(l -> l.onSongChanged(null));
            return;
        }
        if (initialized) {
            stop();
        }
        MediaItem mediaItem = MediaItem.fromUri(song.getParsedUri());
        player.setMediaItem(mediaItem);
        // To allow next buttons in notification
        player.addMediaItem(mediaItem);
        currentSong = song;
        player.prepare();
        initialized = true;
        fireEvent(l -> l.onSongChanged(currentSong));
        // Reset tracks, as the file might be opened before and the tracks may have changed
        song.resetTracks();
        List<Track> tracks = song.getTracks();
        playlist.reset(tracks);
    }

    @Override
    public void onPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        deletedTracks.forEach(track -> {
            if (getLoopStart() == track) {
                setLoopStart(null);
            }
            if (getLoopEnd() == track) {
                setLoopEnd(null);
            }
            if (nextTrack == track) {
                nextTrack = playlist.getNextTrack(currentTrack);
            }
            if (currentTrack == track) {
                currentTrack = null;
                checkTrack();
            }
        });
        if (!newTracks.isEmpty()) {
            currentTrack = null;
            checkTrack();
        }
    }

    public interface PlaybackListener {
        default void onSongChanged(Song newSong) {
        }

        default void onTrackChanged(Track newTrack) {
        }

        default void onIsPlayingChanged(boolean isPlaying) {
        }
    }

    public class LocalBinder extends Binder {
        public PlaybackControl getInstance() {
            return PlaybackControl.this;
        }
    }
}
