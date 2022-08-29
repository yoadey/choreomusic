package de.yoadey.choreomusic.ui.tracks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.ObservableList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.model.Song;

public class SongsTracksAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<SongsTracksAdapter.TabViewHolder> {
    private final Context context;
    private final ObservableList<Song> songs;
    private SongsViewAdapter songsViewAdapter;
    private TrackViewAdapter trackViewAdapter;

    public SongsTracksAdapter(Context context, ObservableList<Song> songs) {
        this.context = context;
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
        if (songsViewAdapter == null) {
            songsViewAdapter = new SongsViewAdapter(songs, context);
        }
        songsView.setAdapter(songsViewAdapter);
    }

    private void bindTracksViewHolder(TabViewHolder holder) {
        View view = holder.itemView;
        RecyclerView tracksView = view.findViewById(R.id.items);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(view.getContext());
        tracksView.setLayoutManager(layoutManager);
        if (trackViewAdapter == null) {
            trackViewAdapter = new TrackViewAdapter(context);
        }
        tracksView.setAdapter(trackViewAdapter);
    }

    public void onDestroy() {
        if (trackViewAdapter != null) {
            trackViewAdapter.onDestroy();
        }
        if (songsViewAdapter != null) {
            songsViewAdapter.onDestroy();
        }
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
