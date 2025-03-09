package models;

import java.util.List;
import java.util.ArrayList;

public class UserMusicData {
    private List<Song> songs;
    private List<Artist> artists;
    private List<PlayHistory> playHistory;
    private List<String> favoriteGenres;

    public UserMusicData() {
        songs = new ArrayList<>();
        artists = new ArrayList<>();
        playHistory = new ArrayList<>();
        favoriteGenres = new ArrayList<>();
    }

    public List<Song> getSongs() { return songs; }
    public void setSongs(List<Song> songs) { this.songs = songs; }

    public List<Artist> getArtists() { return artists; }
    public void setArtists(List<Artist> artists) { this.artists = artists; }

    public List<PlayHistory> getPlayHistory() { return playHistory; }
    public void setPlayHistory(List<PlayHistory> playHistory) { this.playHistory = playHistory; }

    public List<String> getFavoriteGenres() { return favoriteGenres; }
    public void setFavoriteGenres(List<String> favoriteGenres) { this.favoriteGenres = favoriteGenres; }

    public void addSong(Song song) {
        songs.add(song);
    }

    public void addArtist(Artist artist) {
        artists.add(artist);
    }

    public void addFavoriteGenre(String genre) {
        if (!favoriteGenres.contains(genre)) {
            favoriteGenres.add(genre);
        }
    }

    public void merge(UserMusicData other) {
        if (other != null) {
            this.songs.addAll(other.getSongs());
            this.artists.addAll(other.getArtists());
            this.playHistory.addAll(other.getPlayHistory());
            this.favoriteGenres.addAll(other.getFavoriteGenres());
        }
    }

    public boolean isEmpty() {
        return songs.isEmpty() && artists.isEmpty() && playHistory.isEmpty() && favoriteGenres.isEmpty();
    }
}