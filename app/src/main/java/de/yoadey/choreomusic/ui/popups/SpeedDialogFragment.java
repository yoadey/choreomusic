package de.yoadey.choreomusic.ui.popups;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.google.android.material.slider.Slider;

import org.jetbrains.annotations.NotNull;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.service.PlaybackControl;

public class SpeedDialogFragment extends DialogFragment {

    private final PlaybackControl playbackControl;
    private float speed;

    public SpeedDialogFragment(PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;
    }

    @SuppressLint("DefaultLocale")
    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getLayoutInflater();
        View rootView = inflater.inflate(R.layout.popup_speed, null);

        speed = playbackControl.getSpeed();
        TextView speedTextView = rootView.findViewById(R.id.speedTextView);
        speedTextView.setText(String.format("%.2f", playbackControl.getSpeed()));
        Slider speedSlider = rootView.findViewById(R.id.speedSlider);
        speedSlider.setValue(speedToPosition(playbackControl.getSpeed()));
        speedSlider.addOnChangeListener((slider, progress, fromUser) -> {
            if (fromUser) {
                speed = positionToSpeed(progress);
                speedTextView.setText(String.format("%.2f", speed));
            }
        });

        View minus10Button = rootView.findViewById(R.id.speedMinus10);
        minus10Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView, -0.10f));
        View minus1Button = rootView.findViewById(R.id.speedMinus1);
        minus1Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView, -0.01f));
        View resetButton = rootView.findViewById(R.id.speedReset);
        resetButton.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView, 0f));
        View plus1Button = rootView.findViewById(R.id.speedPlus1);
        plus1Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView, 0.01f));
        View plus10Button = rootView.findViewById(R.id.speedPlus10);
        plus10Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView, 0.10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        return builder
                .setIcon(R.drawable.baseline_speed_24)
                .setTitle(R.string.speed_title)
                .setView(rootView)
                .setPositiveButton(R.string.ok, (dialog, which) -> playbackControl.setSpeed(speed))
                .create();
    }

    @SuppressLint("DefaultLocale")
    private void changeSpeed(Slider speedSeeker, TextView textView, float change) {
        float newSpeed = speed + change;
        if (change == 0) {
            newSpeed = 1;
        }
        newSpeed = Math.max(0.5f, newSpeed);
        newSpeed = Math.min(2.0f, newSpeed);
        speed = newSpeed;
        textView.setText(String.format("%.2f", newSpeed));
        speedSeeker.setValue(speedToPosition(newSpeed));
    }

    private float speedToPosition(float speed) {
        return (float) (Math.log(speed) / Math.log(2) * 500.0 + 500.0);
    }

    private float positionToSpeed(float position) {
        return (float) (Math.pow(2, position / 500.0) * 0.5);
    }
}