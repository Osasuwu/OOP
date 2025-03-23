package services.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles local caching and storage of music, album, and artist data,
 * and also tracks file changes for offline synchronization.
 */
public class LocalStorageManager {
    private final String fileName; // File name for local storage (mocked)
    private final Map<String, Map<String, String>> musicCache; // Caches music data
    private final Map<String, String> albumCache; // Caches album data
    private final Map<String, String> artistCache; // Caches artist data

    // Fields for tracking file changes for offline sync
    private final List<File> newFiles = new ArrayList<>();
    private final List<File> deletedFiles = new ArrayList<>();
    private final List<File> modifiedFiles = new ArrayList<>();

    public LocalStorageManager(String fileName) {
        this.fileName = fileName;
        this.musicCache = new HashMap<>();
        this.albumCache = new HashMap<>();
        this.artistCache = new HashMap<>();
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
     * Clears all cached data.
     */
    public void clearCache() {
        System.out.println("Clearing all cached data.");
        musicCache.clear();
        albumCache.clear();
        artistCache.clear();
    }
}