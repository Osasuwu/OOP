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
        this.preferences = userPreferencesMap;
        
        // Add null checks before accessing map values
        List<Object> nameList = userPreferencesMap.get("name");
        this.name = (nameList != null && !nameList.isEmpty()) ? (String) nameList.get(0) : "Default Playlist";
        
        List<Object> songCountList = userPreferencesMap.get("songCount");
        this.songCount = (songCountList != null && !songCountList.isEmpty()) ? (int) songCountList.get(0) : 10;
        
        this.genres = new ArrayList<>();
        List<Object> genresList = userPreferencesMap.get("genres");
        if (genresList != null) {
            for (Object genre : genresList) {
                genres.add((String) genre);
            }
        }
        
        this.excludeArtists = new ArrayList<>();
        List<Object> excludeArtistsList = userPreferencesMap.get("excludeArtists");
        if (excludeArtistsList != null) {
            for (Object artist : excludeArtistsList) {
                excludeArtists.add((String) artist);
            }
        }
        
        List<Object> strategyList = userPreferencesMap.get("selectionStrategy");
        this.selectionStrategy = (strategyList != null && !strategyList.isEmpty()) ? 
            PlaylistParameters.PlaylistSelectionStrategy.valueOf((String) strategyList.get(0)) : 
            PlaylistParameters.PlaylistSelectionStrategy.RANDOM; // Default to RANDOM if not specified
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