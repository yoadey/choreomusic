package de.yoadey.choreomusic.ui.popups;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;

public class PrePostDialogFragment extends DialogFragment {

    private final PlaybackControl playbackControl;
    private final PrePostDialogFragment dialog = this;

    public PrePostDialogFragment(PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.popup_preposttime, container,
                false);

        TextView leadInTextView = rootView.findViewById(R.id.prepostPreTime);
        leadInTextView.setText(Integer.toString(playbackControl.getLeadInTime()/1000));
        TextView leadOutTextView = rootView.findViewById(R.id.prepostPostTime);
        leadOutTextView.setText(Integer.toString(playbackControl.getLeadOutTime()/1000));

        View leadInPlusButton = rootView.findViewById(R.id.prepostPrePlus);
        leadInPlusButton.setOnClickListener(view -> changeValue(leadInTextView,1));
        View leadInMinusButton = rootView.findViewById(R.id.prepostPreMinus);
        leadInMinusButton.setOnClickListener(view -> changeValue(leadInTextView,-1));
        View leadOutPlusButton = rootView.findViewById(R.id.prepostPostPlus);
        leadOutPlusButton.setOnClickListener(view -> changeValue(leadOutTextView,1));
        View leadOutMinusButton = rootView.findViewById(R.id.prepostPostMinus);
        leadOutMinusButton.setOnClickListener(view -> changeValue(leadOutTextView,-1));

        Button okButton = rootView.findViewById(R.id.prepostOk);
        okButton.setOnClickListener(view -> {
            playbackControl.setLeadInTime(Integer.parseInt(leadInTextView.getText().toString())*1000);
            playbackControl.setLeadOutTime(Integer.parseInt(leadOutTextView.getText().toString())*1000);
            dialog.dismiss();
        });
        Button cancelButton = rootView.findViewById(R.id.prepostCancel);
        cancelButton.setOnClickListener(view -> dialog.dismiss());

        return rootView;
    }

    private void changeValue(TextView textView, int change) {
        int before = Integer.parseInt(textView.getText().toString());
        textView.setText(Integer.toString(Math.max(before + change, 0)));
    }
}