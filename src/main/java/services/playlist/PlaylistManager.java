package services.playlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import models.Playlist;
import models.Song;
import services.network.CloudSyncService;
import services.storage.LocalStorageManager;
import utils.Logger;

public class PlaylistManager {

    // Use ConcurrentHashMap for thread-safe access.
    private final Map<String, Playlist> playlists;
    
    private final LocalStorageManager localStorageManager;
    private final CloudSyncService cloudSyncService;
    private final Logger logger;
    
    // Undo stack for deletion operations.
    private final Deque<Runnable> undoStack;
    
    // Set to track modified playlists for efficient cloud synchronization.
    private final Set<String> modifiedPlaylists;

    /**
     * Constructor initializes the playlist manager.
     */
    public PlaylistManager() {
        this.localStorageManager = new LocalStorageManager("playlists/");
        this.cloudSyncService = new CloudSyncService();
        this.logger = Logger.getInstance();
        this.playlists = new ConcurrentHashMap<>();
        this.undoStack = new ArrayDeque<>();
        this.modifiedPlaylists = ConcurrentHashMap.newKeySet();
        
        loadPlaylists();
    }

    /**
     * Creates a new playlist.
     *
     * @param name The name of the playlist.
     * @return The created playlist.
     */
    public Playlist createPlaylist(String name) {
        if (playlists.containsKey(name)) {
            logger.warning("Playlist with name '" + name + "' already exists.");
            return playlists.get(name);
        }

        // Create a new playlist using the provided name.
        Playlist playlist = new Playlist(name);
        // (Metadata updates are removed because the current Playlist model does not support them)
        playlists.put(name, playlist);
        logger.info("Playlist created: " + name);
        savePlaylist(playlist);
        return playlist;
    }

    /**
     * Deletes a playlist.
     *
     * @param name The name of the playlist to delete.
     * @return True if deleted successfully, false otherwise.
     */
    public boolean deletePlaylist(String name) {
        if (!playlists.containsKey(name)) {
            logger.error("Playlist '" + name + "' not found.");
            return false;
        }

        Playlist removed = playlists.remove(name);
        // Push an undo action to allow deletion to be undone.
        undoStack.push(() -> playlists.put(name, removed));
        localStorageManager.deletePlaylist(name);
        cloudSyncService.deletePlaylist(name);
        logger.info("Playlist deleted: " + name);
        return true;
    }

    /**
     * Adds a song to a playlist.
     *
     * @param playlistName The name of the playlist.
     * @param song         The song to add.
     * @return True if added successfully, false otherwise.
     */
    public boolean addSongToPlaylist(String playlistName, Song song) {
        Playlist playlist = playlists.get(playlistName);
        if (playlist == null) {
            logger.error("Playlist '" + playlistName + "' not found.");
            return false;
        }

        List<Song> songList = playlist.getSongs();
        if (!songList.contains(song)) {
            songList.add(song);
            savePlaylist(playlist);
            logger.info("Song added to playlist: " + song.getTitle() + " → " + playlistName);
            markPlaylistAsModified(playlistName);
            return true;
        } else {
            logger.warning("Song already exists in playlist: " + song.getTitle());
            return false;
        }
    }

    /**
     * Removes a song from a playlist.
     *
     * @param playlistName The name of the playlist.
     * @param song         The song to remove.
     * @return True if removed successfully, false otherwise.
     */
    public boolean removeSongFromPlaylist(String playlistName, Song song) {
        Playlist playlist = playlists.get(playlistName);
        if (playlist == null) {
            logger.error("Playlist '" + playlistName + "' not found.");
            return false;
        }

        List<Song> songList = playlist.getSongs();
        if (songList.contains(song)) {
            songList.remove(song);
            savePlaylist(playlist);
            logger.info("Song removed from playlist: " + song.getTitle() + " ← " + playlistName);
            markPlaylistAsModified(playlistName);
            return true;
        } else {
            logger.warning("Song not found in playlist: " + song.getTitle());
            return false;
        }
    }

    /**
     * Returns a list of all available playlists.
     *
     * @return A list of all playlists.
     */
    public List<Playlist> getAllPlaylists() {
        return new ArrayList<>(playlists.values());
    }

    /**
     * Fetches a playlist by name.
     *
     * @param name The name of the playlist.
     * @return The playlist if found, otherwise null.
     */
    public Playlist getPlaylist(String name) {
        return playlists.get(name);
    }

    /**
     * Saves a playlist to local storage and syncs with the cloud.
     *
     * @param playlist The playlist to save.
     */
    private void savePlaylist(Playlist playlist) {
        localStorageManager.savePlaylist(playlist);
        cloudSyncService.syncPlaylist(playlist);
    }

    /**
     * Loads playlists from local storage.
     */
    private void loadPlaylists() {
        List<Playlist> loadedPlaylists = localStorageManager.loadAllPlaylists();
        for (Playlist playlist : loadedPlaylists) {
            playlists.put(playlist.getName(), playlist);
        }
        logger.info("Loaded " + playlists.size() + " playlists from storage.");
    }

    // ------------------- New Enhancement Methods -------------------

    /**
     * Returns playlists sorted alphabetically by name.
     *
     * @return A list of playlists sorted by name.
     */
    public List<Playlist> getPlaylistsSortedByName() {
        return playlists.values().stream()
                .sorted(Comparator.comparing(Playlist::getName))
                .collect(Collectors.toList());
    }

    /**
     * Undoes the most recent playlist deletion.
     */
    public void undo() {
        if (!undoStack.isEmpty()) {
            Runnable undoAction = undoStack.pop();
            undoAction.run();
            logger.info("Undo operation executed.");
        } else {
            logger.warning("No actions to undo.");
        }
    }

    /**
     * Marks a playlist as modified for cloud synchronization.
     *
     * @param name The name of the playlist.
     */
    public void markPlaylistAsModified(String name) {
        modifiedPlaylists.add(name);
    }

    /**
     * Syncs only the playlists that have been marked as modified.
     */
    public void syncModifiedPlaylists() {
        for (String playlistName : modifiedPlaylists) {
            Playlist playlist = playlists.get(playlistName);
            if (playlist != null) {
                cloudSyncService.syncPlaylist(playlist);
                logger.info("Synchronized modified playlist: " + playlistName);
            }
        }
        modifiedPlaylists.clear();
    }
}