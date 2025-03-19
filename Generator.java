package services.generator;

import services.*;
import services.api.*;
import services.database.*;
import services.datasources.*;
import services.offline.*;
import services.output.*;
import services.ui.*;
import utils.*;
import java.util.*;

/**
 * Main class that orchestrates the playlist generation process.
 */
public class Generator {
    private final SongSelector songSelector;
    private final FilterManager filterManager;
    private final RecommendationEngine recommendationEngine;
    private final OfflineSyncManager offlineSyncManager;
    private final PlaylistManager playlistManager;
    private final UserPreferenceManager userPreferenceManager;
    private final DataFetcher dataFetcher;
    private final GenreMapper genreMapper;
    private final Logger logger;
    private boolean isOnline;

    /**
     * Constructor to initialize the generator.
     */
    public Generator() {
        this.songSelector = new SongSelector();
        this.filterManager = new FilterManager();
        this.recommendationEngine = new RecommendationEngine();
        this.offlineSyncManager = new OfflineSyncManager();
        this.playlistManager = new PlaylistManager();
        this.userPreferenceManager = new UserPreferenceManager();
        this.dataFetcher = new DataFetcher();
        this.genreMapper = new GenreMapper();
        this.logger = new Logger();
        
        // Check internet connectivity
        this.isOnline = checkInternetConnectivity();
    }

    /**
     * Checks if the application is online.
     */
    private boolean checkInternetConnectivity() {
        try {
            String token = SpotifyAccessToken.getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            logger.logError("No internet connection detected. Running in offline mode.");
            return false;
        }
    }

    /**
     * Generates a playlist based on user preferences.
     */
    public void generatePlaylist(String playlistName, int songCount) {
        logger.logInfo("Starting playlist generation: " + playlistName);

        Map<String, Object> userPrefs = userPreferenceManager.getUserPreferences();
        List<Map<String, Object>> recommendedSongs = recommendationEngine.getRecommendedSongs(userPrefs, songCount);

        // Apply filters
        List<Map<String, Object>> filteredSongs = filterManager.applyFilters(recommendedSongs, userPrefs);

        if (filteredSongs.size() >= songCount) {
            playlistManager.createPlaylist(playlistName, filteredSongs);
            logger.logInfo("Playlist '" + playlistName + "' successfully created.");
        } else {
            logger.logWarning("Not enough songs available. Trying similar artist recommendations.");
            List<Map<String, Object>> additionalSongs = songSelector.getSongsFromSimilarArtists(userPrefs, songCount - filteredSongs.size());
            filteredSongs.addAll(additionalSongs);

            if (filteredSongs.size() >= songCount) {
                playlistManager.createPlaylist(playlistName, filteredSongs);
                logger.logInfo("Playlist '" + playlistName + "' successfully created with additional recommendations.");
            } else {
                logger.logError("Not enough songs available. Please try again later.");
            }
        }
    }
}
package services.generator;

import java.util.*;

public class SongSelector {
    
    public List<Map<String, Object>> getSongsFromSimilarArtists(Map<String, Object> userPrefs, int count) {
        List<Map<String, Object>> songs = new ArrayList<>();
        Set<String> favoriteArtists = new HashSet<>();

        if (userPrefs.containsKey("favorite_artists")) {
            Object[] artists = (Object[]) userPrefs.get("favorite_artists");
            favoriteArtists.addAll(Arrays.asList(Arrays.copyOf(artists, artists.length, String[].class)));
        }

        for (String artist : favoriteArtists) {
            if (songs.size() >= count) break;
            List<Map<String, Object>> similarArtists = DataFetcher.getSimilarArtists(artist, 3);
            
            for (Map<String, Object> similarArtist : similarArtists) {
                if (songs.size() >= count) break;
                List<Map<String, Object>> artistSongs = DataFetcher.getArtistSongs((String) similarArtist.get("name"), count - songs.size());
                songs.addAll(artistSongs);
            }
        }

        return songs;
    }
}
package services.generator;

import java.util.*;

public class FilterManager {
    
    public List<Map<String, Object>> applyFilters(List<Map<String, Object>> songs, Map<String, Object> userPrefs) {
        List<Map<String, Object>> filteredSongs = new ArrayList<>();

        for (Map<String, Object> song : songs) {
            if (matchesPreferences(song, userPrefs)) {
                filteredSongs.add(song);
            }
        }

        return filteredSongs;
    }

    private boolean matchesPreferences(Map<String, Object> song, Map<String, Object> userPrefs) {
        if (userPrefs.containsKey("preferred_energy")) {
            double preferredEnergy = (double) userPrefs.get("preferred_energy");
            double songEnergy = (double) song.get("energy");

            if (Math.abs(preferredEnergy - songEnergy) > 0.2) {
                return false;
            }
        }

        if (userPrefs.containsKey("favorite_genres")) {
            String[] favoriteGenres = (String[]) userPrefs.get("favorite_genres");
            String songGenre = (String) song.get("genre");

            if (!Arrays.asList(favoriteGenres).contains(songGenre)) {
                return false;
            }
        }

        return true;
    }
}
package services.generator;

import java.util.*;

public class RecommendationEngine {

    private final DataFetcher dataFetcher;

    public RecommendationEngine() {
        this.dataFetcher = new DataFetcher();
    }

