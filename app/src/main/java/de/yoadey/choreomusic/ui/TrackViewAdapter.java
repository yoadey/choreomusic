package de.yoadey.choreomusic.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.ui.popups.EditDialogFragment;

public class TrackViewAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TrackViewAdapter.TrackViewHolder>
        implements ServiceConnection, Playlist.PlaylistListener, PlaybackControl.PlaybackListener {

    private final Context context;
    private final Map<Track, ConstraintLayout> trackToLayout;
    private PlaybackControl playbackControl;
    private Playlist playlist;

    private MaterialButton loopA;
    private MaterialButton loopB;
    private ConstraintLayout currentTrack;
    private RecyclerView recyclerView;

    public TrackViewAdapter(Context context) {
        this.context = context;
        trackToLayout = new HashMap<>();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        super.onAttachedToRecyclerView(recyclerView);
        if (playbackControl == null) {
            context.bindService(new Intent(context, PlaybackControl.class), this, Context.BIND_AUTO_CREATE);
        }
    }

    @NotNull
    @Override
    public TrackViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        ConstraintLayout view = (ConstraintLayout) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.layout_track
                , viewGroup, false);
        return new TrackViewHolder(view);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(TrackViewHolder viewHolder, int i) {
        List<Track> tracks = playlist.getTracks();
        final Track track = tracks.get(i);
        final Track nextTrack = tracks.get(Math.min(i + 1, tracks.size() - 1));

        ConstraintLayout layout = (ConstraintLayout) viewHolder.itemView;
        layout.setOnClickListener(view -> playbackControl.seekTo(track));
        layout.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            MenuItem editItem = menu.add(R.string.track_edit);
            editItem.setOnMenuItemClickListener(item -> {
                EditDialogFragment dialogFragment = new EditDialogFragment(playbackControl, playlist, track);
                dialogFragment.show(((FragmentActivity) context).getSupportFragmentManager(), "OpenPopup");
                return true;
            });
            // Don't delete the start item, we always want to have at least one track
            if (i > 0) {
                MenuItem deleteItem = menu.add(R.string.delete);
                deleteItem.setOnMenuItemClickListener(item -> {
                    playlist.deleteItem(track);
                    return true;
                });
            }
        });

        trackToLayout.put(track, layout);
        if (playbackControl.getCurrentTrack() == track) {
            layout.setBackgroundColor(getColor(R.attr.loopTrackSelectedColor));
            currentTrack = layout;
        } else {
            layout.setBackgroundColor(Color.TRANSPARENT);
        }
        TextView trackLabel = layout.findViewById(R.id.trackLabel);
        trackLabel.setText(track.getLabel());

        TextView number = layout.findViewById(R.id.trackNumber);
        number.setText(String.format("%d", i + 1));

        TextView time = layout.findViewById(R.id.trackTime);
        String timeText;
        if (i < tracks.size() - 2) {
            timeText = String.format("%02d:%02d - %02d:%02d", track.getPosition() / 60000, track.getPosition() / 1000 % 60, nextTrack.getPosition() / 60000, nextTrack.getPosition() / 1000 % 60);
        } else {
            timeText = String.format("%02d:%02d - end", track.getPosition() / 60000, track.getPosition() / 1000 % 60);
        }
        time.setText(timeText);

        MaterialButton loopA = layout.findViewById(R.id.trackLoopA);
        if (playbackControl.getLoopStart() == track) {
            activateLoopA(nextTrack, loopA);
        } else {
            setLoopUnselectedColors(loopA);
        }
        loopA.setOnClickListener(view -> loopA(track, loopA));

        MaterialButton loopB = layout.findViewById(R.id.trackLoopB);
        if (playbackControl.getLoopEnd() == nextTrack) {
            activateLoopB(nextTrack, loopB);
        } else {
            setLoopUnselectedColors(loopB);
        }
        loopB.setOnClickListener(view -> loopB(nextTrack, loopB));
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        playbackControl = ((PlaybackControl.LocalBinder) iBinder).getInstance();
        playbackControl.addPlaybackListener(TrackViewAdapter.this);
        playlist = playbackControl.getPlaylist();
        playlist.addPlaylistListener(TrackViewAdapter.this);
        notifyDataSetChanged();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        playbackControl.deletePlaybackListener(TrackViewAdapter.this);
        playlist = playbackControl.getPlaylist();
        playlist.deletePlaylistListener(TrackViewAdapter.this);
        playlist = null;
        playbackControl = null;
        notifyDataSetChanged();
    }

    private void loopA(Track track, MaterialButton loopA) {
        if (track == playbackControl.getLoopStart()) {
            deactivateLoopA();
        } else {
            if (playbackControl.getLoopEnd() != null && playbackControl.getLoopEnd().getPosition() <= track.getPosition()) {
                deactivateLoopB();
            }
            activateLoopA(track, loopA);
        }
    }

    private void loopB(Track nextTrack, MaterialButton loopB) {
        if (nextTrack == playbackControl.getLoopEnd()) {
            deactivateLoopB();
        } else {
            if (playbackControl.getLoopStart() != null && playbackControl.getLoopStart().getPosition() >= nextTrack.getPosition()) {
                deactivateLoopA();
            }
            activateLoopB(nextTrack, loopB);
        }
    }

    private void activateLoopA(Track track, MaterialButton loopA) {
        playbackControl.setLoopStart(track);
        setLoopSelectedColors(loopA);
        setLoopUnselectedColors(this.loopA);
        this.loopA = loopA;
    }

    private void deactivateLoopA() {
        playbackControl.setLoopStart(null);
        setLoopUnselectedColors(loopA);
        this.loopA = null;
    }

    private void activateLoopB(Track nextTrack, MaterialButton loopB) {
        playbackControl.setLoopEnd(nextTrack);
        setLoopSelectedColors(loopB);
        setLoopUnselectedColors(this.loopB);
        this.loopB = loopB;
    }

    private void deactivateLoopB() {
        playbackControl.setLoopEnd(null);
        setLoopUnselectedColors(loopB);
        this.loopB = null;
    }

    private void setLoopSelectedColors(MaterialButton loopButton) {
        if (loopButton != null) {
            loopButton.setTextColor(getColor(R.attr.loopUnselectedTextColor));
            loopButton.setBackgroundColor(getColor(R.attr.loopUnselectedColor));
        }
    }

    private void setLoopUnselectedColors(MaterialButton loopButton) {
        if (loopButton != null) {
            loopButton.setTextColor(getColor(R.attr.loopUnselectedTextColor));
            loopButton.setBackgroundColor(getColor(R.attr.loopUnselectedColor));
        }
    }

    private int getColor(int attr) {
        @ColorInt int resultColor;
        int[] attrs = {attr};
        TypedArray ta = context.obtainStyledAttributes(R.style.loop, attrs);

        if (ta != null) {
            resultColor = ta.getColor(0, Color.TRANSPARENT);
            ta.recycle();
            return resultColor;
        }
        return Color.TRANSPARENT;
    }

    @Override
    public int getItemCount() {
        if (playlist == null) {
            return 0;
        }
        return playlist.getTracks().size() - 1;
    }

    @Override
    public void onPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        trackToLayout.clear();
        notifyDataSetChanged();
    }

    @Override
    public void onTrackChanged(Track newTrack) {
        if (this.currentTrack != null) {
            this.currentTrack.setBackgroundColor(getColor(R.attr.loopTrackUnselectedColor));
        }
        this.currentTrack = trackToLayout.get(newTrack);
        if (currentTrack != null) {
            this.currentTrack.setBackgroundColor(getColor(R.attr.loopTrackSelectedColor));
        }
        int position = playlist.getTracks().indexOf(newTrack);
        position = Math.max(0, Math.min(getItemCount() - 1, position));
        recyclerView.scrollToPosition(position);
    }

    public void onDestroy() {
        if (playbackControl != null) {
            context.unbindService(this);
        }
    }

    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        public TrackViewHolder(View view) {
            super(view);
        }
    }
}
