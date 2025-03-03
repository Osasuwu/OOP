package models;

import java.util.List;
import java.util.ArrayList;

public class UserMusicData {
    private List<Song> songs = new ArrayList<>();
    private List<Artist> artists = new ArrayList<>();
    private List<PlayHistory> playHistory = new ArrayList<>();
    
    public List<Song> getSongs() { return songs; }
    public void setSongs(List<Song> songs) { this.songs = songs; }

    public List<Artist> getArtists() { return artists; }
    public void setArtists(List<Artist> artists) { this.artists = artists; }

    public List<PlayHistory> getPlayHistory() { return playHistory; }
    public void setPlayHistory(List<PlayHistory> playHistory) { this.playHistory = playHistory; }

    public void merge(UserMusicData other) {
        if (other != null) {
            this.songs.addAll(other.getSongs());
            this.artists.addAll(other.getArtists());
            this.playHistory.addAll(other.getPlayHistory());
        }
    }

    public boolean isEmpty() {
        return songs.isEmpty() && artists.isEmpty() && playHistory.isEmpty();
    }
}