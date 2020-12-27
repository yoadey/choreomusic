package de.yoadey.choreomusic.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;

public class SongsViewAdapter extends RecyclerView.Adapter<SongsViewAdapter.TrackViewHolder> implements Playlist.PlaylistListener, PlaybackControl.PlaybackListener {

    private final List<Song> songs;
    private final PlaybackControl playbackControl;
    private final Map<Song, ConstraintLayout> songToLayout;
    private final Context context;
    private ConstraintLayout currentSong;

    public SongsViewAdapter(List<Song> songs, PlaybackControl playbackControl, Context context) {
        this.playbackControl = playbackControl;
        playbackControl.getCurrentSong();
        playbackControl.addPlaybackListener(this);
        this.context = context;
        this.songs = songs;
        songToLayout = new HashMap<>();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        playbackControl.deletePlaybackListener(this);
    }

    @NotNull
    @Override
    public TrackViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        ConstraintLayout view = (ConstraintLayout) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.layout_song
                , viewGroup, false);
        return new TrackViewHolder(view);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(TrackViewHolder viewHolder, int i) {
        final Song song = songs.get(i);

        ConstraintLayout layout = (ConstraintLayout) viewHolder.itemView;
        layout.setOnClickListener(view -> playbackControl.openSong(context, song));

        songToLayout.put(song, layout);
        if (playbackControl.getCurrentSong() == song) {
            layout.setBackgroundColor(getColor(R.attr.loopTrackSelectedColor));
            currentSong = layout;
        } else {
            layout.setBackgroundColor(Color.TRANSPARENT);
        }
        TextView songTitle = layout.findViewById(R.id.songTitle);
        songTitle.setText(song.getTitle());

        TextView number = layout.findViewById(R.id.songNumber);
        number.setText(String.format("%d", i + 1));

        TextView time = layout.findViewById(R.id.songTime);
        String timeText = String.format("%02d:%02d", song.getLength() / 60_000, song.getLength() / 1_000 % 60);
        time.setText(timeText);
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
        return songs.size();
    }

    @Override
    public void notifyPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        songToLayout.clear();
        notifyDataSetChanged();
    }

    @Override
    public void songChanged(Song newSong) {
        notifyDataSetChanged();
    }

    @Override
    public void trackChanged(Track newTrack) {
    }

    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        public TrackViewHolder(View view) {
            super(view);
        }
    }
}
