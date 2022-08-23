package de.yoadey.choreomusic.ui.tracks;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.service.PlaybackControl;
import de.yoadey.choreomusic.ui.popups.EditDialogFragment;

public class TrackViewAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TrackViewAdapter.TrackViewHolder>
        implements ServiceConnection, Playlist.PlaylistListener, PlaybackControl.PlaybackListener {

    private final Context context;
    private final Map<Track, ConstraintLayout> trackToLayout;
    private PlaybackControl playbackControl;
    private Playlist playlist;

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
        initializeListeners(i, track, layout);

        trackToLayout.entrySet().stream().filter(e -> e.getValue() == layout).map(Map.Entry::getKey)
                .findAny()
                .ifPresent(t -> trackToLayout.remove(track));
        trackToLayout.put(track, layout);
        initializeTexts(i, track, nextTrack, layout);
        initializeLoopButtons(track, nextTrack, layout);

        ProgressBar progressBar = layout.findViewById(R.id.trackProgressBar);
        // Max must be set before min, since otherwise min is set to the previous max value which is by default 100
        progressBar.setMax((int) nextTrack.getPosition());
        progressBar.setMin((int) track.getPosition());
        // Set Max after min again, as it might not be changed previously if it was below the previous min
        progressBar.setMax((int) nextTrack.getPosition());
        progressBar.setProgress(playbackControl.getCurrentPosition() > progressBar.getMax() ? 0 : (int) playbackControl.getCurrentPosition());

        if (playbackControl.getCurrentTrack() == track) {
            int color = getBrighterColor(track.getColor());
            layout.setBackgroundColor(color);
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary)));
            currentTrack = layout;
        } else {
            layout.setBackgroundColor(track.getColor());
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0));
        }
    }

    private void initializeListeners(int i, Track track, ConstraintLayout layout) {
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
    }

    @SuppressLint("DefaultLocale")
    private void initializeTexts(int i, Track track, Track nextTrack, ConstraintLayout layout) {
        TextView trackLabel = layout.findViewById(R.id.trackLabel);
        trackLabel.setText(track.getLabel());

        TextView number = layout.findViewById(R.id.trackNumber);
        number.setText(String.format("%d", i + 1));

        TextView time = layout.findViewById(R.id.trackTime);
        String timeText = String.format("%02d:%02d - %02d:%02d", track.getPosition() / 60000, track.getPosition() / 1000 % 60, nextTrack.getPosition() / 60000, nextTrack.getPosition() / 1000 % 60);
        time.setText(timeText);
    }

    private void initializeLoopButtons(Track track, Track nextTrack, ConstraintLayout layout) {
        if (playbackControl.getLoopStart() == track) {
            setLoopSelectedColors(track, R.id.trackLoopA);
        } else {
            setLoopUnselectedColors(track, R.id.trackLoopA);
        }
        MaterialButton loopA = layout.findViewById(R.id.trackLoopA);
        loopA.setOnClickListener(view -> loopA(track));

        if (playbackControl.getLoopEnd() == nextTrack) {
            setLoopSelectedColors(track, R.id.trackLoopB);
        } else {
            setLoopUnselectedColors(track, R.id.trackLoopB);
        }
        MaterialButton loopB = layout.findViewById(R.id.trackLoopB);
        loopB.setOnClickListener(view -> loopB(track, nextTrack));
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

    private void loopA(Track track) {
        if (track == playbackControl.getLoopStart()) {
            deactivateLoopA();
        } else {
            if (playbackControl.getLoopEnd() != null && playbackControl.getLoopEnd().getPosition() <= track.getPosition()) {
                deactivateLoopB();
            }
            activateLoopA(track);
        }
    }

    private void loopB(Track track, Track nextTrack) {
        if (nextTrack == playbackControl.getLoopEnd()) {
            deactivateLoopB();
        } else {
            if (playbackControl.getLoopStart() != null && playbackControl.getLoopStart().getPosition() >= nextTrack.getPosition()) {
                deactivateLoopA();
            }
            activateLoopB(track, nextTrack);
        }
    }

    private void activateLoopA(Track track) {
        setLoopSelectedColors(track, R.id.trackLoopA);
        setLoopUnselectedColors(playbackControl.getLoopStart(), R.id.trackLoopA);
        playbackControl.setLoopStart(track);
    }

    private void deactivateLoopA() {
        setLoopUnselectedColors(playbackControl.getLoopStart(), R.id.trackLoopA);
        playbackControl.setLoopStart(null);
    }

    private void activateLoopB(Track track, Track nextTrack) {
        setLoopSelectedColors(track, R.id.trackLoopB);
        setLoopUnselectedColors(playlist.getPreviousTrack(playbackControl.getLoopEnd()), R.id.trackLoopB);
        playbackControl.setLoopEnd(nextTrack);
    }

    private void deactivateLoopB() {
        setLoopUnselectedColors(playlist.getPreviousTrack(playbackControl.getLoopEnd()), R.id.trackLoopB);
        playbackControl.setLoopEnd(null);
    }

    private void setLoopSelectedColors(Track loopTrack, int type) {
        ConstraintLayout layout = trackToLayout.get(loopTrack);
        if (layout != null) {
            MaterialButton loopButton = layout.findViewById(type);
            if (loopButton != null) {
                loopButton.setTextColor(getColor(R.attr.loopSelectedTextColor));
                loopButton.setBackgroundColor(getColor(R.attr.loopSelectedColor));
            }
        }
    }

    private void setLoopUnselectedColors(Track loopTrack, int type) {
        ConstraintLayout layout = trackToLayout.get(loopTrack);
        if (layout != null) {
            MaterialButton loopButton = layout.findViewById(type);
            if (loopButton != null) {
                loopButton.setTextColor(getColor(R.attr.loopUnselectedTextColor));
                loopButton.setBackgroundColor(getColor(R.attr.loopUnselectedColor));
            }
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
        ((MainActivity) context).runOnUiThread(() -> {
            if (this.currentTrack != null) {
                trackToLayout.entrySet().stream().filter(e -> e.getValue() == currentTrack).findAny().ifPresent(e -> {
                    this.currentTrack.setBackgroundColor(e.getKey().getColor());
                    ProgressBar progressBar = currentTrack.findViewById(R.id.trackProgressBar);
                    progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                });
            }
            this.currentTrack = trackToLayout.get(newTrack);
            if (currentTrack != null) {
                int color = getBrighterColor(newTrack.getColor());
                this.currentTrack.setBackgroundColor(color);
                ProgressBar progressBar = currentTrack.findViewById(R.id.trackProgressBar);
                progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary)));
            }
            int position = playlist.getTracks().indexOf(newTrack);
            position = Math.max(0, Math.min(getItemCount() - 1, position));
            recyclerView.scrollToPosition(position);
            updateSliders();
        });
    }

    private int getBrighterColor(int color) {
        int[] colors = context.getResources().getIntArray(R.array.trackColors);
        for(int i = 0; i < colors.length; i++) {
            if(color == colors[i]) {
                return context.getResources().getIntArray(R.array.trackSelectedColors)[i];
            }
        }

        return getColor(R.attr.loopTrackSelectedColor);
    }

    @Override
    public void onProgressChanged(int progress) {
        updateSlider(playbackControl.getCurrentTrack(), progress);
    }

    private void updateSliders() {
        int progress = (int) playbackControl.getCurrentPosition();
        trackToLayout.forEach((track, layout) -> updateSlider(track, progress));
    }

    private void updateSlider(Track track, int progress) {
        ((MainActivity) context).runOnUiThread(() ->
                Optional.ofNullable(trackToLayout.get(track))
                        .map(layout -> (ProgressBar) layout.findViewById(R.id.trackProgressBar))
                        // Don't show progress if it isn't the current track
                        .ifPresent(pb -> pb.setProgress(progress > pb.getMax() ? 0 : progress)));
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
