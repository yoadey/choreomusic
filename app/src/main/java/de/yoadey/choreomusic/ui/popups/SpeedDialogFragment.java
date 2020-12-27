package de.yoadey.choreomusic.ui.popups;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.google.android.material.slider.Slider;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;

public class SpeedDialogFragment extends DialogFragment {

    private final PlaybackControl playbackControl;
    private final SpeedDialogFragment dialog = this;
    private float speed;

    public SpeedDialogFragment(PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.popup_speed, container,
                false);

        speed = playbackControl.getSpeed();
        TextView speedTextView = rootView.findViewById(R.id.speedTextView);
        speedTextView.setText(String.format("%.2f", playbackControl.getSpeed()));
        Slider speedSlider = rootView.findViewById(R.id.speedSlider);
        speedSlider.setValue(speedToPosition(playbackControl.getSpeed()));
        speedSlider.addOnChangeListener( (slider, progress, fromUser) -> {
                if(fromUser) {
                    speed = positionToSpeed(progress);
                    speedTextView.setText(String.format("%.2f", speed));
                }
        });

        View minus10Button = rootView.findViewById(R.id.speedMinus10);
        minus10Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView,-0.10f));
        View minus1Button = rootView.findViewById(R.id.speedMinus1);
        minus1Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView,-0.01f));
        View resetButton = rootView.findViewById(R.id.speedReset);
        resetButton.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView,0f));
        View plus1Button = rootView.findViewById(R.id.speedPlus1);
        plus1Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView,0.01f));
        View plus10Button = rootView.findViewById(R.id.speedPlus10);
        plus10Button.setOnClickListener(view -> changeSpeed(speedSlider, speedTextView,0.10f));

        Button okButton = rootView.findViewById(R.id.speedOk);
        okButton.setOnClickListener(view -> {
            playbackControl.setSpeed(speed);
            dialog.dismiss();
        });

        return rootView;
    }

    private void changeSpeed(Slider speedSeeker, TextView textView, float change) {
        float newSpeed = speed + change;
        if(change == 0) {
            newSpeed = 1;
        }
        newSpeed = Math.max(0.5f, newSpeed);
        newSpeed = Math.min(2.0f, newSpeed);
        speed = newSpeed;
        textView.setText(String.format("%.2f", newSpeed));
        speedSeeker.setValue(speedToPosition(newSpeed));
    }

    private float speedToPosition(float speed) {
        return (float) (Math.log(speed)/Math.log(2)*500.0+500.0);
    }

    private float positionToSpeed(float position) {
        return (float) (Math.pow(2, position/500.0)*0.5);
    }
}