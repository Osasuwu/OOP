package services.sync;

import services.utils.Logger;
import services.data.LocalStorageManager;
import services.network.CloudSyncService;

import java.io.File;
import java.nio.file.*;
import java.util.*;

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
        this.logger = new Logger();
        this.watchDirectory = Paths.get(localMusicFolder);
    }

    /**
     * Monitors local music folder for changes (added, removed, or modified files).
     */
    public void startFileWatcher() {
        logger.logInfo("Starting offline file watcher for: " + watchDirectory);

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
            logger.logError("Error in file watcher: " + e.getMessage());
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
            logger.logInfo("New file added: " + changedFile);
            localStorageManager.addNewFile(changedFile.toFile());
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            logger.logInfo("File deleted: " + changedFile);
            localStorageManager.removeFile(changedFile.toFile());
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            logger.logInfo("File modified: " + changedFile);
            localStorageManager.updateFileMetadata(changedFile.toFile());
        }
    }

    /**
     * Synchronizes offline changes with the cloud when internet is available.
     */
    public void syncWithCloud() {
        logger.logInfo("Checking for offline changes to sync...");

        List<File> newFiles = localStorageManager.getNewFiles();
        List<File> deletedFiles = localStorageManager.getDeletedFiles();
        List<File> modifiedFiles = localStorageManager.getModifiedFiles();

        if (newFiles.isEmpty() && deletedFiles.isEmpty() && modifiedFiles.isEmpty()) {
            logger.logInfo("No offline changes detected. Sync not required.");
            return;
        }

        logger.logInfo("Syncing new files...");
        for (File file : newFiles) {
            cloudSyncService.uploadFile(file);
        }

        logger.logInfo("Syncing deleted files...");
        for (File file : deletedFiles) {
            cloudSyncService.deleteFile(file);
        }

        logger.logInfo("Syncing modified files...");
        for (File file : modifiedFiles) {
            cloudSyncService.updateFile(file);
        }

        logger.logInfo("Offline sync completed.");
        localStorageManager.clearSyncHistory();
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
            logger.logInfo("Internet connection detected. Initiating automatic sync...");
            syncWithCloud();
        } else {
            logger.logWarning("Offline mode detected. Changes will sync when online.");
        }
    }
}
