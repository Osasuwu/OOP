package services.ui;

import services.config.ApplicationConfig;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Arrays;
import java.util.List;

/**
 * A wizard to guide users through initial application setup
 */
public class SetupWizard {
    private final Scanner scanner;
    
    public SetupWizard() {
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * Run the setup wizard
     * @param currentConfig The current application configuration
     * @return Updated configuration or null if setup was cancelled
     */
    public ApplicationConfig run(ApplicationConfig currentConfig) {
        System.out.println("\n====================================");
        System.out.println("   Playlist Generator Setup Wizard   ");
        System.out.println("====================================\n");
        
        if (currentConfig.isFirstRun()) {
            System.out.println("Welcome to the Playlist Generator!");
            System.out.println("Let's set up your application for first use.");
        } else {
            System.out.println("Welcome to the setup wizard.");
            System.out.println("You can update your configuration here.");
        }
        
        System.out.print("\nDo you want to continue with setup? (Y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        
        if (response.startsWith("n")) {
            System.out.println("Setup cancelled. Using existing configuration.");
            return null;
        }
        
        // Create a copy of the current config to modify
        ApplicationConfig newConfig = new ApplicationConfig();
        
        // Username setup
        System.out.println("\n-- User Identification --");
        System.out.print("Please enter your username (default: " + 
                        (currentConfig.getDefaultUserId().equals("default") ? "default" : currentConfig.getDefaultUserId()) + 
                        "): ");
        String username = scanner.nextLine().trim();
        
        if (!username.isEmpty()) {
            newConfig.setDefaultUserId(username);
        } else {
            newConfig.setDefaultUserId(currentConfig.getDefaultUserId());
        }
        
        // Data directory setup
        System.out.println("\n-- Data Storage Location --");
        System.out.println("Where would you like to store application data?");
        System.out.print("Enter path (default: " + currentConfig.getDataDirectory() + "): ");
        String dataDir = scanner.nextLine().trim();
        
        if (!dataDir.isEmpty()) {
            Path dirPath = Paths.get(dataDir);
            try {
                Files.createDirectories(dirPath);
                newConfig.setDataDirectory(dataDir);
            } catch (Exception e) {
                System.out.println("Error creating directory: " + e.getMessage());
                System.out.println("Using default directory: " + currentConfig.getDataDirectory());
                newConfig.setDataDirectory(currentConfig.getDataDirectory());
            }
        } else {
            newConfig.setDataDirectory(currentConfig.getDataDirectory());
        }
        
        // Music import paths
        System.out.println("\n-- Music Import Directories --");
        System.out.println("Would you like to specify paths where your music files are located?");
        System.out.print("Add music directories? (Y/n): ");
        response = scanner.nextLine().trim().toLowerCase();
        
        if (!response.startsWith("n")) {
            boolean addingPaths = true;
            List<String> existingPaths = currentConfig.getImportPaths();
            
            if (!existingPaths.isEmpty()) {
                System.out.println("\nExisting music directories:");
                for (String path : existingPaths) {
                    System.out.println(" - " + path);
                }
                System.out.print("\nKeep existing paths? (Y/n): ");
                response = scanner.nextLine().trim().toLowerCase();
                
                if (!response.startsWith("n")) {
                    newConfig.setImportPaths(existingPaths);
                }
            }
            
            while (addingPaths) {
                System.out.print("\nEnter path to music directory (or leave empty to finish): ");
                String path = scanner.nextLine().trim();
                
                if (path.isEmpty()) {
                    addingPaths = false;
                } else {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        newConfig.addImportPath(path);
                        System.out.println("Directory added successfully!");
                    } else {
                        System.out.println("Invalid directory path. Please try again.");
                    }
                }
            }
        } else {
            // Keep existing import paths
            newConfig.setImportPaths(currentConfig.getImportPaths());
        }
        
        // API key setup
        System.out.println("\n-- External Services --");
        System.out.println("Would you like to configure integration with external music services?");
        System.out.println("This allows enriched functionality like artist information and recommendations.");
        System.out.print("Configure external services? (Y/n): ");
        response = scanner.nextLine().trim().toLowerCase();
        
        if (!response.startsWith("n")) {
            configureExternalService(newConfig, currentConfig, "spotify", "Spotify");
            configureExternalService(newConfig, currentConfig, "lastfm", "Last.fm");
            configureExternalService(newConfig, currentConfig, "musicbrainz", "MusicBrainz");
        } else {
            // Keep existing API keys
            newConfig.setApiKeys(currentConfig.getApiKeys());
        }
        
        // UI preferences
        System.out.println("\n-- UI Preferences --");
        System.out.println("Select your preferred theme:");
        System.out.println("1. Light (default)");
        System.out.println("2. Dark");
        System.out.println("3. System");
        System.out.print("Enter your choice (1-3): ");
        response = scanner.nextLine().trim();
        
        String theme = "light";
        switch (response) {
            case "2":
                theme = "dark";
                break;
            case "3":
                theme = "system";
                break;
        }
        newConfig.setUiPreference("theme", theme);
        
        // Offline mode
        System.out.println("\n-- Network Usage --");
        System.out.println("Would you like to start the application in offline mode by default?");
        System.out.print("Enable offline mode? (y/N): ");
        response = scanner.nextLine().trim().toLowerCase();
        newConfig.setOfflineMode(response.startsWith("y"));
        
        // Analytics
        System.out.println("\n-- Anonymous Statistics --");
        System.out.println("Would you like to help improve the application by sending anonymous usage statistics?");
        System.out.println("No personal data or music content will be transmitted.");
        System.out.print("Allow anonymous statistics? (Y/n): ");
        response = scanner.nextLine().trim().toLowerCase();
        newConfig.setCollectAnonymousStats(!response.startsWith("n"));
        
        // Completion
        System.out.println("\n====================================");
        System.out.println("       Setup Wizard Complete!       ");
        System.out.println("====================================");
        
        return newConfig;
    }
    
    private void configureExternalService(ApplicationConfig newConfig, ApplicationConfig currentConfig, 
                                         String serviceKey, String serviceName) {
        System.out.println("\nConfigure " + serviceName + " API integration:");
        String currentKey = currentConfig.getApiKey(serviceKey);
        
        if (!currentKey.isEmpty()) {
            System.out.println("Current API key: " + maskApiKey(currentKey));
            System.out.print("Keep existing key? (Y/n): ");
            String response = scanner.nextLine().trim().toLowerCase();
            
            if (!response.startsWith("n")) {
                newConfig.setApiKey(serviceKey, currentKey);
                return;
            }
        }
        
        System.out.print("Enter " + serviceName + " API key (leave empty to skip): ");
        String key = scanner.nextLine().trim();
        
        if (!key.isEmpty()) {
            newConfig.setApiKey(serviceKey, key);
            System.out.println(serviceName + " API key configured successfully!");
        } else {
            newConfig.setApiKey(serviceKey, "");
        }
    }
    
    private String maskApiKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        
        // Show first and last 4 chars, mask the rest
        if (key.length() <= 8) {
            return "*".repeat(key.length());
        } else {
            return key.substring(0, 4) + "*".repeat(key.length() - 8) + key.substring(key.length() - 4);
        }
    }
}
