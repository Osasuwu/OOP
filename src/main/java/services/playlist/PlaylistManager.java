package services.playlist;

import services.utils.Logger;
import services.data.LocalStorageManager;
import services.network.CloudSyncService;
import services.models.Song;
import services.models.Playlist;

import java.io.File;
import java.util.*;

/**
 * Handles all playlist-related operations such as creation, editing, deletion, and syncing.
 * Supports both local storage and cloud synchronization.
 */
public class PlaylistManager {

    private final LocalStorageManager localStorageManager;
    private final CloudSyncService cloudSyncService;
    private final Logger logger;
    private final Map<String, Playlist> playlists;

    /**
     * Constructor initializes the playlist manager.
     */
    public PlaylistManager() {
        this.localStorageManager = new LocalStorageManager("playlists/");
        this.cloudSyncService = new CloudSyncService();
        this.logger = new Logger();
        this.playlists = new HashMap<>();

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
            logger.logWarning("Playlist with name '" + name + "' already exists.");
            return playlists.get(name);
        }

        Playlist playlist = new Playlist(name);
        playlists.put(name, playlist);
        logger.logInfo("Playlist created: " + name);
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
            logger.logError("Playlist '" + name + "' not found.");
            return false;
        }

        playlists.remove(name);
        localStorageManager.deletePlaylist(name);
        cloudSyncService.deletePlaylist(name);
        logger.logInfo("Playlist deleted: " + name);
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
            logger.logError("Playlist '" + playlistName + "' not found.");
            return false;
        }

        if (playlist.addSong(song)) {
            savePlaylist(playlist);
            logger.logInfo("Song added to playlist: " + song.getTitle() + " → " + playlistName);
            return true;
        } else {
            logger.logWarning("Song already exists in playlist: " + song.getTitle());
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
            logger.logError("Playlist '" + playlistName + "' not found.");
            return false;
        }

        if (playlist.removeSong(song)) {
            savePlaylist(playlist);
            logger.logInfo("Song removed from playlist: " + song.getTitle() + " ← " + playlistName);
            return true;
        } else {
            logger.logWarning("Song not found in playlist: " + song.getTitle());
            return false;
        }
    }

    /**
     * Fetches all available playlists.
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
        logger.logInfo("Loaded " + playlists.size() + " playlists from storage.");
    }
}
