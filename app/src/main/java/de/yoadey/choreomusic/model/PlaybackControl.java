package de.yoadey.choreomusic.model;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import lombok.Getter;
import lombok.Setter;

public class PlaybackControl extends Service implements Playlist.PlaylistListener {
    public static final String PLAYBACK_CHANNEL_ID = "PlaybackServiceForegroundServiceChannel";
    public static final int PLAYBACK_NOTIFICATION_ID = 1;
    public static final String MEDIA_SESSION_TAG = "audio_demo";
    public static final String DOWNLOAD_CHANNEL_ID = "download_channel";
    public static final int DOWNLOAD_NOTIFICATION_ID = 2;
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private final Set<PlaybackListener> listeners;
    @Getter
    private final Playlist playlist;
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private PlayerNotificationManager playerNotificationManager;
    private boolean threadRunning;
    private boolean initialized;

    @Getter
    private Song currentSong;
    @Getter
    private Track start;
    @Getter
    private Track end;
    @Getter
    @Setter
    private long leadInTime;
    @Getter
    @Setter
    private long leadOutTime;
    @Getter
    private float speed;

    // Current and next track are needed to calculate, whether the current track changed.
    // Current track only knows about its start time, not its end time
    @Getter
    private Track currentTrack;
    private Track nextTrack;

    public PlaybackControl() {
        listeners = new HashSet<>();
        this.playlist = new Playlist();
        playlist.addPlaylistListener(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final Context context = this;
        player = new SimpleExoPlayer.Builder(context).build();
        player.prepare();

        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                context,
                PLAYBACK_CHANNEL_ID,
                R.string.playback_channel_name,
                PLAYBACK_NOTIFICATION_ID,
                new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @NotNull
                    @Override
                    public String getCurrentContentTitle(Player player) {
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
                    public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                        return ((BitmapDrawable) context.getResources().getDrawable(R.drawable.ic_stat_name, null)).getBitmap();
                    }
                },
                new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationPosted(int notificationId, @NotNull Notification notification, boolean ongoing) {
                        startForeground(notificationId, notification);
                    }

                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        stopSelf();
                    }
                }
        );
        playerNotificationManager.setPlayer(player);
        player.addListener(new Player.EventListener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if(isPlaying) {
                    startLoopingThread();
                }
                listeners.forEach(l -> l.onIsPlayingChanged(isPlaying));
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if(state == Player.STATE_ENDED && isLoopActive()) {
                    seekTo(start);
                }
            }
        });

        mediaSession = new MediaSessionCompat(context, MEDIA_SESSION_TAG);
        mediaSession.setActive(true);
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());

        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setQueueNavigator(new TimelineQueueNavigator(mediaSession) {
            @Override
            public MediaDescriptionCompat getMediaDescription(@NotNull Player player, int windowIndex) {
                if (currentSong == null) {
                    return new MediaDescriptionCompat.Builder()
                            .build();
                }
                return new MediaDescriptionCompat.Builder()
                        .setMediaId(currentSong.getUri())
                        .setTitle(currentSong.getTitle())
                        .build();
            }
        });
        mediaSessionConnector.setPlayer(player);
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
        return START_STICKY;
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
        player.stop();
    }

    public boolean isLoopActive() {
        return start != null && end != null;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        player.setPlaybackParameters(new PlaybackParameters(speed));
    }

    public void setStart(Track start) {
        this.start = start;
        if (isLoopActive()) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    public void setEnd(Track end) {
        this.end = end;
        if (isLoopActive()) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    public long getStartPosition() {
        if (!isLoopActive()) {
            return 0;
        }
        return Math.max(0, start.getPosition() - leadInTime);
    }

    public long getEndPosition() {
        if (!isLoopActive()) {
            return Integer.MAX_VALUE;
        }
        return end.getPosition() + leadOutTime;
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void seekTo(Track track) {
        player.seekTo(track.getPosition());
        currentTrack = track;
        nextTrack = playlist.getNextTrack(track);
        listeners.forEach(l -> l.trackChanged(currentTrack));
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
                    long delay = Math.min(MainActivity.BACKGROUND_THREAD_DELAY, getEndPosition() - player.getCurrentPosition());
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

    private void checkLoop() {
        // Update loop
        if (isLoopActive()) {
            if (player.getCurrentPosition() < getStartPosition() || player.getCurrentPosition() >= getEndPosition()) {
                player.seekTo(getStartPosition());
            }
        }
    }

    private void checkTrack() {
        if (currentTrack == null || player.getCurrentPosition() < currentTrack.getPosition() ||
                player.getCurrentPosition() > nextTrack.getPosition()) {
            currentTrack = playlist.getTrackForPosition(player.getCurrentPosition());
            nextTrack = playlist.getNextTrack(currentTrack);
            listeners.forEach(l -> l.trackChanged(currentTrack));
        }
    }

    public void ifInitialized(Runnable runnable) {
        if (initialized) {
            runnable.run();
        }
    }

    public synchronized void openSong(Song song) {
        if (Objects.equals(song, currentSong)) {
            return;
        }
        if (initialized) {
            stop();
        }
        MediaItem mediaItem = MediaItem.fromUri(song.getParsedUri());
        player.setMediaItem(mediaItem);
        currentSong = song;
        player.prepare();
        initialized = true;
        listeners.forEach(l -> l.songChanged(currentSong));
        song.resetTracks();
        List<Track> tracks = song.getTracks();
        playlist.reset(tracks);
    }

    @Override
    public void notifyPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        deletedTracks.forEach(track -> {
            if (getStart() == track) {
                setStart(null);
            }
            if (getEnd() == track) {
                setEnd(null);
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

    public void addPlaybackListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    public void deletePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public void seekTo(int progress) {
        player.seekTo(progress);
        checkTrack();
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public interface PlaybackListener {
        default void songChanged(Song newSong) {
        }

        default void trackChanged(Track newTrack) {
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
