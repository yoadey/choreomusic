package de.yoadey.choreomusic.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.utils.Constants;
import de.yoadey.choreomusic.utils.Utils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
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
    public static final long BACKGROUND_THREAD_DELAY = 100;
    /**
     * Time in ms in which the previous button jumps the previous track instead to the beginning
     * of this track
     */
    private static final long PREVIOUS_TRACK_DISTANCE = 5000;
    /**
     * Time in ms in which the music fades into the original volume before the track
     */
    private static final long FADE_IN_DURATION = 1000;
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
    private Looper looper = Looper.myLooper();
    /**
     * Observe the playback time, so the UI can react to it.
     * <p>
     * In JUnit, the looper will be null and not mocked, so we won't be able to have an observer.
     */
    public Observable<Long> playbackProgressObservable = looper != null ?
            Observable.interval(BACKGROUND_THREAD_DELAY, TimeUnit.MILLISECONDS, AndroidSchedulers.from(looper))
                    .map(t -> player != null ? getCurrentPosition() : 0)
                    .distinctUntilChanged() : null;
    private Handler exoHandler;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private PlayerNotificationManager playerNotificationManager;
    private boolean initialized;
    private Disposable progressStateObserverDisposable;

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
    @Getter
    private Track nextTrack;

    /**
     * Volume of the leadin part if the music is played in a loop.
     */
    @Getter
    private float leadInVolume = 1.0f;

    /**
     * Volume of the leadout part if the music is played in a loop.
     */
    @Getter
    private float leadOutVolume = 1.0f;

    private File localFile;
    private Notification notification;

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
        leadInVolume = sharedPreferences.getFloat(Constants.SP_KEY_LEAD_IN_VOLUME, 1.0f);
        leadOutVolume = sharedPreferences.getFloat(Constants.SP_KEY_LEAD_OUT_VOLUME, 1.0f);

        player = new ExoPlayer.Builder(this).build();
        player.prepare();
        exoHandler = new Handler(player.getApplicationLooper());
        player.setRepeatMode(Player.REPEAT_MODE_OFF);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    // Whenever music is playing, it must be a foreground service, so it won't get
                    // killed
                    startForeground(PLAYBACK_NOTIFICATION_ID, notification);
                    startLoopingThread();
                } else {
                    // Stop the foreground, so the notification can be removed with a swipe
                    stopForeground(false);
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
        progressStateObserverDisposable = playbackProgressObservable.subscribe(progress -> fireEvent(l -> l.onProgressChanged(progress.intValue())));

        mediaSession = new MediaSessionCompat(this, MEDIA_SESSION_TAG);

        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);
        mediaSession.setActive(true);
    }

    private void initializeNotification() {
        if (playerNotificationManager != null) {
            return;
        }
        playerNotificationManager = new PlayerNotificationManager.Builder(this, PLAYBACK_NOTIFICATION_ID, PLAYBACK_CHANNEL_ID)
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                        // Initially, the service must be set as a foreground service, otherwise
                        // it will be immediately killed. Once the music stopped, it can be set as a
                        // background service.
                        if (PlaybackControl.this.notification == null) {
                            startForeground(PLAYBACK_NOTIFICATION_ID, notification);
                        }
                        if (PlaybackControl.this.notification != notification) {
                            PlaybackControl.this.notification = notification;
                        }
                    }
                })
                .setChannelNameResourceId(R.string.playback_channel_name)
                .setChannelDescriptionResourceId(R.string.playback_channel_name)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @NotNull
                    @Override
                    public String getCurrentContentTitle(@NotNull Player player) {
                        if (currentSong == null) {
                            return getString(R.string.notification_nothing_loaded);
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
                        Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.ic_launcher, getTheme());
                        if (drawable == null) {
                            return null;
                        }
                        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        drawable.draw(canvas);
                        return bitmap;
                    }
                })
                .setStopActionIconResourceId(R.drawable.ic_outline_close_24)
                .build();

        ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player) {
            @Override
            public void seekToPrevious() {
                previousTrack();
            }

            @Override
            public void seekToNext() {
                nextTrack();
            }

            @Override
            public boolean isCommandAvailable(int command) {
                if (command == COMMAND_SEEK_TO_NEXT) {
                    return nextTrack != null;
                }
                return super.isCommandAvailable(command);
            }
        };
        playerNotificationManager.setPlayer(forwardingPlayer);

        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSessionConnector.setPlayer(null);
        }
        if (playerNotificationManager != null) {
            playerNotificationManager.setPlayer(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (progressStateObserverDisposable != null) {
            progressStateObserverDisposable.dispose();
        }

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
        exoHandler.post(() -> player.play());
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        exoHandler.post(() -> player.pause());
        seekTo(0);
    }

    public void seekTo(long progress) {
        exoHandler.post(() -> {
            player.seekTo(progress);
            // Must be called after seekTo has been finished, as many sub calls rely on
            // playbackControl.getCurrentPosition()
            checkTrack();
        });
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public boolean isLoopActive() {
        return loopStart != null && loopEnd != null;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        exoHandler.post(() -> player.setPlaybackParameters(new PlaybackParameters(speed)));
        fireEvent(l -> l.onSpeedChanged(speed));
    }

    public void setLoopStart(Track loopStart) {
        this.loopStart = loopStart;
        fireEvent(l -> l.onLoopChanged(this.leadInTime, this.loopStart, this.leadOutTime, this.loopEnd));
    }

    public long getLoopStartPosition() {
        if (!isLoopActive()) {
            return 0;
        }
        return Math.max(0, loopStart.getPosition() - leadInTime);
    }

    public void setLoopEnd(Track loopEnd) {
        this.loopEnd = loopEnd;
        fireEvent(l -> l.onLoopChanged(this.leadInTime, this.loopStart, this.leadOutTime, this.loopEnd));
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
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong(Constants.SP_KEY_LEAD_IN_TIME, leadInTime);
            editor.apply();
        }

        fireEvent(l -> l.onLoopChanged(this.leadInTime, this.loopStart, this.leadOutTime, this.loopEnd));
    }

    public void setLeadOutTime(long leadOutTime) {
        this.leadOutTime = leadOutTime;


        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong(Constants.SP_KEY_LEAD_OUT_TIME, leadOutTime);
            editor.apply();
        }

        fireEvent(l -> l.onLoopChanged(this.leadInTime, this.loopStart, this.leadOutTime, this.loopEnd));
    }

    public void setLeadInVolume(float leadInOutVolume) {
        this.leadInVolume = leadInOutVolume;

        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(Constants.SP_KEY_LEAD_IN_VOLUME, leadInOutVolume);
            editor.apply();
        }
    }

    public void setLeadOutVolume(float leadInOutVolume) {
        this.leadOutVolume = leadInOutVolume;

        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SP_PLAYBACK, MODE_PRIVATE);
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(Constants.SP_KEY_LEAD_OUT_VOLUME, leadInOutVolume);
            editor.apply();
        }
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void previousTrack() {
        long position = getCurrentPosition();
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
        long position = getCurrentPosition();
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
        position = Math.max(0, position);

        seekTo(position);
    }

    /**
     * Background thread to manage the loop and track. Must be more exact than UI updates, therefore
     * the observer is not sufficient.
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
                try {
                    checkLoop();
                    checkTrack();
                    checkVolume();

                    // Restart handler
                    if (player.isPlaying()) {
                        // If the loop cycle should end before normal delay, then update it earlier
                        long delay = Math.min(BACKGROUND_THREAD_DELAY, getLoopEndPosition() - getCurrentPosition());
                        delay = Math.max(0, delay);
                        handler.postDelayed(this, delay);
                    } else {
                        synchronized (threadRunningLock) {
                            threadRunning = false;
                        }
                    }
                } catch (NullPointerException e) {
                    // Only needs to be handled, if the instance is not already destroyed.
                    if (player != null) {
                        throw e;
                    }
                }
            }
        }, BACKGROUND_THREAD_DELAY);
    }

    /**
     * Check, whether a loop is active and if yes, keeps the song in the area of this loop.
     */
    private void checkLoop() {
        if (player == null) {
            // This should not occur, and if it does, only if the service is already disposed, but
            // handle nevertheless
            return;
        }
        // Update loop
        if (isLoopActive()) {
            long loopStart = getLoopStartPosition();
            if (getCurrentPosition() < loopStart - 500 || getCurrentPosition() >= getLoopEndPosition()) {
                player.seekTo(loopStart);
            }
        }
    }

    /**
     * Check, whether the current track is still the current track or whether it should be changed
     * to another one.
     */
    private void checkTrack() {
        if (player == null) {
            // This should not occur, and if it does, only if the service is already disposed, but
            // handle nevertheless
            return;
        }
        long position = getCurrentPosition();
        if (currentTrack == null || nextTrack == null ||
                position < currentTrack.getPosition() ||
                position >= nextTrack.getPosition()) {
            currentTrack = playlist.getTrackForPosition(position);
            nextTrack = playlist.getNextTrack(currentTrack);
            playerNotificationManager.invalidate();
            fireEvent(l -> l.onTrackChanged(currentTrack));
        }
    }

    public void checkVolume() {
        if (player == null) {
            return;
        }
        if (isLoopActive()) {
            long currentPosition = getCurrentPosition();
            long position = loopStart.getPosition() - currentPosition;
            position = position > 0 ? position : currentPosition - loopEnd.getPosition();
            float leadInOutVolume = currentPosition < loopStart.getPosition() ? leadInVolume : leadOutVolume;

            if (position < 0) {
                // Loop main part
                player.setVolume(1.0f);
            } else if (currentPosition > getLoopEndPosition() - FADE_IN_DURATION / 2 ||
                    currentPosition < getLoopStartPosition() + FADE_IN_DURATION / 2) {
                position = currentPosition < loopStart.getPosition() ?
                        currentPosition - getLoopStartPosition() :
                        currentPosition - getLoopEndPosition();
                position += FADE_IN_DURATION / 2;

                // Fade between lead in and out
                float volume = (leadOutVolume - leadInVolume) * (FADE_IN_DURATION - position) / FADE_IN_DURATION + leadInVolume;
                player.setVolume(volume);
            } else if (position > FADE_IN_DURATION) {
                // Lead in / out part
                player.setVolume(leadInOutVolume);
            } else {
                // Fade part between main
                float volume = (1.0f - leadInOutVolume) * (FADE_IN_DURATION - position) / FADE_IN_DURATION + leadInOutVolume;
                player.setVolume(volume);
            }
        } else {
            player.setVolume(1.0f);
        }
    }

    public synchronized void openSong(Song song) {
        if (Objects.equals(song, currentSong)) {
            return;
        }
        if (localFile != null) {
            //noinspection ResultOfMethodCallIgnored
            localFile.delete();
        }
        if (song == null) {
            this.currentSong = null;
            playlist.reset(Collections.emptyList(), Integer.MAX_VALUE);
            fireEvent(l -> l.onSongChanged(null));
            return;
        }
        if (initialized) {
            stop();
        }

        Uri parsedUri = song.getParsedUri();
        MediaItem mediaItem = MediaItem.fromUri(parsedUri);

        exoHandler.post(() -> {
            player.setMediaItem(mediaItem);
            currentSong = song;
            player.prepare();
            initialized = true;
            fireEvent(l -> l.onSongChanged(currentSong));
            // Reset tracks, as the file might be opened before and the tracks may have changed
            song.resetTracks();
            List<Track> tracks = song.getTracks();
            playlist.reset(tracks, song.getLength());
        });
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

        /**
         * Called, whenever the position of the track is changed, but at least 100ms between each
         * invocation.
         *
         * @param progress the current position in the song.
         */
        default void onProgressChanged(int progress) {
        }

        default void onIsPlayingChanged(boolean isPlaying) {
        }

        /**
         * Called, whenever the loop changes or is deactivated. If not both a and b are set, no
         * loop is active.
         *
         * @param a the start track of the loop if set, otherwise null
         * @param b the end track of the loop if set, otherwise null
         */
        default void onLoopChanged(long leadInTime, Track a, long leadOutTime, Track b) {
        }

        default void onSpeedChanged(float newSpeed) {

        }
    }

    public class LocalBinder extends Binder {
        public PlaybackControl getInstance() {
            return PlaybackControl.this;
        }
    }
}
