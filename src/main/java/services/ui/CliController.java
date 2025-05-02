package services.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

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
        System.out.println("Welcome to Playlist Generator CLI!");
        boolean running = true;
        
        while (running) {
            printMainMenu();
            int choice = getUserChoice(1, 5);
            
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
                    logout();
                    running = false; // Ensure CLI loop exits after logout
                    break;
                case 5:
                    running = false; // Exit the program
                    System.out.println("Exiting Playlist Generator. Goodbye!");
                    break;
                default:
                    System.out.println("Invalid choice. Please enter a valid option.");
            }
        }
        
        scanner.close(); // Close scanner to release system resources
        // Perform any additional cleanup, if necessary
    }
    
    private void printMainMenu() {
        System.out.println("\n===== Playlist Generator =====");
        System.out.println("1. Generate Playlist");
        System.out.println("2. Launch User Interface");
        System.out.println("3. Import Data");
        System.out.println("4. Logout");
        System.out.println("5. Exit");
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
        System.out.println("4. Balanced");
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

        System.out.print("Enter minimum duration (in seconds, default 0): ");
        try {
            int minDuration = Integer.parseInt(scanner.nextLine().trim());
            params.setMinDuration(minDuration);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input, setting minimum duration to 0");
        }

        System.out.print("Enter maximum duration (in seconds, default 3600): ");
        try {
            int maxDuration = Integer.parseInt(scanner.nextLine().trim());
            params.setMaxDuration(maxDuration);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input, setting maximum duration to 3600 seconds");
            params.setMaxDuration(3600); // Default to 1 hour
        }

        System.out.print("Do you have custom parameters? (yes/no): ");
        String customParamsResponse = scanner.nextLine().trim().toLowerCase();
        if (customParamsResponse.equals("yes")) {
            Map<String, Set<String>> parameterMap = params.getInclusionCriteria();
            System.out.println("Available custom parameters:");

            int paramIndex = 1;
            Map<Integer, String> paramIndexMap = new HashMap<>();

            for (String key : parameterMap.keySet()) {
                System.out.println(paramIndex + ". Include " + key);
                paramIndexMap.put(paramIndex++, "include:" + key);
                System.out.println(paramIndex + ". Exclude " + key);
                paramIndexMap.put(paramIndex++, "exclude:" + key);
            }

            System.out.print("Enter your choice (or 0 to finish): ");
            int paramChoice = getUserChoice(0, paramIndexMap.size());

            while (paramChoice != 0) {
                String selectedParam = paramIndexMap.get(paramChoice);
                String[] parts = selectedParam.split(":");
                String action = parts[0];
                String key = parts[1];
                
                System.out.print("Enter " + key + " values (comma-separated): ");
                String input = scanner.nextLine().trim();
                Set<String> values = Set.of(input.split(","));
                
                if (action.equals("include")) {
                    params.setInclusionCriteria(key, values);
                    System.out.println("Added inclusion filter for " + key);
                } else {
                    params.setExclusionCriteria(key, values);
                    System.out.println("Added exclusion filter for " + key);
                }
                
                System.out.print("Enter another parameter choice (or 0 to finish): ");
                paramChoice = getUserChoice(0, paramIndexMap.size());
            }
        }
    
        System.out.println("Generating playlist...");
        boolean success = app.generatePlaylist(params);
        if (success) {
            System.out.println("Playlist generated successfully!");
            System.out.println("Do you want to export it? (y/n)");
            String exportChoice = scanner.nextLine().trim().toLowerCase();
            if (exportChoice.equals("y")) {
                System.out.println("Select export format: ");
                System.out.println("1. CSV");
                System.out.println("2. M3U");
                System.out.println("3. Spotify");
                System.out.print("Enter your choice: ");

                int formatChoice = getUserChoice(1, 3);
                String format;
                switch (formatChoice) {
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
                        System.out.println("Invalid choice. Defaulting to CSV.");
                        format = "csv";
                }

                app.exportPlaylist(format);
            }
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
    
    private void logout() {
        System.out.println("\n----- Logging Out -----");
        authService.logout();
        System.out.println("You have been logged out successfully.");
        System.out.println("Restarting application...");
        
        // Restart the application by launching a new instance
        Application app = new Application();
        app.start();
    }
}