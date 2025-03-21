package services;

import services.api.*;
import services.database.*;
import services.output.*;
import services.ui.*;
import models.*;
import utils.*;
import services.enrichment.DataEnrichmentService;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application service that coordinates all playlist generation functionality.
 * Handles data import, enrichment, storage, and playlist generation.
 * 
 * Features:
 * - Online/offline mode support
 * - Multi-service data enrichment
 * - Transaction-safe data persistence
 * - Automatic genre mapping
 */
public class PlaylistGeneratorApp {
    private final MusicDatabaseManager dbManager;
    private final OfflineDataManager offlineManager;
    private final SpotifyAPIManager spotifyManager;
    private final MusicBrainzAPIManager musicBrainzManager;
    private final LastFmAPIManager lastFmManager;
    private final GenreMapper genreMapper;
    private final String userId;
    private final UserInterface userInterface;
    private final PlaylistGenerator playlistGenerator;
    private boolean isOnline;
    private DataEnrichmentService enrichmentService;
    private static final Logger logger = LoggerFactory.getLogger(PlaylistGeneratorApp.class);

    public PlaylistGeneratorApp(String userId) {
        this.userId = userId;
        this.genreMapper = new GenreMapper();
        this.playlistGenerator = new PlaylistGenerator();
        
        // Check internet connectivity
        this.isOnline = checkInternetConnectivity();
        
        // Initialize managers
        this.dbManager = new MusicDatabaseManager(isOnline, userId);
        this.offlineManager = new OfflineDataManager(userId);
        this.spotifyManager = new SpotifyAPIManager();
        this.musicBrainzManager = new MusicBrainzAPIManager();
        this.lastFmManager = new LastFmAPIManager();
        this.userInterface = new UserInterface();
        this.enrichmentService = new DataEnrichmentService();
    }

    private boolean checkInternetConnectivity() {
        try {
            // Try to connect to Spotify API
            String token = SpotifyAccessToken.getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            System.out.println("No internet connection detected. Running in offline mode.");
            return false;
        }
    }

    public void generatePlaylist(PlaylistParameters params) {
        
    }

    public void saveUserData(UserMusicData userData) {
        try {
            dbManager.saveUserData(userData);
            System.out.println("User data saved successfully!");
        } catch (Exception e) {
            System.err.println("Error saving user data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (enrichmentService != null) {
            enrichmentService.shutdown();
        }
        if (dbManager != null) {
            dbManager.cleanup(); // Add cleanup call
        }
    }

    /**
     * Imports user music data with a defined processing flow:
     * 1. Save basic song and artist data
     * 2. Enrich data with external APIs
     * 3. Save enriched data
     * 4. Save listening history
     * 5. Calculate and save user preferences
     *
     * @param rawData The raw user music data to import
     */
    public void importUserData(UserMusicData rawData) {
        logger.info("Starting data import for user: {}", userId);
        
        try {
            // Validate input data
            if (rawData == null || rawData.isEmpty()) {
                logger.warn("Empty or null user data provided");
                return;
            }
            
            logger.info("Processing {} songs, {} artists, {} history entries", 
                rawData.getSongs().size(),
                rawData.getArtists().size(),
                rawData.getPlayHistory().size());

            // Step 1: Add missing songs and artists
            UserMusicData savedBasicData = dbManager.saveSongsAndArtists(rawData);
            logger.info("Saved basic data: {} songs, {} artists", 
                savedBasicData.getSongs().size(),
                savedBasicData.getArtists().size());

            // Step 2: Enrich with external data
            UserMusicData enrichedData = enrichmentService.enrichUserData(savedBasicData);
            logger.debug("Enriched data: {} songs with genres, {} artists with details",
                enrichedData.getSongs().stream().filter(s -> !s.getGenres().isEmpty()).count(),
                enrichedData.getArtists().stream().filter(a -> !a.getGenres().isEmpty()).count());

            // Step 3: Save enriched data
            dbManager.saveEnrichedData(enrichedData);
            logger.info("Saved enriched data to database");

            // Step 4: Save listening history
            dbManager.saveListenHistory(rawData.getPlayHistory());
            logger.info("Saved {} listening history entries", rawData.getPlayHistory().size());

            // Step 5: Calculate and save preferences
            Map<String, Object> preferences = calculateUserPreferences(rawData);
            dbManager.updateUserPreferences(preferences);
            logger.info("Updated user preferences with {} top genres and {} top artists",
                ((List<?>)preferences.get("favorite_genres")).size(),
                ((List<?>)preferences.get("favorite_artists")).size());

        } catch (Exception e) {
            logger.error("Error during data import: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import user data", e);
        }
    }

    private Map<String, Object> calculateUserPreferences(UserMusicData userData) {
        Map<String, Object> preferences = new HashMap<>();
        
        // Calculate genre preferences
        Map<String, Integer> genreCounts = new HashMap<>();
        for (Song song : userData.getSongs()) {
            for (String genre : song.getGenres()) {
                String normalizedGenre = GenreMapper.normalizeGenre(genre);
                genreCounts.merge(normalizedGenre, 1, Integer::sum);
            }
        }
        
        // Get top genres
        List<String> topGenres = genreCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        preferences.put("favorite_genres", topGenres);
        
        // Calculate artist preferences
        Map<String, Integer> artistCounts = userData.getPlayHistory().stream()
            .collect(Collectors.groupingBy(
                ph -> ph.getSong().getArtistName(),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
            
        List<String> topArtists = artistCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        preferences.put("favorite_artists", topArtists);
        
        return preferences;
    }
}