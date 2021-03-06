package de.yoadey.choreomusic.ui.popups;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;

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

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        return builder
                .setIcon(R.drawable.baseline_more_time_24)
                .setTitle(R.string.prepost_title)
                .setView(rootView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    playbackControl.setLeadInTime(Integer.parseInt(leadInTextView.getText().toString()) * 1000);
                    playbackControl.setLeadOutTime(Integer.parseInt(leadOutTextView.getText().toString()) * 1000);
                })
                .create();
    }

    @SuppressLint("SetTextI18n")
    private void changeValue(TextView textView, int change) {
        int before = Integer.parseInt(textView.getText().toString());
        textView.setText(Integer.toString(Math.max(before + change, 0)));
    }
}