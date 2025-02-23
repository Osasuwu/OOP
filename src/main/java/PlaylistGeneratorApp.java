import java.util.List;
import java.util.ArrayList;
import interfaces.MusicDataSource;
import models.*;
import services.*;
import services.output.*;
import utils.GenreMapper;
import java.util.Scanner;
import services.datasources.*;
import services.SourceSelector;

public class PlaylistGeneratorApp {
    private List<MusicDataSource> dataSources;
    private final PlaylistGenerator generator;
    private final List<PlaylistExporter> exporters;
    
    public PlaylistGeneratorApp() {
        this.generator = new PlaylistGenerator(new GenreMapper(), new GetDataViaSpotifyAPI());
        this.exporters = initializeExporters();
    }

    private List<PlaylistExporter> initializeExporters() {
        List<PlaylistExporter> list = new ArrayList<>();
        list.add(new SpotifyPlaylistExporter());
        list.add(new M3uPlaylistExporter());
        list.add(new CsvPlaylistExporter());
        return list;
    }

    private PlaylistPreferences getUserPreferences() {
        PlaylistPreferences prefs = new PlaylistPreferences();
        // Implementation to get user preferences
        return prefs;
    }

    private String getDestination() {
        // Implementation to get export destination
        return "playlist.csv";
    }

    public void run() {
        // 1. Let user select data sources
        SourceSelector selector = new SourceSelector();
        this.dataSources = selector.selectSources();
        
        if (dataSources.isEmpty()) {
            System.out.println("No data sources selected. Using manual input mode.");
            // Handle manual input case
        }

        // 2. Collect data from selected sources
        UserMusicData userData = collectUserData();
        
        // 3. Get user preferences
        PlaylistPreferences preferences = getUserPreferences();
        
        // 4. Generate playlist
        Playlist playlist = generator.generatePlaylist(userData, preferences);
        
        // 5. Let user choose export format
        exportPlaylist(playlist);
    }

    private void exportPlaylist(Playlist playlist) {
        System.out.println("\nChoose export format:");
        System.out.println("1. Spotify Playlist");
        System.out.println("2. M3U File");
        System.out.println("3. CSV File");
        
        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine();
        
        PlaylistExporter exporter = switch (choice) {
            case "1" -> new SpotifyPlaylistExporter();
            case "2" -> new M3uPlaylistExporter();
            case "3" -> new CsvPlaylistExporter();
            default -> {
                System.out.println("Invalid choice. Defaulting to CSV export.");
                yield new CsvPlaylistExporter();
            }
        };
        
        System.out.print("Enter export destination (file path or playlist name): ");
        String destination = scanner.nextLine();
        
        exporter.export(playlist, destination);
    }

    private UserMusicData collectUserData() {
        UserMusicData combined = new UserMusicData();
        
        // Try each data source in order
        for (MusicDataSource source : dataSources) {
            if (source.isSourceAvailable()) {
                UserMusicData sourceData = source.loadMusicData();
                if (sourceData != null && !sourceData.isEmpty()) {
                    combined.merge(sourceData);
                    
                    // If we got good data from IFTTT, we can stop here
                    if (source instanceof IFTTTSpotifySource && 
                        !sourceData.getPlayHistory().isEmpty()) {
                        break;
                    }
                }
            }
        }
        
        return combined;
    }
} 