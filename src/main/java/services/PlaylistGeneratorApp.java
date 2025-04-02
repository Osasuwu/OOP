package services;

// Imports remain as you provided
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.Playlist;
import models.UserMusicData;
import services.api.LastFmAPIManager;
import services.api.MusicBrainzAPIManager;
import services.api.SpotifyAPIManager;
import services.database.MusicDatabaseManager;
import services.database.OfflineDataManager;
import services.enrichment.DataEnrichmentService;
import services.ui.UserInterface;
import utils.GenreMapper;
import utils.SpotifyAccessToken;

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
    private Playlist generatedPlaylist; // This field stores the most recently generated playlist
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

    // Add the method to retrieve the most recently generated playlist
    public Playlist getGeneratedPlaylist() {
        return generatedPlaylist;
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
     * The rest of the existing methods, such as importUserData, calculateUserPreferences, etc., remain unchanged.
     */
}
        