import services.*;
import services.api.*;
import services.config.ApplicationConfig;
import services.database.*;
import services.output.*;
import services.ui.*;
import services.importer.*;
import models.*;
import utils.*;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;


/**
 * Main application class that serves as the entry point to the Playlist Generator.
 * Supports both CLI and GUI modes, handles command-line arguments, and initializes
 * the appropriate components based on the execution context.
 */
public class Application {
    private static final String CONFIG_FILE = "config.json";
    private static ApplicationConfig config;
    
    public static void main(String[] args) {
        // Parse command-line arguments
        CommandLineOptions options = parseArguments(args);
        
        // Load or create configuration
        config = loadConfig();
        
        // Check for first-run setup
        if (config.isFirstRun() || options.isSetupMode()) {
            runSetupWizard();
        }
        
        // Determine mode (CLI or GUI)
        if (options.isGuiMode()) {
            launchGui();
        } else {
            launchCli(options);
        }
    }
    
    private static CommandLineOptions parseArguments(String[] args) {
        CommandLineOptions options = new CommandLineOptions();
        
        // Simple argument parsing (can be replaced with a proper CLI library)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--gui":
                    options.setGuiMode(true);
                    break;
                case "--setup":
                    options.setSetupMode(true);
                    break;
                case "--import":
                    if (i + 1 < args.length) {
                        options.setImportFile(args[++i]);
                    }
                    break;
                case "--user":
                    if (i + 1 < args.length) {
                        options.setUserId(args[++i]);
                    }
                    break;
                case "--generate":
                    options.setGeneratePlaylist(true);
                    break;
                case "--help":
                    displayHelp();
                    System.exit(0);
                    break;
            }
        }
        
        return options;
    }
    
    private static ApplicationConfig loadConfig() {
        Path configPath = Paths.get(CONFIG_FILE);
        ApplicationConfig config = new ApplicationConfig();
        
        if (Files.exists(configPath)) {
            try {
                String content = new String(Files.readAllBytes(configPath));
                config = ApplicationConfig.fromJson(content);
            } catch (IOException e) {
                System.err.println("Error reading configuration file: " + e.getMessage());
                System.err.println("Using default configuration.");
            }
        } else {
            // Create default configuration
            config.setFirstRun(true);
            saveConfig(config);
        }
        
        return config;
    }
    
    private static void saveConfig(ApplicationConfig config) {
        try {
            Files.write(Paths.get(CONFIG_FILE), config.toJson().getBytes());
        } catch (IOException e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }
    
    private static void runSetupWizard() {
        SetupWizard wizard = new SetupWizard();
        ApplicationConfig newConfig = wizard.run(config);
        
        if (newConfig != null) {
            config = newConfig;
            config.setFirstRun(false);
            saveConfig(config);
        }
    }
    
    private static void launchGui() {
        GuiLauncher launcher = new GuiLauncher();
        launcher.start(config);
    }
    
    private static void launchCli(CommandLineOptions options) {
        // Set default user ID if not specified
        String userId = options.getUserId();
        
        if (userId == null || userId.isEmpty()) {
            System.out.println("No user ID provided. Please log in or sign up.");
            AuthenticationService authService = new AuthenticationService();
            userId = authService.promptLoginOrSignup();
            
            if (userId != null && !userId.isEmpty()) {
                // Save the user ID in config for future use
                config.setDefaultUserId(userId);
                saveConfig(config);
            } else {
                System.err.println("Authentication failed. Exiting application.");
                System.exit(1);
            }
        }
        
        // Create playlist generator app
        PlaylistGeneratorApp app = new PlaylistGeneratorApp(userId);
        
        // Handle import if requested
        if (options.getImportFile() != null) {
            importUserData(app, options.getImportFile());
        }
        
        // Generate playlist if requested
        if (options.isGeneratePlaylist()) {
            generatePlaylist(app);
        }
        
        // Otherwise, start interactive CLI
        CliController cli = new CliController(app);
        cli.start();
    }
    
    private static void importUserData(PlaylistGeneratorApp app, String filePath) {
        try {
            Path path = Paths.get(filePath);
            DataImportFactory importFactory = new DataImportFactory();
            DataImportAdapter importAdapter;
            
            try {
                // Get appropriate adapter based on file extension
                importAdapter = importFactory.getAdapter(path);
            } catch (ImportException e) {
                // Fallback to CSV adapter if no specific adapter is found
                importAdapter = new CsvDataImportAdapter();
                if (!importAdapter.canHandle(path)) {
                    System.err.println("Unsupported file format for: " + filePath);
                    return;
                }
            }
            
            UserMusicData importedData = importAdapter.importFromFile(path);
            
            if (importedData != null && !importedData.isEmpty()) {
                System.out.println("Successfully imported data from " + filePath);
                System.out.println("Songs: " + importedData.getSongs().size());
                System.out.println("Artists: " + importedData.getArtists().size());
                System.out.println("Play history entries: " + importedData.getPlayHistory().size());
                
                // Normalize genres using GenreMapper
                normalizeGenres(importedData);
                
                // Store imported data
                app.importUserData(importedData);
            } else {
                System.err.println("No usable data found in file: " + filePath);
            }
        } catch (ImportException e) {
            System.err.println("Error importing data: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during import: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Add the generatePlaylist method
    private static void generatePlaylist(PlaylistGeneratorApp app) {
        try {
            PlaylistParameters params = new PlaylistParameters();

            // Get playlist name
            System.out.print("Enter playlist name: ");
            Scanner scanner = new Scanner(System.in);
            params.setName(scanner.nextLine().trim());

            // Get number of songs
            System.out.print("How many songs (10-100)? ");
            int songCount = Integer.parseInt(scanner.nextLine().trim());
            params.setSongCount(songCount);

            // Get generation strategy
            System.out.println("Select generation strategy:");
            System.out.println("1. Random");
            System.out.println("2. Popular");
            System.out.println("3. Diverse");
            System.out.println("4. Balanced (default)");
            System.out.print("Enter choice: ");
            int strategyChoice = Integer.parseInt(scanner.nextLine().trim());
            switch (strategyChoice) {
                case 1:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.RANDOM);
                    break;
                case 2:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.POPULAR);
                    break;
                case 3:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.DIVERSE);
                    break;
                default:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.BALANCED);
            }

            // Generate the playlist
            Playlist playlist = app.generatePlaylist(params);
            System.out.println("Generated Playlist: " + playlist.getName());
            System.out.println("Songs in Playlist:");
            playlist.getSongs().forEach(song -> System.out.println("- " + song.getTitle()));
        } catch (Exception e) {
            System.err.println("Error generating playlist: " + e.getMessage());
        }
    }
}
    
    private static void normalizeGenres(UserMusicData data) {
        GenreMapper genreMapper = new GenreMapper();
        
        // Process songs with genres
        for (Song song : data.getSongs()) {
            if (song.getGenres() != null && !song.getGenres().isEmpty()) {
                Set<String> genres = new HashSet<>(song.getGenres());
                song.setGenres(new ArrayList<>(genreMapper.normalizeGenres(genres)));
            }
        }
        
        // Process user's favorite genres
        if (data.getFavoriteGenres() != null && !data.getFavoriteGenres().isEmpty()) {
            Set<String> genres = new HashSet<>(data.getFavoriteGenres());
            data.setFavoriteGenres(new ArrayList<>(genreMapper.normalizeGenres(genres)));
        }
    }
    private static void generatePlaylist(PlaylistGeneratorApp app) {
        PlaylistParameters params = new PlaylistParameters();
        params.setName("Quick Playlist");
        params.setSongCount(20);
        
        // If in import mode, prefer imported music
        params.setPreferImportedMusic(true);
        
        // Use default parameters
    }
    private static void displayHelp() {
        System.out.println("Playlist Generator - Help");
        System.out.println("-------------------------");
        System.out.println("Available options:");
        System.out.println("  --gui                 Start in graphical mode");
        System.out.println("  --setup               Run setup wizard");
        System.out.println("  --import <file>       Import data from file");
        System.out.println("  --user <id>           Specify user ID");
        System.out.println("  --generate            Generate a quick playlist");
        System.out.println("  --help                Display this help message");
    }
    
    // Helper class for command line options
    private static class CommandLineOptions {
        private boolean guiMode = false;
        private boolean setupMode = false;
        private boolean generatePlaylist = false;
        private String importFile = null;
        private String userId = null;
        
        // Getters and setters
        public boolean isGuiMode() { return guiMode; }
        public void setGuiMode(boolean guiMode) { this.guiMode = guiMode; }
        
        public boolean isSetupMode() { return setupMode; }
        public void setSetupMode(boolean setupMode) { this.setupMode = setupMode; }
        
        public boolean isGeneratePlaylist() { return generatePlaylist; }
        public void setGeneratePlaylist(boolean generatePlaylist) { this.generatePlaylist = generatePlaylist; }
        
        public String getImportFile() { return importFile; }
        public void setImportFile(String importFile) { this.importFile = importFile; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    private static void exportPlaylist(PlaylistGeneratorApp app, String format, String destination) {
        try {
            Playlist playlist = app.getGeneratedPlaylist();
            if (playlist == null) {
                System.err.println("No playlist available to export.");
                return;
            }
            PlaylistExporterFactory factory = new PlaylistExporterFactory();
            PlaylistExporter exporter = factory.getExporter(format);
            exporter.export(playlist, destination);

            System.out.println("Playlist exported successfully to " + destination + " in " + format + " format.");
        } catch (Exception e) {
            System.err.println("Error exporting playlist: " + e.getMessage());
            e.printStackTrace();
        }
    }