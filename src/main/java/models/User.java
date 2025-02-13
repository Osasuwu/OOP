package models;

import java.util.List;

public class User {
    private String username;
    private List<String> favoriteGenres;
    private List<String> favoriteSongs;

    public User(String username, List<String> favoriteGenres) {
        this.username = username;
        this.favoriteGenres = favoriteGenres;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getFavoriteGenres() {
        return favoriteGenres;
    }

    public void setFavoriteGenres(List<String> favoriteGenres) {
        this.favoriteGenres = favoriteGenres;
    }

    public List<String> getFavoriteSongs() {
        return favoriteSongs;
    }

    public void setFavoriteSongs(List<String> favoriteSongs) {
        this.favoriteSongs = favoriteSongs;
    }
}