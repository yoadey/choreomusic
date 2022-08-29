package de.yoadey.choreomusic.ui.popups;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.service.PlaybackControl;
import de.yoadey.choreomusic.ui.layouts.CutoutDrawable;

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

        View prepos_precontent = rootView.findViewById(R.id.prepost_precontent);
        View prepos_prelabel = rootView.findViewById(R.id.prepost_prelabel);
        cutoutFrame(prepos_precontent, prepos_prelabel);
        View prepos_postcontent = rootView.findViewById(R.id.prepost_postcontent);
        View prepos_postlabel = rootView.findViewById(R.id.prepost_postlabel);
        cutoutFrame(prepos_postcontent, prepos_postlabel);

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
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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

    private void cutoutFrame(View content, View label) {
        // configuration of the shape for the outline
        ShapeAppearanceModel shape = new ShapeAppearanceModel.Builder()
                .setAllCorners(new RoundedCornerTreatment())
                .setAllCornerSizes(16f)
                .build();

        CutoutDrawable drawable = new CutoutDrawable(shape);
        drawable.setStroke(4f, getContext().getColor(R.color.primary));
        drawable.setFillColor(ColorStateList.valueOf(0));

        content.setBackground(drawable);
        label.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
                    // offset the position by the margin of the content view
                    int realLeft = left - content.getLeft();
                    int realTop = top - content.getTop();
                    int realRigth = right - content.getLeft();
                    int realBottom = bottom - content.getTop();
                    // update the cutout part of the drawable
                    drawable.setCutout(
                            (float) realLeft,
                            (float) realTop,
                            (float) realRigth,
                            (float) realBottom);
                }
        );
    }

    @SuppressLint("SetTextI18n")
    private void changeValue(TextView textView, int change) {
        int before = Integer.parseInt(textView.getText().toString());
        textView.setText(Integer.toString(Math.max(before + change, 0)));
    }
}