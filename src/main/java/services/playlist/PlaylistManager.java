package services.playlist;

import utils.Logger;
import services.storage.LocalStorageManager; // Make sure this is the correct package
import services.network.CloudSyncService;
import models.Playlist;
import models.Song;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        this.logger = Logger.getInstance();
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
            logger.warning("Playlist with name '" + name + "' already exists.");
            return playlists.get(name);
        }

        Playlist playlist = new Playlist(name);
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

        playlists.remove(name);
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

        // Using the songs list directly instead of a missing addSong() method
        List<Song> songList = playlist.getSongs();
        if (!songList.contains(song)) {
            songList.add(song);
            savePlaylist(playlist);
            logger.info("Song added to playlist: " + song.getTitle() + " → " + playlistName);
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

        // Instead of calling playlist.removeSong(song), use the song list directly.
        List<Song> songList = playlist.getSongs();
        if (songList.contains(song)) {
            songList.remove(song);
            savePlaylist(playlist);
            logger.info("Song removed from playlist: " + song.getTitle() + " ← " + playlistName);
            return true;
        } else {
            logger.warning("Song not found in playlist: " + song.getTitle());
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
        logger.info("Loaded " + playlists.size() + " playlists from storage.");
    }
}