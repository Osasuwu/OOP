package services;
import services.api.*;
import services.database.*;
import services.datasources.*;
import services.output.*;
import services.ui.*;
import models.*;
import interfaces.*;
import utils.*;
import java.util.*;

public class PlaylistGeneratorApp {
    private final MusicDatabaseManager dbManager;
    private final OfflineDataManager offlineManager;
    private final SpotifyAPIManager spotifyManager;
    private final MusicBrainzAPIManager musicBrainzManager;
    private final LastFmAPIManager lastFmManager;
    private final GenreMapper genreMapper;
    private final String userId;
    private final UserInterface userInterface;
    private boolean isOnline;

    public PlaylistGeneratorApp(String userId) {
        this.userId = userId;
        this.genreMapper = new GenreMapper();
        
        // Check internet connectivity
        this.isOnline = checkInternetConnectivity();
        
        // Initialize managers
        this.dbManager = new MusicDatabaseManager(isOnline, userId);
        this.offlineManager = new OfflineDataManager(userId);
        this.spotifyManager = new SpotifyAPIManager();
        this.musicBrainzManager = new MusicBrainzAPIManager();
        this.lastFmManager = new LastFmAPIManager();
        this.userInterface = new UserInterface();
    }

    public static void main(String[] args) {
        // Example usage
        String userId = "user123"; // Replace with actual user ID
        PlaylistGeneratorApp app = new PlaylistGeneratorApp(userId);
        
        // Generate a playlist
        app.generatePlaylist("My Playlist", 20);
        
        // Update user preferences
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("favorite_genres", new String[]{"rock", "pop"});
        preferences.put("favorite_artists", new String[]{"The Beatles", "Queen"});
        preferences.put("preferred_energy", 0.8);
        app.updateUserPreferences(preferences);
        
        // Sync offline data when back online
        app.syncOfflineData();
    }

    private boolean checkInternetConnectivity() {
        try {
            // Try to connect to Spotify API
            String token = SpotifyAccessToken.getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            System.out.println("No internet connection detected. Running in offline mode.");
            return false;
        }
    }

    public void generatePlaylist(PlaylistParameters params) {
        //implementation
    }

    private List<Map<String, Object>> getSongsFromSimilarArtists(Map<String, Object> userPrefs, int count) throws Exception {
        List<Map<String, Object>> songs = new ArrayList<>();
        Set<String> favoriteArtists = new HashSet<>();
        
        // Get favorite artists from preferences
        if (userPrefs.containsKey("favorite_artists")) {
            Object[] artists = (Object[]) userPrefs.get("favorite_artists");
            favoriteArtists.addAll(Arrays.asList(Arrays.copyOf(artists, artists.length, String[].class)));
        }

        // Get similar artists for each favorite artist
        for (String artist : favoriteArtists) {
            if (songs.size() >= count) break;
            
            List<Map<String, Object>> similarArtists = dbManager.getSimilarArtists(artist, 3);
            for (Map<String, Object> similarArtist : similarArtists) {
                if (songs.size() >= count) break;
                
                // Get songs from similar artist
                List<Map<String, Object>> artistSongs = getArtistSongs((String) similarArtist.get("name"), count - songs.size());
                songs.addAll(artistSongs);
            }
        }

        return songs;
    }

    private List<Map<String, Object>> getArtistSongs(String artistName, int limit) throws Exception {
        if (isOnline) {
            return spotifyManager.getArtistSongs(artistName, limit);
        } else {
            return offlineManager.getArtistSongs(artistName, limit);
        }
    }

    private void createPlaylist(String playlistName, List<Map<String, Object>> songs) {
        try {
            if (isOnline) {
                // Create playlist on Spotify
                spotifyManager.createPlaylist(playlistName, songs);
            } else {
                // Save playlist locally
                offlineManager.savePlaylist(playlistName, songs);
            }
            System.out.println("Playlist '" + playlistName + "' created successfully!");
        } catch (Exception e) {
            System.err.println("Error creating playlist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateUserPreferences(Map<String, Object> preferences) {
        try {
            if (isOnline) {
                dbManager.updateUserPreferences(preferences);
            } else {
                offlineManager.updateUserPreferences(preferences);
            }
            System.out.println("User preferences updated successfully!");
        } catch (Exception e) {
            System.err.println("Error updating user preferences: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void syncOfflineData() {
        if (!isOnline) {
            System.out.println("Cannot sync data while offline.");
            return;
        }

        try {
            // Get offline data
            Map<String, Object> offlineData = offlineManager.getOfflineData();
            
            // Sync with database
            syncUserPreferences(offlineData);
            syncPlaylists(offlineData);
            syncListenHistory(offlineData);
            
            System.out.println("Offline data synced successfully!");
        } catch (Exception e) {
            System.err.println("Error syncing offline data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveUserData(UserMusicData userData) {
        try {
            dbManager.saveUserData(userData);
            System.out.println("User data saved successfully!");
        } catch (Exception e) {
            System.err.println("Error saving user data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void syncUserPreferences(Map<String, Object> offlineData) throws Exception {
        if (offlineData.containsKey("user_preferences")) {
            Map<String, Object> preferences = (Map<String, Object>) offlineData.get("user_preferences");
            dbManager.updateUserPreferences(preferences);
        }
    }

    private void syncPlaylists(Map<String, Object> offlineData) throws Exception {
        if (offlineData.containsKey("playlists")) {
            Map<String, Object> playlists = (Map<String, Object>) offlineData.get("playlists");
            for (Map.Entry<String, Object> entry : playlists.entrySet()) {
                String playlistName = entry.getKey();
                List<Map<String, Object>> songs = (List<Map<String, Object>>) entry.getValue();
                spotifyManager.createPlaylist(playlistName, songs);
            }
        }
    }

    private void syncListenHistory(Map<String, Object> offlineData) throws Exception {
        if (offlineData.containsKey("listen_history")) {
            List<Map<String, Object>> history = (List<Map<String, Object>>) offlineData.get("listen_history");
            for (Map<String, Object> entry : history) {
                // Update listen history in database
                // Implementation depends on your database schema
            }
        }
    }
}