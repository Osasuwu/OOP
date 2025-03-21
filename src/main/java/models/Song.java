package models;

import java.util.ArrayList;
import java.util.List;

public class Song {
    private String id;
    private String title;
    private String artistName;
    private Artist artist;
    private String album;
    private long durationMs;
    private int playCount;
    private String spotifyId;
    private String ArtistSpotifyId;
    private String imageUrl;
    private int popularity;
    private List<String> genres;

    public Song(String title, String artistName) {
        this.title = title;
        this.artistName = artistName;
        this.genres = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Sets the artist for this song
     * @param artist The artist to set
     */
    public void setArtist(Artist artist) {
        this.artist = artist;
        if (artist != null) {
            this.artistName = artist.getName();
        }
    }

    /**
     * Ensures artist is not null, creating one if needed based on artist name
     * @return The artist associated with this song
     */
    public Artist getArtist() {
        // If artist is null but we have artistName, create a simple artist object
        if (artist == null && artistName != null && !artistName.isEmpty()) {
            artist = new Artist(artistName);
        }
        return artist;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public void setSpotifyId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    // Extract Spotify ID from Spotify URL
    public void setSpotifyUrlOrId(String spotifyUrlOrId) {
        if (spotifyUrlOrId == null || spotifyUrlOrId.isEmpty()) {
            return;
        }
        
        if (spotifyUrlOrId.contains("spotify.com/track/")) {
            // Extract ID from URL
            String[] parts = spotifyUrlOrId.split("track/");
            if (parts.length > 1) {
                this.spotifyId = parts[1];
            }
        } else {
            // Assume it's already an ID
            this.spotifyId = spotifyUrlOrId;
        }
    }

    @Override
    public String toString() {
        return title + " by " + artistName;
    }
}