package services.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import app.Application;
import models.PlaylistParameters;
import models.UserMusicData;
import services.AuthenticationService;
import services.importer.ImportException;
import services.importer.file.BatchFileImportService;
import services.importer.file.FileImportAdapter;
import services.importer.file.FileImportAdapterFactory;
import services.importer.service.ServiceImportAdapter;
import services.importer.service.ServiceImportAdapterFactory;
import services.output.PlaylistExporter;
import services.output.PlaylistExporterFactory;
/**
 * Controller class for Command Line Interface operations
 * Handles user interaction and commands for the playlist generator
 */
public class CliController {
    private final Application app;
    private final Scanner scanner;
    private final UserInterface ui;
    private final AuthenticationService authService = new AuthenticationService();
    
    public CliController(Application app) {
        this.app = app;
        this.scanner = new Scanner(System.in);
        this.ui = new UserInterface();
        System.out.println("CliController initialized successfully.");
    }
    
    public void start() {
        boolean running = true;
        while (running) {
            System.out.println("Welcome to Playlist Generator CLI!");
            printMainMenu();
            int choice = getUserChoice(1, 6);
            
            switch (choice) {
                case 1:
                    generatePlaylist();
                    break;
                case 2:
                    launchUserInterface();
                    break;
                case 3:
                    importData();
                    break;
                case 4:
                    exportPlaylist();
                    break;
                case 5:
                    if (logout()) {
                        // Reset and continue the loop
                        continue;
                    }
                    running = false;
                    break;
                case 6:
                    running = false;
                    System.out.println("Exiting Playlist Generator. Goodbye!");
                    break;
            }
        }
        scanner.close();
    }

    private void printMainMenu() {
        System.out.println("\n===== Playlist Generator =====");
        System.out.println("1. Generate Playlist");
        System.out.println("2. Launch User Interface");
        System.out.println("3. Import Data");
        System.out.println("4. Export Playlist");
        System.out.println("5. Logout");
        System.out.println("6. Exit");
        System.out.print("Enter your choice: ");
    }
    
