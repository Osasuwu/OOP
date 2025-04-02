package services.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import models.Playlist;
import models.PlaylistParameters;
import models.UserMusicData;
import services.DataImportService;
import services.PlaylistGeneratorApp;
import services.generator.Generator;
import services.importer.CsvDataImportAdapter;
import services.importer.DataImportAdapter;
import services.importer.DataImportFactory;
import services.importer.ImportException;


public class CliController {
    private final PlaylistGeneratorApp app;
    private final Scanner scanner;
    private final UserInterface ui;
    public CliController(PlaylistGeneratorApp app) {
        this.app = app;
        this.scanner = new Scanner(System.in);
        this.ui = new UserInterface();
    }
//n
public void start() {
    System.out.println("Welcome to Playlist Generator CLI!");
    boolean running = true;

    while (running) {
        printMainMenu(); // Displays the main menu options
        int choice = getUserChoice(1, 5); // Adjusted to match the number of valid menu options

        switch (choice) {
            case 1:
                generatePlaylist(); // Generates a playlist based on user input
                break;
            case 2:
                launchUserInterface(); // Launches the GUI user interface
                break;
            case 3:
                importData(); // Imports user data for playlist generation
                break;
            case 4:
                runGeneratorTest(); // Executes the Generator test logic
                break;
            case 5:
                running = false; // Stops the CLI loop and exits the application
                System.out.println("Exiting Playlist Generator. Goodbye!");
                break;
            default:
                System.out.println("Invalid option. Please select a valid menu item.");
        }
    }
    scanner.close(); // Ensures scanner resources are released upon exit
}
//n
    private void runGeneratorTest() {
        System.out.println("\n----- Running Generator Test -----");
        try {
            // Instantiate the Generator class
            Generator generator = new Generator();
    
            // Prompt user for input values
            System.out.print("Enter user ID: ");
            String userId = scanner.nextLine().trim();
    
            System.out.print("Enter criteria (e.g., 'popular', 'diverse'): ");
            String criteria = scanner.nextLine().trim();
    
            // Call the generator method
            Playlist playlist = generator.generatePlaylist(userId, criteria);
    
            // Print the playlist details
            System.out.println("\nGenerated Playlist: " + playlist.getName());
            System.out.println("Songs in Playlist:");
            playlist.getSongs().forEach(song -> System.out.println("- " + song.getTitle()));
        } catch (Exception e) {
            System.err.println("An error occurred while running the generator: " + e.getMessage());
            e.printStackTrace();
        }
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
                System.out.print("Please enter a valid number: ");
            }
        }
        return choice;
    }
    private void generatePlaylist() {
        System.out.println("\n----- Generate Playlist -----");
    
        // Create a PlaylistParameters object to store user input
        PlaylistParameters params = new PlaylistParameters();
    
        // Collect playlist name
        System.out.print("Enter playlist name: ");
        params.setName(scanner.nextLine().trim());
    
        // Collect song count (validate within range)
        System.out.print("How many songs (10-100)? ");
        params.setSongCount(getUserChoice(10, 100));
    
        // Collect playlist generation strategy
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
    
        // Generate playlist using app

        Playlist playlist = app.generatePlaylist(params);

        // Display playlist details
        System.out.println("\nGenerated Playlist: " + playlist.getName());
        System.out.println("Songs in Playlist:");
        playlist.getSongs().forEach(song -> System.out.println("- " + song.getTitle()));
    }

    private void launchUserInterface() {
        System.out.println("\n----- Launching User Interface -----");
        ui.start();
    }
    
    private void importData() {
        System.out.println("\n----- Import Data -----");
        System.out.println("1. Import from file");
        System.out.println("2. Import from directory");
        System.out.println("3. Import from Spotify");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option: ");
        
        int choice = getUserChoice(1, 4);
        
        switch (choice) {
            case 1:
                importFromFile();
                break;
            case 2:
                importFromDirectory();
                break;
            case 3:
                importFromSpotify();
                break;
            case 4:
                return;
        }
    }
    
    private void importFromFile() {
        System.out.print("Enter file path to import: ");
        String filePath = scanner.nextLine().trim();
        
        try {
            Path path = Paths.get(filePath);
            DataImportFactory importFactory = new DataImportFactory();
            DataImportAdapter importAdapter;
            
            try {
                // Get appropriate adapter based on file extension
                importAdapter = importFactory.getAdapter(path);
                System.out.println("Using " + importAdapter.getClass().getSimpleName() + " for import...");
            } catch (ImportException e) {
                // Fallback to CSV adapter if no specific adapter is found
                System.out.println("No specific adapter found. Defaulting to CSV import...");
                importAdapter = new CsvDataImportAdapter();
                if (!importAdapter.canHandle(path)) {
                    System.err.println("Unsupported file format for: " + filePath);
                    return;
                }
            }
            
            System.out.println("Importing data from: " + filePath);
            UserMusicData importedData = importAdapter.importFromFile(path);
            
            if (importedData != null && !importedData.isEmpty()) {
                System.out.println("Successfully imported data from " + filePath);
                System.out.println("Songs: " + importedData.getSongs().size());
                System.out.println("Artists: " + importedData.getArtists().size());
                System.out.println("Play history entries: " + importedData.getPlayHistory().size());
                
                // Store imported data
                app.importUserData(importedData);
                
                // Ask if user wants to generate a playlist from the imported data
                System.out.print("Do you want to generate a playlist from the imported data? (y/n): ");
                if (scanner.nextLine().trim().toLowerCase().startsWith("y")) {
                    generatePlaylistFromImported();
                }
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
    
    private void importFromDirectory() {
        System.out.print("Enter directory path to import from: ");
        String directoryPath = scanner.nextLine().trim();
        
        try {
            DataImportService importService = new DataImportService();
            System.out.println("Importing data from directory: " + directoryPath);
            
            UserMusicData importedData = importService.importFromDirectory(directoryPath);
            
            if (importedData != null && !importedData.isEmpty()) {
                System.out.println("Successfully imported data from directory");
                System.out.println("Songs: " + importedData.getSongs().size());
                System.out.println("Artists: " + importedData.getArtists().size());
                System.out.println("Play history entries: " + importedData.getPlayHistory().size());
                
                // Store imported data
                app.saveUserData(importedData);
            } else {
                System.err.println("No usable data found in directory: " + directoryPath);
            }
        } catch (ImportException e) {
            System.err.println("Error importing data: " + e.getMessage());
        }
    }
    
    private void importFromSpotify() {
        System.out.println("\n----- Spotify Import -----");
        System.out.print("Enter your Spotify user ID: ");
        String userId = scanner.nextLine().trim();
        
        System.out.print("Enter your Spotify access token: ");
        String accessToken = scanner.nextLine().trim();
        
        if (userId.isEmpty() || accessToken.isEmpty()) {
            System.out.println("User ID and access token are required for Spotify import.");
            return;
        }
        
        try {
            DataImportService importService = new DataImportService();
            System.out.println("Connecting to Spotify...");
            
            UserMusicData importedData = importService.importFromStreamingService("spotify", accessToken, userId);
            
            if (importedData != null && !importedData.isEmpty()) {
                System.out.println("Successfully imported data from Spotify");
                System.out.println("Songs: " + importedData.getSongs().size());
                System.out.println("Artists: " + importedData.getArtists().size());
                System.out.println("Play history entries: " + importedData.getPlayHistory().size());
                
                // Store imported data
                app.saveUserData(importedData);
            } else {
                System.err.println("No usable data found from Spotify account.");
            }
        } catch (ImportException e) {
            System.err.println("Error importing from Spotify: " + e.getMessage());
        }
    }

    
    private void generatePlaylistFromImported() {
        PlaylistParameters params = new PlaylistParameters();
        
        System.out.print("Enter playlist name: ");
        params.setName(scanner.nextLine().trim());
        
        System.out.print("How many songs (10-100)? ");
        params.setSongCount(getUserChoice(10, 100));
        
        // Set to prefer imported music
        params.setPreferImportedMusic(true);
        
        // Use balanced strategy by default
        params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.BALANCED);
        
        System.out.println("Generating playlist from imported data...");
        app.generatePlaylist(params);
    }
    public void runGenerator(String userId, String criteria) {
    Generator generator = new Generator(); // Create an instance of Generator
    Playlist playlist = generator.generatePlaylist(userId, criteria); // Call generatePlaylist
    System.out.println("Generated Playlist: " + playlist.getName());
    System.out.println("Songs in Playlist:");
    playlist.getSongs().forEach(song -> System.out.println("- " + song.getTitle()));
}
//n
private void printMainMenu() {
    System.out.println("\nMain Menu:");
    System.out.println("1. Run Generator Test"); // New option for Generator.java
    System.out.println("2. Generate Playlist");
    System.out.println("3. Import Data");
    System.out.println("4. Exit");
}

    }