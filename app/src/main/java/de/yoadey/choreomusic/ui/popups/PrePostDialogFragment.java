package de.yoadey.choreomusic.ui.popups;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.service.PlaybackControl;

public class PrePostDialogFragment extends DialogFragment {

    private final PlaybackControl playbackControl;

    public PrePostDialogFragment(PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getLayoutInflater();
        View rootView = inflater.inflate(R.layout.popup_preposttime, null);

        TextView leadInTextView = rootView.findViewById(R.id.prepostPreTime);
        leadInTextView.setText(Long.toString(playbackControl.getLeadInTime() / 1000L));
        TextView leadOutTextView = rootView.findViewById(R.id.prepostPostTime);
        leadOutTextView.setText(Long.toString(playbackControl.getLeadOutTime() / 1000L));

        View leadInPlusButton = rootView.findViewById(R.id.prepostPrePlus);
        leadInPlusButton.setOnClickListener(view -> changeValue(leadInTextView, 1));
        View leadInMinusButton = rootView.findViewById(R.id.prepostPreMinus);
        leadInMinusButton.setOnClickListener(view -> changeValue(leadInTextView, -1));
        View leadOutPlusButton = rootView.findViewById(R.id.prepostPostPlus);
        leadOutPlusButton.setOnClickListener(view -> changeValue(leadOutTextView, 1));
        View leadOutMinusButton = rootView.findViewById(R.id.prepostPostMinus);
        leadOutMinusButton.setOnClickListener(view -> changeValue(leadOutTextView, -1));

        TextView preVolumeText = rootView.findViewById(R.id.preVolumeText);
        SeekBar preVolume = rootView.findViewById(R.id.preVolume);
        preVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                preVolumeText.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        preVolume.setProgress((int) (playbackControl.getLeadInVolume() * 100));


        TextView postVolumeText = rootView.findViewById(R.id.postVolumeText);
        SeekBar postVolume = rootView.findViewById(R.id.postVolume);
        postVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                postVolumeText.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        postVolume.setProgress((int) (playbackControl.getLeadOutVolume() * 100));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        return builder
                .setIcon(R.drawable.baseline_more_time_24)
                .setTitle(R.string.prepost_title)
                .setView(rootView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    playbackControl.setLeadInTime(Integer.parseInt(leadInTextView.getText().toString()) * 1000L);
                    playbackControl.setLeadOutTime(Integer.parseInt(leadOutTextView.getText().toString()) * 1000L);
                    playbackControl.setLeadInVolume(preVolume.getProgress() / 100.0f);
                    playbackControl.setLeadOutVolume(postVolume.getProgress() / 100.0f);
                })
                .create();
    }

    @SuppressLint("SetTextI18n")
    private void changeValue(TextView textView, int change) {
        int before = Integer.parseInt(textView.getText().toString());
        textView.setText(Integer.toString(Math.max(before + change, 0)));
    }
}