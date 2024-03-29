package de.yoadey.choreomusic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class Playlist {
    private final List<Track> tracks;
    private final Set<PlaylistListener> listeners;

    public Playlist() {
        listeners = ConcurrentHashMap.newKeySet();
        this.tracks = new ArrayList<>();
    }

    public List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    public void addTrack(Track track) {
        int index = tracks.indexOf(track);
        if (index >= 0) {
            // Do not add a track if it already exists
            return;
        }
        tracks.add(track);
        tracks.sort((o1, o2) -> Long.compare(o1.getPosition(), o2.getPosition()));
        firePlaylistChanged(singletonList(track), emptyList());
    }

    public void updateTrack(Track track) {
        firePlaylistChanged(singletonList(track), emptyList());
    }

    public void deleteItem(Track track) {
        int index = tracks.indexOf(track);
        if (index >= 0) {
            deleteItem(index);
        }
    }

    public void deleteItem(int i) {
        Track track = tracks.remove(i);
        firePlaylistChanged(emptyList(), singletonList(track));
    }

    public void addPlaylistListener(PlaylistListener listener) {
        this.listeners.add(listener);
    }

    public void deletePlaylistListener(PlaylistListener listener) {
        this.listeners.remove(listener);
    }

    public Track getTrackForPosition(long position) {
        position = Math.max(position, 0);
        Track lastTrack = null;
        Track nextTrack = null;
        for (int i = 0; i < tracks.size(); i++) {
            lastTrack = nextTrack;
            nextTrack = tracks.get(i);
            if (lastTrack != null && position >= lastTrack.getPosition() && position < nextTrack.getPosition()) {
                return lastTrack;
            }
        }
        return lastTrack;
    }

    public Track getPreviousTrack(Track track) {
        int i = Math.max(tracks.indexOf(track) - 1, 0);
        return tracks.get(i);
    }

    public Track getNextTrack(Track track) {
        int i = Math.min(tracks.indexOf(track) + 1, tracks.size() - 1);
        return tracks.get(i);
    }

    public void reset(List<Track> tracks, long length) {
        Set<Track> oldTracks = new HashSet<>(this.tracks);
        this.tracks.clear();
        this.tracks.addAll(tracks);
        this.tracks.sort((o1, o2) -> Long.compare(o1.getPosition(), o2.getPosition()));
        if (this.tracks.isEmpty() || this.tracks.stream().noneMatch(track -> track.getPosition() == 0)) {
            this.tracks.add(0, new Track(0, "Start"));
        }
        if (this.tracks.stream().noneMatch(track -> track.getPosition() == length)) {
            this.tracks.add(new Track(length, "End"));
        }
        // calculate real differences
        List<Track> newTracks = this.tracks.stream() //
                .filter(t -> !oldTracks.contains(t)) //
                .collect(Collectors.toList());
        List<Track> oldTracksList = oldTracks.stream() //
                .filter(t -> !this.tracks.contains(t)) //
                .collect(Collectors.toList());
        firePlaylistChanged(newTracks, oldTracksList);
    }

    private void firePlaylistChanged(List<Track> newTracks, List<Track> oldTracksList) {
        listeners.forEach(l -> l.onPlaylistChanged(newTracks, oldTracksList, getTracks()));
    }


    public interface PlaylistListener {
        void onPlaylistChanged(List<Track> newTracks, List<Track> deletedTracks, List<Track> playlistAfter);
    }
}
