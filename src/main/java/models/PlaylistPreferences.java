package models;

import java.util.ArrayList;
import java.util.List;

/**
 * User preferences for playlist generation
 */
public class PlaylistPreferences {
    private String name;
    private int songCount;
    private List<String> genres;
    private List<String> excludeArtists;
    private PlaylistParameters.PlaylistSelectionStrategy selectionStrategy;
    
    public PlaylistPreferences() {
        this.name = "New Playlist";
        this.songCount = 20;
        this.genres = new ArrayList<>();
        this.excludeArtists = new ArrayList<>();
        this.selectionStrategy = PlaylistParameters.PlaylistSelectionStrategy.BALANCED;
    }
    
    // Constructor from PlaylistParameters
    public PlaylistPreferences(PlaylistParameters params) {
        this.name = params.getName();
        this.songCount = params.getSongCount();
        this.genres = new ArrayList<>(params.getGenres());
        this.excludeArtists = new ArrayList<>(params.getExcludeArtists());
        this.selectionStrategy = params.getSelectionStrategy();
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

    public List<String> getExcludeArtists() {
        return excludeArtists;
    }

    public void setExcludeArtists(List<String> excludeArtists) {
        this.excludeArtists = excludeArtists;
    }

    public PlaylistParameters.PlaylistSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public void setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }
}