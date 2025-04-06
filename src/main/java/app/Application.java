package app;

import services.*;
import services.AppAPI.*;
import services.database.*;
import services.output.*;
import services.ui.*;
import services.enrichment.DataEnrichmentManager;
import models.*;
import utils.*;
import services.config.Config;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class that serves as the entry point to the Playlist Generator.
 * Handles all core functionality including data import, playlist generation,
 * database operations, and user interface management.
 */
public class Application {
    private Config config;
    private AuthenticationService authService;
    private MusicDatabaseManager dbManager;
    private AppSpotifyAPIManager spotifyManager;
    private MusicBrainzAPIManager musicBrainzManager;
    private LastFmAPIManager lastFmManager;
    private DataEnrichmentManager enrichmentManager;
    private PlaylistGenerator playlistGenerator;
    private GenreManager genreManager;
    private User currentUser;
    private boolean isOnline;
    private Playlist generatedPlaylist;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public Application() {
        config = Config.getInstance();
        authService = new AuthenticationService();
        genreManager = new GenreManager();
        playlistGenerator = new PlaylistGenerator();
    }
    
    public static void main(String[] args) {
        Application app = new Application();
        app.start();
    }
    
    public void start() {
        // Check internet connectivity
        this.isOnline = checkInternetConnectivity();
        
        if (config.isFirstBoot()) {
            LOGGER.info("First time startup detected, launching in CLI mode");
            config.set("interface", "cli");
            runSetupWizard();
            // Note: Setup wizard will automatically call setFirstBootCompleted()
            launchCli();
        } else {
            LOGGER.info("Normal startup, using saved config");
            String interfaceType = config.getInterface();
            if ("cli".equals(interfaceType)) {
                launchCli();
            } else {
                launchGui();
            }
        }
    }
    
    private boolean checkInternetConnectivity() {
        try {
            // Try to connect to Spotify API
            String token = SpotifyAccessToken.getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            LOGGER.info("No internet connection detected. Running in offline mode.");
            return false;
        }
    }
    
    private void launchCli() {
        // Check for saved login
        if (!authService.hasValidSession()) {
            currentUser = authService.promptLoginOrSignup();
            if (currentUser == null) {
                LOGGER.error("Authentication failed");
                System.exit(1);
            }
        } else {
            currentUser = authService.getUser();
        }
    
        // Save user ID in config
        config.set("defaultUserId", currentUser.getId());
        
        // Initialize services
        initializeServices();
        
        // Start interactive CLI
        CliController cli = new CliController(this);
        cli.start();
    }
    
    private void initializeServices() {
        // Initialize managers with current user
        this.dbManager = new MusicDatabaseManager(isOnline, currentUser);
        this.spotifyManager = new AppSpotifyAPIManager();
        this.musicBrainzManager = new MusicBrainzAPIManager();
        this.lastFmManager = new LastFmAPIManager();
        this.enrichmentManager = new DataEnrichmentManager(isOnline, spotifyManager, musicBrainzManager, lastFmManager);
    }

    private void runSetupWizard() {
        LOGGER.info("Starting setup wizard...");
        SetupWizard wizard = new SetupWizard();
        wizard.run(config);
        // Ensure firstBoot is set to false after setup
        config.setFirstBootCompleted();
        LOGGER.info("Setup wizard completed");
    }
    
    private void launchGui() {
        GuiLauncher launcher = new GuiLauncher();
        launcher.start(config);
    }

    /**
     * Comprehensive user data import workflow:
     * 1. Save basic song and artist data
     * 2. Enrich data with external APIs if online
     * 3. Save listening history
     * 4. Calculate and save user preferences
     *
     * @param userData The user music data to import
     * @return true if import was successful, false otherwise
     */
    public boolean importUserData(UserMusicData userData) {
        LOGGER.info("Starting data import for user: {}", currentUser.getName());
        
        try {
            // Validate input data
            if (userData == null || userData.isEmpty()) {
                LOGGER.warn("Empty or null user data provided");
                return false;
            }
            
            LOGGER.info("Processing {} songs, {} artists, {} history entries", 
                userData.getSongs().size(),
                userData.getArtists().size(),
                userData.getPlayHistory().size());

            // Process genres before saving
            genreManager.normalizeGenres(userData);

            // Step 1: Save songs and artists to database
            dbManager.saveUserData(userData);
            LOGGER.info("Saved basic data: {} songs, {} artists", 
                userData.getSongs().size(),
                userData.getArtists().size());

            // Step 2: Enrich with external data (only if online)
            if (isOnline) {
                enrichmentManager.enrichUserData(userData);
                
                // Save the enriched data
                dbManager.saveUserData(userData);
                LOGGER.info("Saved enriched data to database");
            }

            // Step 3: Save listening history
            dbManager.savePlayHistory(userData);
            LOGGER.info("Saved {} listening history entries", userData.getPlayHistory().size());

            return true;
        } catch (Exception e) {
            LOGGER.error("Error during data import: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Generates a playlist based on specified parameters
     * 
     * @param params Parameters for playlist generation
     * @return true if generation was successful, false otherwise
     */
    public boolean generatePlaylist(PlaylistParameters params) {
        try {
            LOGGER.info("Generating playlist with parameters: {}", params.getName());
            Map<String, List<Object>> userPreferences = dbManager.getCurrentUserPreferences();
            PlaylistPreferences playlistPreferences = new PlaylistPreferences();
            generatedPlaylist = playlistGenerator.generatePlaylist(params, userPreferences);
            return generatedPlaylist != null;
        } catch (Exception e) {
            LOGGER.error("Error generating playlist: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Exports the most recently generated playlist
     * 
     * @param format Export format ("csv", "json", "spotify", etc.)
     * @param destination Output destination (file path or URL)
     * @return true if export was successful, false otherwise
     */
    public boolean exportPlaylist(String format, String destination) {
        try {
            if (generatedPlaylist == null) {
                LOGGER.error("No playlist available to export.");
                return false;
            }

            PlaylistExporterFactory factory = new PlaylistExporterFactory();
            PlaylistExporter exporter = factory.getExporter(format);
            exporter.export(generatedPlaylist, destination);

            LOGGER.info("Playlist exported successfully to {} in {} format.", destination, format);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error exporting playlist: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the currently generated playlist
     */
    public Playlist getGeneratedPlaylist() {
        return generatedPlaylist;
    }
    
    /**
     * Gets the current user
     */
    public User getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Clean up resources when application is shutting down
     */
    public void shutdown() {
        LOGGER.info("Shutting down application");
        if (enrichmentManager != null) {
            enrichmentManager.shutdown();
        }
        if (dbManager != null) {
            dbManager.cleanup();
        }
    }
}

