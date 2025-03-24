package models;

import java.util.ArrayList;
import java.util.List;

public class Artist {
    private String ArtistName;
    private String spotifyId;
    private int popularity;
    private String imageUrl;
    private List<String> genres;

    public Artist(String name) {
        this.ArtistName = name;
        this.genres = new ArrayList<>();
    }

    public String getName() {
        return ArtistName;
    }

    public void setName(String name) {
        this.ArtistName = name;
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

    public void setId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public String getId() {
        return spotifyId;
    }

    @Override
    public String toString() {
        return ArtistName;
    }
}