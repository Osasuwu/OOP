package services.sync;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;  // Make sure to import Playlist for syncPlaylist
import java.nio.file.WatchService;
import java.util.List;

import models.Playlist;
import services.network.CloudSyncService;
import services.storage.LocalStorageManager;
import utils.Logger;

/**
 * Handles offline mode by tracking local file changes and syncing data when online.
 * Ensures a seamless user experience by maintaining consistency between local and cloud storage.
 */
public class OfflineSyncManager {

    private final LocalStorageManager localStorageManager;
    private final CloudSyncService cloudSyncService;
    private final Logger logger;
    private final Path watchDirectory;

    /**
     * Constructor initializes required components.
     */
    public OfflineSyncManager(String localMusicFolder) {
        this.localStorageManager = new LocalStorageManager(localMusicFolder);
        this.cloudSyncService = new CloudSyncService();
        this.logger = Logger.getInstance();
        this.watchDirectory = Paths.get(localMusicFolder);
    }

    /**
     * Monitors local music folder for changes (added, removed, or modified files).
     */
    public void startFileWatcher() {
        logger.info("Starting offline file watcher for: " + watchDirectory);

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchDirectory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    handleFileChangeEvent(event);
                }
                key.reset();
            }
        } catch (Exception e) {
            logger.error("Error in file watcher: " + e.getMessage());
        }
    }

    /**
     * Handles file system events and updates local database accordingly.
     *
     * @param event WatchEvent triggered by file changes.
     */
    private void handleFileChangeEvent(WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();
        Path changedFile = watchDirectory.resolve((Path) event.context());

        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            logger.info("New file added: " + changedFile);
            localStorageManager.addNewFile(changedFile.toFile());
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            logger.info("File deleted: " + changedFile);
            localStorageManager.removeFile(changedFile.toFile());
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            logger.info("File modified: " + changedFile);
            localStorageManager.updateFileMetadata(changedFile.toFile());
        }
    }

    /**
     * Synchronizes offline changes with the cloud when internet is available.
     */
    public void syncWithCloud() {
        logger.info("Checking for offline changes to sync...");

        List<File> newFiles = localStorageManager.getNewFiles();
        List<File> deletedFiles = localStorageManager.getDeletedFiles();
        List<File> modifiedFiles = localStorageManager.getModifiedFiles();

        if (newFiles.isEmpty() && deletedFiles.isEmpty() && modifiedFiles.isEmpty()) {
            logger.info("No offline changes detected. Sync not required.");
            return;
        }

        logger.info("Syncing new files...");
        for (File file : newFiles) {
            if (file != null) {  // It’s good practice to check for null
                cloudSyncService.uploadFile(file);
            }
        }

        logger.info("Syncing deleted files...");
        for (File file : deletedFiles) {
            if (file != null) {
                cloudSyncService.deleteFile(file);
            }
        }

        logger.info("Syncing modified files...");
        for (File file : modifiedFiles) {
            if (file != null) {
                cloudSyncService.updateFile(file);
            }
        }
    }

    /**
     * Fetches all locally stored songs.
     *
     * @return A list of song files stored offline.
     */
    public List<File> getLocalSongs() {
        return localStorageManager.getAllFiles();
    }

    /**
     * Checks if an internet connection is available.
     *
     * @return True if online, false if offline.
     */
    public boolean isOnline() {
        return cloudSyncService.isConnected();
    }


    /**
     * Performs automatic synchronization if online.
     */
    public void autoSync() {
        if (isOnline()) {
            logger.info("Internet connection detected. Initiating automatic sync...");
            syncWithCloud();
        } else {
            logger.warning("Offline mode detected. Changes will sync when online.");
        }
    }
    
    /**
     * Synchronizes the given playlist with the cloud.
     * This method is added to resolve the syncPlaylist error in Generator.
     *
     * @param playlist The playlist to sync.
     */
    public void syncPlaylist(Playlist playlist) {
        logger.info("Syncing playlist: " + playlist.getName());
        if (cloudSyncService.isConnected()) {
            cloudSyncService.syncPlaylist(playlist);
            logger.info("Playlist '" + playlist.getName() + "' synced to the cloud.");
        } else {
            logger.warning("Offline mode detected. Playlist '" + playlist.getName() + "' will be synced when online.");
        }
    }
}