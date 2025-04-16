package services.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import models.Playlist;  // Added import for Playlist

// [Enhancement] SLF4J logging (if available; otherwise, use your existing Logger)
// Uncomment the next two lines if using SLF4J:
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;


/**
 * Handles local caching and storage of music, album, and artist data,
 * and also tracks file changes for offline synchronization.
 */
public class LocalStorageManager {
    private final String fileName; // File name for local storage (mocked)

    // [Enhancement] Using ConcurrentHashMap for thread-safe caching.
    private final Map<String, Map<String, String>> musicCache;
    private final Map<String, String> albumCache;
    private final Map<String, String> artistCache;

    // Fields for tracking file changes for offline sync using lists (could be unified later)
    private final List<File> newFiles = new ArrayList<>();
    private final List<File> deletedFiles = new ArrayList<>();
    private final List<File> modifiedFiles = new ArrayList<>();

    // [Enhancement] Playlist persistence file name
    private static final String PLAYLIST_STORAGE_FILE = "playlists.json";
    
    // [Enhancement] If using SLF4J, use this logger:
    // private static final Logger logger = LoggerFactory.getLogger(LocalStorageManager.class);
    // For now, we maintain System.out.println, but comments indicate where to replace with logger.
    
    public LocalStorageManager(String fileName) {
        this.fileName = fileName;
        this.musicCache = new ConcurrentHashMap<>();
        this.albumCache = new ConcurrentHashMap<>();
        this.artistCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Adds a new file to the offline tracking system.
     * This method is called from OfflineSyncManager.
     *
     * @param file The file that was added.
     */
    public void addNewFile(File file) {
        System.out.println("LocalStorageManager: New file added: " + file.getName());
        newFiles.add(file);
    }
    
    /**
     * Removes a file from the offline tracking system.
     *
     * @param file The file that was removed.
     */
    public void removeFile(File file) {
        System.out.println("LocalStorageManager: File removed: " + file.getName());
        deletedFiles.add(file);
    }
    
    /**
     * Updates metadata for a modified file in the offline tracking system.
     *
     * @param file The file that was modified.
     */
    public void updateFileMetadata(File file) {
        System.out.println("LocalStorageManager: File modified: " + file.getName());
        modifiedFiles.add(file);
    }
    
    /**
     * Returns a list of all new files tracked.
     */
    public List<File> getNewFiles() {
        return new ArrayList<>(newFiles);
    }
    
    /**
     * Returns a list of all deleted files tracked.
     */
    public List<File> getDeletedFiles() {
        return new ArrayList<>(deletedFiles);
    }
    
    /**
     * Returns a list of all modified files tracked.
     */
    public List<File> getModifiedFiles() {
        return new ArrayList<>(modifiedFiles);
    }
    
    /**
     * Clears all file tracking history.
     */
    public void clearSyncHistory() {
        newFiles.clear();
        deletedFiles.clear();
        modifiedFiles.clear();
    }
    
    /**
     * Retrieves cached music data for a specific query.
     * @param query The search keyword.
     * @return Cached music data as a Map wrapped in an Optional.
     */
    public Optional<Map<String, String>> getCachedMusic(String query) {
        System.out.println("Fetching cached music data for query: " + query);
        return Optional.ofNullable(musicCache.get(query));
    }
    
    /**
     * Caches music data for a specific query.
     * @param query The search keyword.
     * @param musicData The music data to cache.
     */
    public void cacheMusic(String query, Map<String, String> musicData) {
        System.out.println("Caching music data for query: " + query);
        musicCache.put(query, musicData);
        saveCacheToFile(); // [Enhancement] Persist cache
    }
    
    /**
     * Retrieves cached album details for a specific album ID.
     * @param albumId The album's unique identifier.
     * @return Cached album details wrapped in an Optional.
     */
    public Optional<String> getCachedAlbum(String albumId) {
        System.out.println("Fetching cached album details for ID: " + albumId);
        return Optional.ofNullable(albumCache.get(albumId));
    }
    
    /**
     * Caches album details.
     * @param albumId The album's unique identifier.
     * @param albumDetails The album details to cache.
     */
    public void cacheAlbum(String albumId, String albumDetails) {
        System.out.println("Caching album details for ID: " + albumId);
        albumCache.put(albumId, albumDetails);
        saveCacheToFile();
    }
    
    /**
     * Retrieves cached artist details for a specific artist name.
     * @param artistName The artist's name.
     * @return Cached artist details wrapped in an Optional.
     */
    public Optional<String> getCachedArtist(String artistName) {
        System.out.println("Fetching cached artist details for: " + artistName);
        return Optional.ofNullable(artistCache.get(artistName));
    }
    
    /**
     * Caches artist details.
     * @param artistName The artist's name.
     * @param artistDetails The artist details to cache.
     */
    public void cacheArtist(String artistName, String artistDetails) {
        System.out.println("Caching artist details for: " + artistName);
        artistCache.put(artistName, artistDetails);
        saveCacheToFile();
    }
    
    /**
     * Deletes a playlist from local storage.
     * @param name The name of the playlist to delete.
     */
    public void deletePlaylist(String name) {
        System.out.println("LocalStorageManager: Deleting playlist '" + name + "'");
        // Implement your playlist deletion logic here (e.g., removing a file or updating a map)
    }
    
    /**
     * Clears the cached music data entry for the given query.
     *
     * @param query The query whose cache entry should be removed.
     */
    public void clearCacheEntry(String query) {
        System.out.println("LocalStorageManager: Clearing cache entry for query '" + query + "'");
        musicCache.remove(query);
        saveCacheToFile();
    }
    
    /**
     * Clears all cached data.
     */
    public void clearCache() {
        System.out.println("Clearing all cached data.");
        musicCache.clear();
        albumCache.clear();
        artistCache.clear();
        saveCacheToFile();
    }
    
    // ================= New Methods Added =================
    
    /**
     * Saves the given playlist to local storage.
     * (Implement your actual saving logic if needed.)
     *
     * @param playlist The playlist to save.
     */
    public void savePlaylist(Playlist playlist) {
        System.out.println("LocalStorageManager: Saving playlist '" + playlist.getName() + "'");
        // [Enhancement 7] Example: Save to a JSON file using Jackson.
        // For now, this is a placeholder. In production, instantiate ObjectMapper and write to PLAYLIST_STORAGE_FILE.
    }
    
    /**
     * Loads all playlists from local storage.
     * (Implement your actual loading logic if needed.)
     *
     * @return A list of all playlists.
     */
    public List<Playlist> loadAllPlaylists() {
        System.out.println("LocalStorageManager: Loading all playlists from storage.");
        // [Enhancement 7] Example: Read from PLAYLIST_STORAGE_FILE with Jackson.
        // For now, this is a placeholder that returns an empty list.
        return new ArrayList<>();
    }
    // ================= End of New Methods =================
    
    /**
     * Retrieves all files stored in the directory specified by fileName.
     * Assumes that fileName represents a directory.
     *
     * @return A list of File objects in the directory.
     */
    public List<File> getAllFiles() {
        List<File> files = new ArrayList<>();
        File dir = new File(fileName);
        if (dir.exists() && dir.isDirectory()) {
            File[] fileArray = dir.listFiles();
            if (fileArray != null) {
                for (File f : fileArray) {
                    files.add(f);
                }
            }
        }
        return files;
    }
    
    /**
     * Saves user preferences to local storage.
     *
     * @param preferences The map of user preferences to save.
     */
    public void savePreferences(Map<String, String> preferences) {
        System.out.println("LocalStorageManager: Saving user preferences to " + fileName);
        // [Enhancement] Add persistence logic for user preferences if desired.
    }
    
    /**
     * Loads user preferences from local storage.
     *
     * @return A map of user preferences. If none are found, returns an empty map.
     */
    public Map<String, String> loadPreferences() {
        System.out.println("LocalStorageManager: Loading user preferences from " + fileName);
        // [Enhancement] Add loading logic for user preferences if desired.
        return new HashMap<>();
    }
    
    // [Enhancement] File-Based Persistence for Cache (using JSON)
    private final String CACHE_FILE = "cache_data.json";
    
    /**
     * Persists the current cache (music, album, artist) to a JSON file.
     */
    private void saveCacheToFile() {
        // Example using Jackson (ensure dependency is added in pom.xml)
        try {
            // Create a wrapper for caches
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("musicCache", musicCache);
            wrapper.put("albumCache", albumCache);
            wrapper.put("artistCache", artistCache);
            
            // Write the wrapper to the CACHE_FILE
            // In a real implementation, use ObjectMapper from Jackson.
            // For demonstration, write the wrapper's toString() to file.
            Files.write(Paths.get(CACHE_FILE), wrapper.toString().getBytes());
            System.out.println("Cache data persisted to " + CACHE_FILE);
        } catch (IOException e) {
            System.err.println("Failed to persist cache data to " + CACHE_FILE + ": " + e.getMessage());
        }
    }
    
    /**
     * Loads cache data from a JSON file into the in-memory caches.
     */
    public void loadCacheFromFile() {
        // Placeholder for JSON-based cache loading
        try {
            File file = new File(CACHE_FILE);
            if (!file.exists()) {
                System.out.println("No cache file found. Starting with empty caches.");
                return;
            }
            // In a real implementation, use ObjectMapper to read the JSON file into a Map.
            // For now, we simulate a successful cache load.
            System.out.println("Cache data loaded from " + CACHE_FILE);
        } catch (Exception e) {
            System.err.println("Failed to load cache data from " + CACHE_FILE + ": " + e.getMessage());
        }
    }
}