    private int getUserChoice(int min, int max) {
        int choice = -1;
        while (choice < min || choice > max) {
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice < min || choice > max) {
                    System.out.print("Please enter a number between " + min + " and " + max + ": ");
                }
            } catch (NumberFormatException e) {
                System.out.print("Invalid input. Please enter a valid number: ");
            }
        }
        return choice;
    }
    
    private void generatePlaylist() {
        System.out.println("\n----- Generate Playlist -----");
        PlaylistParameters params = new PlaylistParameters();
        System.out.print("Enter playlist name: ");
        params.setName(scanner.nextLine().trim());
    
        System.out.print("How many songs (10-100)? ");
        params.setSongCount(getUserChoice(10, 100));
    
        System.out.println("Select generation strategy:");
        System.out.println("1. Random");
        System.out.println("2. Popular");
        System.out.println("3. Diverse");
        System.out.println("4. Balanced (default)");
        System.out.print("Enter your choice: ");
        int strategyChoice = getUserChoice(1, 4);
    
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
    
        System.out.println("Generating playlist...");
        boolean success = app.generatePlaylist(params);
        if (success) {
            System.out.println("Playlist generated successfully!");
        } else {
            System.out.println("Failed to generate playlist.");
        }
    }
    
    private void launchUserInterface() {
        System.out.println("\n----- Launching User Interface -----");
        ui.start(); // Ensure the UI logic is implemented in UserInterface
    }
    
    private void importData() {
        System.out.println("\n----- Import Data -----");
        System.out.println("1. Import from file");
        System.out.println("2. Import from directory");
        System.out.println("3. Import from Streaming Service");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option: ");
        
        int choice = getUserChoice(1, 4); // Adjust max to 4 since there are four choices
        
        switch (choice) {
            case 1:
                importFromFile();
                break;
            case 2:
                importFromDirectory();
                break;
            case 3:
                importFromStreamingService();
                break;
            case 4:
                System.out.println("Returning to main menu...");
                break; // No action needed for "Back to main menu"
            default:
                System.out.println("Invalid option. Please try again.");
        }
    }
    
    
    private void importFromFile() {
        System.out.print("Enter file path to import: ");
        String filePath = scanner.nextLine();
        
        System.out.println("Importing data from " + filePath + "...");
        
        try {
            // Create a path from the file string
            Path path = Paths.get(filePath);
            
            // Get the appropriate adapter
            FileImportAdapterFactory factory = new FileImportAdapterFactory();
            FileImportAdapter adapter = factory.getAdapter(path);
            
            // Import the data
            UserMusicData userData = adapter.importFromFile(path);
            
            if (userData != null && !userData.isEmpty()) {
                System.out.println("Successfully imported from file:");
                System.out.println("Songs: " + userData.getSongs().size());
                System.out.println("Artists: " + userData.getArtists().size());
                System.out.println("Play history entries: " + userData.getPlayHistory().size());
                
                // Send to Application to save
                boolean success = app.importUserData(userData);
                
                if (success) {
                    System.out.println("Data successfully saved to database.");
                } else {
                    System.out.println("Failed to save data to database.");
                }
            } else {
                System.out.println("No usable data found in file: " + filePath);
            }
        } catch (ImportException e) {
            System.out.println("Error importing data: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }
    
    private void importFromDirectory() {
        System.out.print("Enter directory path to import from: ");
        String directoryPath = scanner.nextLine().trim();
        
        try {
            BatchFileImportService batchImporter = new BatchFileImportService();
            System.out.println("Importing data from directory: " + directoryPath);
            
            UserMusicData userData = batchImporter.importFromDirectory(Paths.get(directoryPath));
            
            if (userData != null && !userData.isEmpty()) {
                System.out.println("Successfully imported data from directory");
                System.out.println("Songs: " + userData.getSongs().size());
                System.out.println("Artists: " + userData.getArtists().size());
                System.out.println("Play history entries: " + userData.getPlayHistory().size());
                
                // Import the data through Application
                boolean success = app.importUserData(userData);
                
                if (success) {
                    System.out.println("Data successfully saved to database.");
                } else {
                    System.out.println("Failed to save data to database.");
                }
            } else {
                System.err.println("No usable data found in directory: " + directoryPath);
            }
        } catch (ImportException e) {
            System.err.println("Error importing data: " + e.getMessage());
        }
    }
    
    private void importFromStreamingService() {
        System.out.println("\n----- Import from Streaming Service -----");
        System.out.println("1. Spotify");
        System.out.println("2. Apple Music");
        System.out.println("3. YouTube Music");
        System.out.println("4. Back to import menu");
        System.out.print("Choose a service: ");
        
        int choice = getUserChoice(1, 4);
        if (choice == 4) return;
        
        String serviceName;
        switch (choice) {
            case 1:
                serviceName = "spotify";
                break;
            case 2:
                serviceName = "apple_music";
                break;
            case 3:
                serviceName = "youtube_music";
                break;
            default:
                return;
        }
        
        // Collect authentication details
        Map<String, String> credentials = new HashMap<>();
        
        // Generic credential gathering - service-specific adapters will use what they need
        System.out.print("Enter your access token: ");
        String token = scanner.nextLine().trim();
        credentials.put("access_token", token);
        
        System.out.print("Enter your user ID (or leave blank if using token): ");
        String userId = scanner.nextLine().trim();
        if (!userId.isEmpty()) {
            credentials.put("user_id", userId);
        }
        
        try {
            // Get the appropriate adapter
            ServiceImportAdapterFactory factory = new ServiceImportAdapterFactory();
            ServiceImportAdapter adapter = factory.getAdapter(serviceName);
            
            System.out.println("Importing data from " + serviceName + "...");
            
            // Import the data
            UserMusicData userData = adapter.importFromService(credentials);
            
            if (userData != null && !userData.isEmpty()) {
                System.out.println("Successfully imported from " + serviceName + ":");
                System.out.println("Songs: " + userData.getSongs().size());
                System.out.println("Artists: " + userData.getArtists().size());
                System.out.println("Play history entries: " + userData.getPlayHistory().size());
                
                // Send to Application to save
                boolean success = app.importUserData(userData);
                
                if (success) {
                    System.out.println("Data successfully saved to database.");
                } else {
                    System.out.println("Failed to save data to database.");
                }
            } else {
                System.out.println("No usable data found from service: " + serviceName);
            }
        } catch (ImportException e) {
            System.out.println("Error importing from service: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }
    
    private void exportPlaylist() {
        if (app.getCurrentPlaylist() == null) {
            System.out.println("No playlist available to export. Please generate a playlist first.");
            return;
        }

        System.out.println("\n----- Export Playlist -----");
        System.out.println("Choose export format:");
        System.out.println("1. CSV");
        System.out.println("2. M3U");
        System.out.println("3. Spotify");
        System.out.print("Enter your choice: ");

        int choice = getUserChoice(1, 3);
        String format;
        switch (choice) {
            case 1:
                format = "csv";
                break;
            case 2:
                format = "m3u";
                break;
            case 3:
                format = "spotify";
                break;
            default:
                System.out.println("Invalid format choice.");
                return;
        }

        System.out.print("Enter destination path for export: ");
        String destination = scanner.nextLine().trim();

        try {
            PlaylistExporter exporter = PlaylistExporterFactory.getExporter(format);
            exporter.export(app.getCurrentPlaylist(), destination);
            System.out.println("Playlist exported successfully to: " + destination);
        } catch (IllegalArgumentException e) {
            System.err.println("Export failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred during export: " + e.getMessage());
        }
    }
    
    private boolean logout() {
        System.out.println("\n----- Logging Out -----");
        authService.logout();
        System.out.println("You have been logged out successfully.");
        return true; // Return true to indicate we should restart
    }
}