import services.*;
import services.api.*;
import services.config.*;
import services.database.*;
import services.datasources.*;
import services.output.*;
import services.ui.*;
import models.*;
import interfaces.*;
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
            userId = config.getDefaultUserId();
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
            DataImportService importService = new DataImportService();
            UserMusicData importedData = importService.importFromFile(filePath);
            
            if (importedData != null && !importedData.isEmpty()) {
                System.out.println("Successfully imported data from " + filePath);
                System.out.println("Songs: " + importedData.getSongs().size());
                System.out.println("Artists: " + importedData.getArtists().size());
                
                // Store imported data
                app.saveUserData(importedData);
            }
        } catch (Exception e) {
            System.err.println("Error importing data: " + e.getMessage());
        }
    }
    
    private static void generatePlaylist(PlaylistGeneratorApp app) {
        PlaylistParameters params = new PlaylistParameters();
        params.setName("Quick Playlist");
        params.setSongCount(20);
        
        // Use default parameters
        app.generatePlaylist(params);
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
}
