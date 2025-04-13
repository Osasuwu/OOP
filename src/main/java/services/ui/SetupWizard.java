package services.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import services.config.Config;

/**
 * A wizard to guide users through initial application setup
 */
public class SetupWizard {
    private final Scanner scanner;

    public SetupWizard() {
        this.scanner = new Scanner(System.in);
    }

    public void run(Config config) {
        System.out.println("\n====================================");
        System.out.println("   Playlist Generator Setup Wizard   ");
        System.out.println("====================================\n");
        
        if (config.isFirstBoot()) {
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
            return;
        }

        // Username setup
        System.out.println("\n-- User Identification --");
        System.out.print("Please enter your username (default: " + 
                        config.get("defaultUsername") + "): ");
        String username = scanner.nextLine().trim();
        
        if (!username.isEmpty()) {
            config.set("defaultUserId", username);
        }
        
        // Data directory setup
        System.out.println("\n-- Data Storage Location --");
        System.out.println("Where would you like to store application data?");
        System.out.print("Enter path (default: " + config.get("dataDirectory") + "): ");
        String dataDir = scanner.nextLine().trim();
        
        if (!dataDir.isEmpty()) {
            Path dirPath = Paths.get(dataDir);
            try {
                Files.createDirectories(dirPath);
                config.set("dataDirectory", dataDir);
            } catch (Exception e) {
                System.out.println("Error creating directory: " + e.getMessage());
                System.out.println("Using default directory: " + config.get("dataDirectory"));
            }
        }

        // API Services setup
        configureApiServices(config);
        
        // Completion
        System.out.println("\n====================================");
        System.out.println("       Setup Wizard Complete!       ");
        System.out.println("====================================");
        
        config.saveConfig();
    }

    private void configureApiServices(Config config) {
        System.out.println("\n-- External Services --");
        System.out.println("Would you like to configure integration with external music services?");
        System.out.println("This allows enriched functionality like artist information and recommendations.");
        System.out.print("Configure external services? (Y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        
        if (!response.startsWith("n")) {
            configureExternalService(config, "spotify", "Spotify");
            configureExternalService(config, "yandexMusic", "Yandex Music");
            configureExternalService(config, "AppleMusic", "Apple Music");
        }
    }

    private void configureExternalService(Config config, String serviceKey, String serviceName) {
        System.out.println("\nConfigure " + serviceName + " API integration:");
        String currentKey = config.getApiKey(serviceKey);
        
        if (!currentKey.isEmpty()) {
            System.out.println("Current API key: " + maskApiKey(currentKey));
            System.out.print("Keep existing key? (Y/n): ");
            String response = scanner.nextLine().trim().toLowerCase();
            
            if (!response.startsWith("n")) {
                return;
            }
        }
        
        System.out.print("Enter " + serviceName + " API key (leave empty to skip): ");
        String key = scanner.nextLine().trim();
        
        if (!key.isEmpty()) {
            config.setApiKey(serviceKey, key);
            System.out.println(serviceName + " API key configured successfully!");
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.isEmpty() || key.length() <= 8) {
            return "*".repeat(Math.max(0, key != null ? key.length() : 0));
        }
        return key.substring(0, 4) + "*".repeat(key.length() - 8) + key.substring(key.length() - 4);
    }
}
