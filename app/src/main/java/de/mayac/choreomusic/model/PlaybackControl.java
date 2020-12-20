package de.mayac.choreomusic.model;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mayac.choreomusic.MainActivity;
import lombok.Getter;
import lombok.Setter;

public class PlaybackControl implements Playlist.PlaylistListener {
    private final MediaPlayer mediaPlayer;
    private final Handler handler = new Handler();
    private final Object threadRunningLock = new Object();
    private final Set<PlaybackListener> listeners;
    @Getter
    private final Playlist playlist;
    private boolean threadRunning;
    private boolean initialized;
    private boolean shouldPlay;
    @Getter
    @Setter
    private Track start;
    @Getter
    @Setter
    private Track end;
    @Getter
    @Setter
    private int leadInTime;
    @Getter
    @Setter
    private int leadOutTime;
    @Getter
    private float speed;

    // Current and next track are needed to calculate, whether the current track changed.
    // Current track only knows about its start time, not its end time
    @Getter
    private Track currentTrack;
    private Track nextTrack;

    public PlaybackControl(Playlist playlist) {
        listeners = new HashSet<>();
        this.playlist = playlist;
        playlist.addPlaylistListener(this);
        mediaPlayer = new MediaPlayer();
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        AudioAttributes attributes = builder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        mediaPlayer.setAudioAttributes(attributes);
    }

    public void play() {
        shouldPlay = true;

        if (speed == 0.0f) {
            speed = 1.0f;
        }
        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
        startLoopingThread();
    }

    public void pause() {
        mediaPlayer.pause();
        shouldPlay = false;
    }

    public void stop() {
        pause();
        mediaPlayer.seekTo(0);
    }

    public boolean isLoopActive() {
        return start != null && end != null;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        if (mediaPlayer != null && shouldPlay) {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
        }
    }

    public int getStartPosition() {
        if (!isLoopActive()) {
            return 0;
        }
        return Math.max(0, start.getPosition() - leadInTime);
    }

    public int getEndPosition() {
        if (!isLoopActive()) {
            return Integer.MAX_VALUE;
        }
        return end.getPosition() + leadOutTime;
    }

    public boolean isPlaying() {
        return shouldPlay;
    }

    public void seekTo(Track track) {
        mediaPlayer.seekTo(track.getPosition());
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
                if (shouldPlay) {
                    // If the loop cycle should end before normal delay, then update it earlier
                    int delay = Math.min(MainActivity.BACKGROUND_THREAD_DELAY, getEndPosition() - mediaPlayer.getCurrentPosition());
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
            if (mediaPlayer.getCurrentPosition() >= getEndPosition() ||
                    (shouldPlay && !mediaPlayer.isPlaying() && mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration())) {
                mediaPlayer.seekTo(getStartPosition());
                if (shouldPlay && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            }
        }
    }

    private void checkTrack() {
        if (currentTrack == null || mediaPlayer.getCurrentPosition() < currentTrack.getPosition() ||
                mediaPlayer.getCurrentPosition() > nextTrack.getPosition()) {
            currentTrack = playlist.getTrackForPosition(mediaPlayer.getCurrentPosition());
            nextTrack = playlist.getNextTrack(currentTrack);
            listeners.forEach(l -> l.trackChanged(currentTrack));
        }
    }

    public void ifInitialized(Runnable runnable) {
        if (initialized) {
            runnable.run();
        }
    }


    public void openFile(Context context, Uri musicFile) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(context, musicFile);
            mediaPlayer.prepare();
            initialized = true;
        } catch (IOException e) {
            // TODO: show error in a popup (don't know how currently...)
            e.printStackTrace();
        }
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
        if(! newTracks.isEmpty()) {
            currentTrack = null;
            checkTrack();
        }
    }

    public void addPlaybackListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    public int getDuration() {
        return mediaPlayer.getDuration();
    }

    public void seekTo(int progress) {
        mediaPlayer.seekTo(progress);
        checkTrack();
    }

    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public interface PlaybackListener {
        void trackChanged(Track newTrack);
    }
}
