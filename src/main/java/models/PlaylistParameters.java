package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for playlist generation
 */
public class PlaylistParameters {
    private String name;
    private int songCount;
    private List<String> genres;
    private List<String> artists;
    private List<String> excludeArtists;
    private int minDuration; // in seconds
    private int maxDuration; // in seconds
    private boolean preferImportedMusic;
    private PlaylistSelectionStrategy selectionStrategy;
    
    public enum PlaylistSelectionStrategy {
        RANDOM, POPULAR, DIVERSE, BALANCED
    }
    
    public PlaylistParameters() {
        this.name = "New Playlist";
        this.songCount = 20;
        this.genres = new ArrayList<>();
        this.artists = new ArrayList<>();
        this.excludeArtists = new ArrayList<>();
        this.minDuration = 0;
        this.maxDuration = Integer.MAX_VALUE;
        this.preferImportedMusic = false;
        this.selectionStrategy = PlaylistSelectionStrategy.BALANCED;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }
    
    public void addGenre(String genre) {
        if (!this.genres.contains(genre)) {
            this.genres.add(genre);
        }
    }

    public List<String> getArtists() {
        return artists;
    }

    public void setArtists(List<String> artists) {
        this.artists = artists;
    }
    
    public void addArtist(String artist) {
        if (!this.artists.contains(artist)) {
            this.artists.add(artist);
        }
    }

    public List<String> getExcludeArtists() {
        return excludeArtists;
    }

    public void setExcludeArtists(List<String> excludeArtists) {
        this.excludeArtists = excludeArtists;
    }
    
    public void addExcludedArtist(String artist) {
        if (!this.excludeArtists.contains(artist)) {
            this.excludeArtists.add(artist);
        }
    }

    public int getMinDuration() {
        return minDuration;
    }

    public void setMinDuration(int minDuration) {
        this.minDuration = minDuration;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(int maxDuration) {
        this.maxDuration = maxDuration;
    }

    public boolean isPreferImportedMusic() {
        return preferImportedMusic;
    }

    public void setPreferImportedMusic(boolean preferImportedMusic) {
        this.preferImportedMusic = preferImportedMusic;
    }

    public PlaylistSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public void setSelectionStrategy(PlaylistSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }
}
