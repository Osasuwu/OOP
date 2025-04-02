package models;

import java.util.ArrayList;
import java.util.List;

public class Artist {
    private String artistName;
    private String spotifyId;
    private String SpotifyLink;
    private int popularity;
    private String imageUrl;
    private List<String> genres;

    public Artist(String name) {
        this.artistName = name;
        this.genres = new ArrayList<>();
    }

    public String getName() {
        return artistName;
    }

    public void setName(String name) {
        this.artistName = name;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public void setSpotifyId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getSpotifyLink() {
        return SpotifyLink;
    }

    public void setSpotifyLink(String spotifyLink) {
        this.SpotifyLink = spotifyLink;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public void addGenre(String genre) {
        if (!genres.contains(genre)) {
            genres.add(genre);
        }
    }

    // Provided id accessor methods (using spotifyId as the id)
    public void setId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public String getId() {
        return spotifyId;
    }
    
    public List<Song> getTopSongs() {
        // Return a list of top songs by the artist
        return new ArrayList<>(); // Replace with actual logic for retrieving top songs
    }

    @Override
    public String toString() {
        return artistName;
    }
}