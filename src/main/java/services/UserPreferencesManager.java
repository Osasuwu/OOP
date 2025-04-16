package services;

import models.Song;
import models.UserMusicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.GenreMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages user preferences, including calculation and updating
 */
public class UserPreferencesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferencesManager.class);

    /**
     * Calculates user preferences based on music data
     * 
     * @param userData The user's music data
     * @return Map of preferences
     */
    public Map<String, List<Object>> calculateUserPreferences(UserMusicData userData) {
        LOGGER.debug("Calculating user preferences");
        Map<String, List<Object>> preferences = new HashMap<>();
        
        // Calculate genre preferences
        Map<String, Integer> genreCounts = new HashMap<>();
        if (userData.getSongs() != null) {
            for (Song song : userData.getSongs()) {
                if (song.getGenres() != null) {
                    for (String genre : song.getGenres()) {
                        String normalizedGenre = GenreMapper.normalizeGenre(genre);
                        genreCounts.merge(normalizedGenre, 1, Integer::sum);
                    }
                }
            }
        }
        
        // Get top genres
        List<String> topGenres = genreCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        preferences.put("favorite_genres", new ArrayList<>(topGenres.stream().map(g -> (Object) g).collect(Collectors.toList())));
        LOGGER.debug("Found {} top genres", topGenres.size());
        
        // Calculate artist preferences
        Map<String, Integer> artistCounts = new HashMap<>();
        if (userData.getPlayHistory() != null) {
            artistCounts = userData.getPlayHistory().stream()
                .filter(ph -> ph.getSong() != null && ph.getSong().getArtist() != null)
                .collect(Collectors.groupingBy(
                    ph -> ph.getSong().getArtist().getName(),
                    Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        }
            
        List<String> topArtists = artistCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        preferences.put("favorite_artists", new ArrayList<>(topArtists.stream().map(a -> (Object) a).collect(Collectors.toList())));
        LOGGER.debug("Found {} top artists", topArtists.size());
        
        // Calculate favorite decades
        Map<String, Integer> decadeCounts = calculateDecadeCounts(userData);
        List<String> topDecades = decadeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        preferences.put("favorite_decades", new ArrayList<>(topDecades.stream().map(d -> (Object) d).collect(Collectors.toList())));
        
        return preferences;
    }
    
    private Map<String, Integer> calculateDecadeCounts(UserMusicData userData) {
        Map<String, Integer> decadeCounts = new HashMap<>();
        
        if (userData.getSongs() != null) {
            for (Song song : userData.getSongs()) {
                if (song.getReleaseDate() != null) {
                    int year = song.getReleaseDate().getYear() + 1900;
                    String decade = (year / 10) * 10 + "s";
                    decadeCounts.merge(decade, 1, Integer::sum);
                }
            }
        }
        
        return decadeCounts;
    }
}
