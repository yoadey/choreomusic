package de.yoadey.choreomusic.ui.tracks;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.ObservableList;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import de.yoadey.choreomusic.MainActivity;
import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.service.PlaybackControl;
import de.yoadey.choreomusic.utils.Utils;

public class SongsViewAdapter extends RecyclerView.Adapter<SongsViewAdapter.TrackViewHolder> implements PlaybackControl.PlaybackListener {

    private final ObservableList<Song> songs;
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

    public SongsViewAdapter(ObservableList<Song> songs, Context context) {
        this.context = context;
        this.songs = songs;
        songs.addOnListChangedCallback(new ObservableListChanged());
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
        layout.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            MenuItem deleteItem = menu.add(R.string.delete);
            deleteItem.setOnMenuItemClickListener(item -> {
                songs.remove(song);
                ((MainActivity) context).getDatabaseHelper().deleteSong(song);
                if(playbackControl.getCurrentSong() == song) {
                    playbackControl.openSong(null);
                }
                return true;
            });
        });

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
    public void onSongChanged(Song newSong) {
        listChanged();
    }

    public void onDestroy() {
        if (playbackControl != null) {
            context.unbindService(playbackControlConnection);
        }
    }

    private void listChanged() {
        ((MainActivity) context).runOnUiThread(this::notifyDataSetChanged);
    }

    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        public TrackViewHolder(View view) {
            super(view);
        }
    }

    private class ObservableListChanged extends ObservableList.OnListChangedCallback<ObservableList<Song>> {
        @Override
        public void onChanged(ObservableList<Song> sender) {
            listChanged();
        }

        @Override
        public void onItemRangeChanged(ObservableList<Song> sender, int positionStart, int itemCount) {
            listChanged();
        }

        @Override
        public void onItemRangeInserted(ObservableList<Song> sender, int positionStart, int itemCount) {
            listChanged();
        }

        @Override
        public void onItemRangeMoved(ObservableList<Song> sender, int fromPosition, int toPosition, int itemCount) {
            listChanged();
        }

        @Override
        public void onItemRangeRemoved(ObservableList<Song> sender, int positionStart, int itemCount) {
            listChanged();
        }
    }
}
