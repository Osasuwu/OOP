package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User preferences for playlist generation
 */
public class PlaylistPreferences {
    private String name;
    private int songCount;
    private List<String> genres;
    private List<String> excludeArtists;
    private PlaylistParameters.PlaylistSelectionStrategy selectionStrategy;
    private Map<String, Object> preferences;
    
    public PlaylistPreferences(Map<String, List<Object>> userPreferencesMap) {
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
    

    
    // Add getters and other methods as needed
    public Object getPreference(String key) {
        return preferences.get(key);
    }
    
    public Map<String, Object> getAllPreferences() {
        return preferences;
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