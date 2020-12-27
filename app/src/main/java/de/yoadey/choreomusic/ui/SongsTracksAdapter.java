package de.yoadey.choreomusic.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.PlaybackControl;
import de.yoadey.choreomusic.model.Song;

public class SongsTracksAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<SongsTracksAdapter.TabViewHolder> {
    private final List<Song> songs;
    private final PlaybackControl playbackControl;

    public SongsTracksAdapter(List<Song> songs, PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;
        this.songs = songs;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        ConstraintLayout view = (ConstraintLayout) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.layout_list
                , viewGroup, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        switch (position) {
            case 0:
                bindSongsViewHolder(holder);
                break;
            case 1:
                bindTracksViewHolder(holder);
                break;
        }
    }

    private void bindSongsViewHolder(TabViewHolder holder) {
        View view = holder.itemView;
        RecyclerView songsView = view.findViewById(R.id.items);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(view.getContext());
        songsView.setLayoutManager(layoutManager);
        SongsViewAdapter songViewAdapter = new SongsViewAdapter(songs, playbackControl, view.getContext());
        songsView.setAdapter(songViewAdapter);
    }

    private void bindTracksViewHolder(TabViewHolder holder) {
        View view = holder.itemView;
        RecyclerView tracksView = view.findViewById(R.id.items);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(view.getContext());
        tracksView.setLayoutManager(layoutManager);
        TrackViewAdapter trackViewAdapter = new TrackViewAdapter(playbackControl, view.getContext());
        tracksView.setAdapter(trackViewAdapter);
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    public static class TabViewHolder extends RecyclerView.ViewHolder {
        public TabViewHolder(View view) {
            super(view);
        }
    }
}
