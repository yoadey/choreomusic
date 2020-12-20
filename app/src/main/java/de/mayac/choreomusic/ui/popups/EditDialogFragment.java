package de.mayac.choreomusic.ui.popups;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import de.mayac.choreomusic.R;
import de.mayac.choreomusic.model.Playlist;
import de.mayac.choreomusic.model.Track;

public class EditDialogFragment extends DialogFragment {

    private final Playlist playlist;
    private final Track track;
    private final EditDialogFragment dialog = this;

    public EditDialogFragment(Playlist playlist, Track track) {
        this.playlist = playlist;
        this.track = track;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.popup_edittrack, container,
                false);

        TextView renameTextView = rootView.findViewById(R.id.edittrackTextfield);
        renameTextView.setText(track.getLabel());

        View deleteButton = rootView.findViewById(R.id.edittrackDelete);
        deleteButton.setOnClickListener(view -> {
            playlist.deleteItem(track);
            dialog.dismiss();
        });

        Button okButton = rootView.findViewById(R.id.edittrackOk);
        okButton.setOnClickListener(view -> {
            track.setLabel(renameTextView.getText().toString());
            playlist.updateTrack(track);
            dialog.dismiss();
        });
        Button cancelButton = rootView.findViewById(R.id.edittrackCancel);
        cancelButton.setOnClickListener(view -> dialog.dismiss());

        return rootView;
    }
}