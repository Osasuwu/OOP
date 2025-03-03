package services;

import interfaces.MusicDataSource;
import services.datasources.*;
import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

public class SourceSelector {
    private final Scanner scanner;

    public SourceSelector() {
        this.scanner = new Scanner(System.in);
    }

    public List<MusicDataSource> selectSources() {
        List<MusicDataSource> selectedSources = new ArrayList<>();
        boolean done = false;

        while (!done) {
            System.out.println("\nAvailable music data sources:");
            System.out.println("1. IFTTT Spotify History (CSV file)");
            System.out.println("2. Local Music Files");
            System.out.println("3. Other CSV Format");
            System.out.println("4. Done selecting");

            System.out.print("\nSelect a source (1-4): ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    MusicDataSource iftttSource = configureIFTTTSource();
                    if (iftttSource != null) {
                        selectedSources.add(iftttSource);
                        System.out.println("IFTTT Spotify source added!");
                    }
                    break;

                case "2":
                    MusicDataSource localSource = configureLocalFilesSource();
                    if (localSource != null) {
                        selectedSources.add(localSource);
                        System.out.println("Local files source added!");
                    }
                    break;

                case "3":
                    MusicDataSource csvSource = configureCustomCSVSource();
                    if (csvSource != null) {
                        selectedSources.add(csvSource);
                        System.out.println("Custom CSV source added!");
                    }
                    break;

                case "4":
                    done = true;
                    break;

                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }

        return selectedSources;
    }

    private MusicDataSource configureIFTTTSource() {
        System.out.println("\nIFTTT Spotify History Configuration");
        System.out.print("Enter the path to your IFTTT CSV file: ");
        String path = scanner.nextLine();

        try {
            Path csvPath = Paths.get(path);
            IFTTTSpotifySource source = new IFTTTSpotifySource(csvPath.toString());
            
            if (source.isSourceAvailable()) {
                return source;
            } else {
                System.out.println("Error: The file doesn't appear to be a valid IFTTT Spotify history file.");
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error: Invalid file path");
            return null;
        }
    }

    private MusicDataSource configureLocalFilesSource() {
        System.out.println("\nLocal Music Files Configuration");
        System.out.print("Enter the path to your music directory: ");
        String path = scanner.nextLine();

        try {
            Path musicPath = Paths.get(path);
            LocalFilesSource source = new LocalFilesSource(musicPath.toString());
            
            if (source.isSourceAvailable()) {
                return source;
            } else {
                System.out.println("Error: No music files found in the specified directory.");
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error: Invalid directory path");
            return null;
        }
    }

    private MusicDataSource configureCustomCSVSource() {
        System.out.println("\nCustom CSV Configuration");
        System.out.print("Enter the path to your CSV file: ");
        String path = scanner.nextLine();

        System.out.println("\nSelect CSV format:");
        System.out.println("1. Title,Artist,Date");
        System.out.println("2. Artist,Album,Title");
        System.out.println("3. Custom format");
        
        System.out.print("Choice (1-3): ");
        String formatChoice = scanner.nextLine();

        try {
            Path csvPath = Paths.get(path);
            String format;
            
            switch (formatChoice) {
                case "1":
                    format = "title,artist,date";
                    break;
                case "2":
                    format = "artist,album,title";
                    break;
                case "3":
                    System.out.print("Enter your CSV columns (comma-separated): ");
                    format = scanner.nextLine().toLowerCase();
                    break;
                default:
                    System.out.println("Invalid choice");
                    return null;
            }

            CsvHistorySource source = new CsvHistorySource(csvPath.toString(), format);
            if (source.isSourceAvailable()) {
                return source;
            } else {
                System.out.println("Error: The file doesn't appear to be a valid CSV file.");
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error: Invalid file path");
            return null;
        }
    }
} 