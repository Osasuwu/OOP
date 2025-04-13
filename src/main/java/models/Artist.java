package models;

import java.util.ArrayList;
import java.util.List;

public class Artist {
    private String id;
    private String artistName;
    private String spotifyId;
    private String spotifyLink;
    private int popularity;
    private String imageUrl;
    private List<String> genres;
    private List<Song> songs;
    private List<Album> albums;
    private double score;

    // Constructor accepting only the artist name
    public Artist(String name) {
        this(); // Call the default constructor to initialize lists and defaults
        this.artistName = name;
    }

    // Valid no-argument constructor
    public Artist() {
        this.id = "";
        this.artistName = "";
        this.spotifyId = "";
        this.spotifyLink = "";
        this.popularity = 0;
        this.imageUrl = "";
        this.genres = new ArrayList<>();
        this.songs = new ArrayList<>();
        this.albums = new ArrayList<>();
        this.score = 0.0;
    }

    // Additional constructor accepting both id and name
    public Artist(String id, String name) {
        this(); // Initialize defaults
        this.id = id;
        this.artistName = name;
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
        return spotifyLink;
    }

    public void setSpotifyLink(String spotifyLink) {
        this.spotifyLink = spotifyLink;
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

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    @Override
    public String toString() {
        return artistName;
    }

    /**
     * Returns a list of top songs for the artist.
     * You can update this logic later to return a meaningful list of songs.
     */
    public List<Song> getTopSongs() {
        // For now, return an empty list; later you can implement actual logic.
        return new ArrayList<>();
    }
}