    /**
     * Retrieves recommended songs based on user preferences.
     */
    public List<Map<String, Object>> getRecommendedSongs(Map<String, Object> userPrefs, int count) {
        List<Map<String, Object>> recommendedSongs = new ArrayList<>();

        if (userPrefs.containsKey("favorite_genres")) {
            String[] favoriteGenres = (String[]) userPrefs.get("favorite_genres");

            for (String genre : favoriteGenres) {
                if (recommendedSongs.size() >= count) break;
                List<Map<String, Object>> genreSongs = dataFetcher.fetchSongsByGenre(genre, count - recommendedSongs.size());
                recommendedSongs.addAll(genreSongs);
            }
        }

        if (recommendedSongs.size() < count) {
            List<Map<String, Object>> trendingSongs = dataFetcher.fetchTrendingSongs(count - recommendedSongs.size());
            recommendedSongs.addAll(trendingSongs);
        }

        return recommendedSongs;
    }
}
package services.offline;

import java.io.*;
import java.util.*;
import utils.Logger;

public class OfflineSyncManager {

    private final Logger logger;

    public OfflineSyncManager() {
        this.logger = new Logger();
    }

    /**
     * Saves a playlist for offline usage.
     */
    public void savePlaylistOffline(String playlistName, List<Map<String, Object>> songs) {
        try {
            File file = new File("offline_playlists/" + playlistName + ".txt");
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);

            for (Map<String, Object> song : songs) {
                writer.write(song.get("title") + " - " + song.get("artist") + "\n");
            }

            writer.close();
            logger.logInfo("Playlist saved for offline use: " + playlistName);
        } catch (IOException e) {
            logger.logError("Error saving playlist offline: " + e.getMessage());
        }
    }
}
package services.playlist;

import java.util.*;
import utils.Logger;

public class PlaylistManager {

    private final Logger logger;
    private final Map<String, List<Map<String, Object>>> playlists;

    public PlaylistManager() {
        this.logger = new Logger();
        this.playlists = new HashMap<>();
    }

    /**
     * Creates a new playlist.
     */
    public void createPlaylist(String name, List<Map<String, Object>> songs) {
        if (playlists.containsKey(name)) {
            logger.logWarning("Playlist with name '" + name + "' already exists.");
        } else {
            playlists.put(name, songs);
            logger.logInfo("Playlist '" + name + "' created successfully.");
        }
    }

    /**
     * Deletes an existing playlist.
     */
    public void deletePlaylist(String name) {
        if (playlists.containsKey(name)) {
            playlists.remove(name);
            logger.logInfo("Playlist '" + name + "' deleted successfully.");
        } else {
            logger.logWarning("Playlist '" + name + "' does not exist.");
        }
    }

    /**
     * Retrieves all playlists.
     */
    public Map<String, List<Map<String, Object>>> getAllPlaylists() {
        return playlists;
    }
}
package services.user;

import java.util.*;
import utils.Logger;

public class UserPreferenceManager {

    private final Map<String, Object> userPreferences;
    private final Logger logger;

    public UserPreferenceManager() {
        this.userPreferences = new HashMap<>();
        this.logger = new Logger();
    }

    /**
     * Loads user preferences from the database or a local file.
     */
    public void loadPreferences(Map<String, Object> preferences) {
        userPreferences.clear();
        userPreferences.putAll(preferences);
        logger.logInfo("User preferences loaded successfully.");
    }

    /**
     * Updates a user preference.
     */
    public void updatePreference(String key, Object value) {
        userPreferences.put(key, value);
        logger.logInfo("Preference updated: " + key + " = " + value);
    }

    /**
     * Retrieves the full set of user preferences.
     */
    public Map<String, Object> getPreferences() {
        return userPreferences;
    }
}
package services.data;

import java.util.*;

public class DataFetcher {

    public DataFetcher() {}

    /**
     * Fetches songs based on genre.
     */
    public List<Map<String, Object>> fetchSongsByGenre(String genre, int count) {
        // Simulating a data fetch from an API or database
        List<Map<String, Object>> songs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> song = new HashMap<>();
            song.put("title", genre + " Song " + i);
            song.put("artist", "Artist " + i);
            songs.add(song);
        }
        return songs;
    }

    /**
     * Fetches trending songs.
     */
    public List<Map<String, Object>> fetchTrendingSongs(int count) {
        List<Map<String, Object>> songs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> song = new HashMap<>();
            song.put("title", "Trending Song " + i);
            song.put("artist", "Trending Artist " + i);
            songs.add(song);
        }
        return songs;
    }
}
package services.utils;

import java.util.*;

public class GenreMapper {

    private final Map<String, List<String>> genreMapping;

    public GenreMapper() {
        genreMapping = new HashMap<>();
        initializeMappings();
    }

    /**
     * Defines relationships between genres.
     */
    private void initializeMappings() {
        genreMapping.put("rock", Arrays.asList("alternative rock", "indie rock", "classic rock"));
        genreMapping.put("pop", Arrays.asList("synthpop", "dance pop", "electropop"));
        genreMapping.put("jazz", Arrays.asList("smooth jazz", "swing", "bebop"));
        genreMapping.put("hip-hop", Arrays.asList("trap", "old school", "conscious rap"));
    }

    /**
     * Retrieves similar genres.
     */
    public List<String> getRelatedGenres(String genre) {
        return genreMapping.getOrDefault(genre, Collections.singletonList(genre));
    }
}
package utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void logInfo(String message) {
        System.out.println("INFO [" + getCurrentTime() + "]: " + message);
    }

    public void logWarning(String message) {
        System.out.println("WARNING [" + getCurrentTime() + "]: " + message);
    }

    public void logError(String message) {
        System.err.println("ERROR [" + getCurrentTime() + "]: " + message);
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }
}
