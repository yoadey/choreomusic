package de.yoadey.choreomusic.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Playlist;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;
import de.yoadey.choreomusic.utils.Utils;

public class SongsViewAdapter extends RecyclerView.Adapter<SongsViewAdapter.TrackViewHolder> implements Playlist.PlaylistListener, PlaybackControl.PlaybackListener {

    private final List<Song> songs;
    private final Context context;
    private PlaybackControl playbackControl;

    private final ServiceConnection playbackControlConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            playbackControl = ((PlaybackControl.LocalBinder) iBinder).getInstance();
            playbackControl.addPlaybackListener(SongsViewAdapter.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            playbackControl.deletePlaybackListener(SongsViewAdapter.this);
            playbackControl = null;
        }
    };

    public SongsViewAdapter(List<Song> songs, Context context) {
        this.context = context;
        this.songs = songs;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        if (playbackControl == null) {
            context.bindService(new Intent(context, PlaybackControl.class), playbackControlConnection, Context.BIND_AUTO_CREATE);
        }
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
        layout.setOnClickListener(view -> playbackControl.openSong(song));

        if (playbackControl != null && playbackControl.getCurrentSong() == song) {
            layout.setBackgroundColor(Utils.getColor(context, R.style.loop, R.attr.loopTrackSelectedColor));
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


    @Override
    public int getItemCount() {
        return songs.size();
    }

    @Override
    public void notifyPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter) {
        notifyDataSetChanged();
    }

    @Override
    public void songChanged(Song newSong) {
        notifyDataSetChanged();
    }

    @Override
    public void trackChanged(Track newTrack) {
    }

    public void onDestroy() {
        if (playbackControl != null) {
            context.unbindService(playbackControlConnection);
        }
    }

    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        public TrackViewHolder(View view) {
            super(view);
        }
    }
}
