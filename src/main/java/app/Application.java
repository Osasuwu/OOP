package app;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.Playlist;
import models.PlaylistParameters;
import models.PlaylistPreferences;
import models.User;
import models.UserMusicData;
import services.AppAPI.AppSpotifyAPIManager;
import services.AppAPI.LastFmAPIManager;
import services.AppAPI.MusicBrainzAPIManager;
import services.AuthenticationService;
import services.PlaylistGenerator;
import services.config.Config;
import services.database.MusicDatabaseManager;
import services.enrichment.DataEnrichmentManager;
import services.ui.CliController;
import services.ui.GuiLauncher;
import services.ui.SetupWizard;
import utils.GenreManager;
import utils.SpotifyAccessToken;

/**
 * Main application class that serves as the entry point to the Playlist Generator.
 * Handles all core functionality including data import, playlist generation,
 * database operations, and user interface management.
 */
public class Application  {
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
    
    public boolean generatePlaylist(PlaylistParameters params) {
        try {
            LOGGER.info("Generating playlist with parameters: {}", params.getName());
    
            // Retrieve the user preferences map (Map<String, List<Object>>)
            Map<String, java.util.List<Object>> userPreferencesMap = dbManager.getCurrentUserPreferences();
    
            // Use the preferences map directly in PlaylistPreferences
            // NOTE: Ensure that the PlaylistPreferences constructor accepts a Map<String, List<Object>>
            PlaylistPreferences playlistPreferences = new PlaylistPreferences(userPreferencesMap);
    
            // Retrieve user music data
            UserMusicData userData = dbManager.loadUserData();
            if (userData == null) {
                LOGGER.error("Failed to retrieve user music data.");
                return false;
            }
    
            // Generate the playlist
            generatedPlaylist = playlistGenerator.generatePlaylist(userData, params, playlistPreferences);
    
            if (generatedPlaylist != null) {
                LOGGER.info("Playlist generated successfully.");
                return true;
            } else {
                LOGGER.warn("Playlist generation returned null.");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Error generating playlist: {}", e.getMessage(), e);
        }
        return isOnline;
    }
}