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
    private Map<String, List<Object>> preferences;
    
    public PlaylistPreferences(Map<String, List<Object>> userPreferencesMap) {
        this.name = (String) userPreferencesMap.get("name").get(0);
        this.songCount = (int) userPreferencesMap.get("songCount").get(0);
        this.genres = new ArrayList<>();
        for (Object genre : userPreferencesMap.get("genres")) {
            genres.add((String) genre);
        }
        this.excludeArtists = new ArrayList<>();
        for (Object artist : userPreferencesMap.get("excludeArtists")) {
            excludeArtists.add((String) artist);
        }
        this.selectionStrategy = PlaylistParameters.PlaylistSelectionStrategy.valueOf((String) userPreferencesMap.get("selectionStrategy").get(0));
        this.preferences = userPreferencesMap;
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
    
    public Map<String, List<Object>> getAllPreferences() {
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