package de.yoadey.choreomusic.ui.popups;


import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.masoudss.lib.WaveformSeekBar;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;

public class EditDialogFragment extends DialogFragment {

    private static final int SURROUND = 5000;

    private final Playlist playlist;
    private final Track track;
    private final PlaybackControl playbackControl;
    private final Handler handler;
    /* The changed position for the track which is applied if pressed Ok */
    private long editPosition;
    /* Whether the sample is currently playing */
    private boolean playSample;

    private View rootView;
    private boolean threadRunning;

    // Values used during moving the seekbar
    private float touchDownX;
    private long touchEditPosition = -1;

    public EditDialogFragment(PlaybackControl playbackControl, Playlist playlist, Track track) {
        this.playbackControl = playbackControl;
        this.playlist = playlist;
        this.track = track;
        this.editPosition = track.getPosition();
        handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.popup_edittrack, container,
                false);

        TextView renameTextView = rootView.findViewById(R.id.edittrackTextfield);
        renameTextView.setText(track.getLabel());

        WaveformSeekBar waveformSeekBar = rootView.findViewById(R.id.edittrackWaveformSeekBar);
        waveformSeekBar.setSample(getPartialWaveformData(editPosition - SURROUND, editPosition + SURROUND));
        waveformSeekBar.setOnTouchListener(this::onWaveformTouchEvent);

        MaterialButton play = rootView.findViewById(R.id.edittrackPlaypause);
        play.setOnClickListener(v -> onPlayChanged());

        Button okButton = rootView.findViewById(R.id.edittrackOk);
        okButton.setOnClickListener(view -> {
            track.setPosition(editPosition);
            track.setLabel(renameTextView.getText().toString());
            playlist.updateTrack(track);
            EditDialogFragment.this.dismiss();
        });
        Button cancelButton = rootView.findViewById(R.id.edittrackCancel);
        cancelButton.setOnClickListener(view -> EditDialogFragment.this.dismiss());

        return rootView;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if(playSample) {
            playbackControl.pause();
        }
        super.onDismiss(dialog);
    }

    private boolean onWaveformTouchEvent(View view, MotionEvent event) {
        // First song may not be modified
        if (!view.isEnabled()) {
            return false;
        }
        if(track.getPosition() == 0) {
            if(!playSample) {
                ((WaveformSeekBar) view).setProgress(50);
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            playSample = false;
            playbackControl.pause();
            playbackControl.seekTo((int) editPosition);
            MaterialButton play = rootView.findViewById(R.id.edittrackPlaypause);
            play.setIconResource(R.drawable.baseline_play_arrow_24);
            ((WaveformSeekBar) view).setProgress(50);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            float movedInMs = ((event.getX() - touchDownX) / view.getWidth()) * SURROUND * 2;
            touchEditPosition = editPosition - (int) movedInMs;
            touchEditPosition = Math.min(Math.max(0, touchEditPosition), playbackControl.getCurrentSong().getLength());
            updateSeekbar();
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            editPosition = touchEditPosition;
            touchEditPosition = -1;
            view.performClick();
        }
        return true;
    }

    private void updateSeekbar() {
        WaveformSeekBar waveformSeekBar = rootView.findViewById(R.id.edittrackWaveformSeekBar);
        if (touchEditPosition > 0) {
            waveformSeekBar.setSample(getPartialWaveformData(touchEditPosition - SURROUND, touchEditPosition + SURROUND));
        } else {
            waveformSeekBar.setSample(getPartialWaveformData(editPosition - SURROUND, editPosition + SURROUND));
        }
    }

    private int[] getPartialWaveformData(long start, long end) {
        Song song = playbackControl.getCurrentSong();
        long length = song.getLength();

        MainActivity mainActivity = (MainActivity) getActivity();
        int[] waveformData = mainActivity.getWaveformData();
        int startIndex = (int) (start * waveformData.length / length);
        int endIndex = (int) (end * waveformData.length / length);
        int[] partial = new int[endIndex - startIndex];
        System.arraycopy(waveformData, Math.max(0, startIndex),
                partial, Math.max(0, -1 * startIndex),
                Math.min(endIndex, waveformData.length) - Math.max(0, startIndex));
        return partial;
    }

    private void onPlayChanged() {
        if (!playbackControl.isInitialized()) {
            return;
        }
        MaterialButton play = rootView.findViewById(R.id.edittrackPlaypause);
        WaveformSeekBar seekbar = rootView.findViewById(R.id.edittrackWaveformSeekBar);
        if (!playSample) {
            playSample = true;
            playbackControl.seekTo((int) editPosition);
            play.setIconResource(R.drawable.baseline_stop_24);
            seekbar.setProgress(50);
            playbackControl.play();
            startLoopingThread();
        } else {
            playSample = false;
            playbackControl.pause();
            playbackControl.seekTo((int) editPosition);
            play.setIconResource(R.drawable.baseline_play_arrow_24);
            seekbar.setProgress(50);
        }
    }

    /**
     * Background thread to update the slider and timer
     */
    private void startLoopingThread() {
        synchronized (this) {
            if (threadRunning) {
                return;
            }

            threadRunning = true;
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                MaterialButton play = rootView.findViewById(R.id.edittrackPlaypause);
                WaveformSeekBar seekbar = rootView.findViewById(R.id.edittrackWaveformSeekBar);
                if (playbackControl.isPlaying() && playbackControl.getCurrentPosition() < editPosition + SURROUND) {
                    long position = (playbackControl.getCurrentPosition() - editPosition) * 50 / SURROUND + 50;
                    seekbar.setProgress((int) position);
                } else {
                    playSample = false;
                    if (playbackControl.isPlaying()) {
                        playbackControl.pause();
                        playbackControl.seekTo((int) editPosition);
                        play.setIconResource(R.drawable.baseline_play_arrow_24);
                        seekbar.setProgress(50);
                    }
                }

                // Restart handler
                synchronized (this) {
                    if (playSample) {
                        handler.postDelayed(this, MainActivity.BACKGROUND_THREAD_DELAY);
                    } else {
                        threadRunning = false;
                    }
                }
            }
        }, MainActivity.BACKGROUND_THREAD_DELAY);
    }
}