package de.yoadey.choreomusic.ui.popups;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.masoudss.lib.WaveformSeekBar;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.service.PlaybackControl;

public class EditDialogFragment extends DialogFragment implements PlaybackControl.PlaybackListener {

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

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getLayoutInflater();
        rootView = inflater.inflate(R.layout.popup_edittrack, null);

        TextView renameTextView = rootView.findViewById(R.id.edittrackTextfield);
        renameTextView.setText(track.getLabel());

        WaveformSeekBar waveformSeekBar = rootView.findViewById(R.id.edittrackWaveformSeekBar);
        waveformSeekBar.setSample(getPartialWaveformData(editPosition - SURROUND, editPosition + SURROUND));
        waveformSeekBar.setOnTouchListener(this::onWaveformTouchEvent);

        MaterialButton play = rootView.findViewById(R.id.edittrackPlaypause);
        play.setOnClickListener(v -> onPlayChanged());
        playbackControl.addPlaybackListener(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        return builder
                .setIcon(R.drawable.baseline_edit_24)
                .setTitle(R.string.edit_track)
                .setView(rootView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    track.setPosition(editPosition);
                    track.setLabel(renameTextView.getText().toString());
                    playlist.updateTrack(track);
                })
                .create();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (playSample) {
            playbackControl.pause();
        }
        playbackControl.deletePlaybackListener(this);
        super.onDismiss(dialog);
    }

    private boolean onWaveformTouchEvent(View view, MotionEvent event) {
        // First song may not be modified
        if (!view.isEnabled()) {
            return false;
        }
        if (track.getPosition() == 0) {
            if (!playSample) {
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
            updateSeekBar();
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            editPosition = touchEditPosition;
            touchEditPosition = -1;
            view.performClick();
        }
        return true;
    }

    private void updateSeekBar() {
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
        assert mainActivity != null;
        int[] waveformData = mainActivity.getWaveformData();
        if (waveformData == null || waveformData.length == 0) {
            return new int[]{0};
        }
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
        } else {
            playSample = false;
            playbackControl.pause();
            playbackControl.seekTo((int) editPosition);
            play.setIconResource(R.drawable.baseline_play_arrow_24);
            seekbar.setProgress(50);
        }
    }

    @Override
    public void onProgressChanged(int progress) {
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
    }

}