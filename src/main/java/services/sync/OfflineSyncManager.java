package services.sync;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    
    // [Enhancement 2] Use a volatile flag for graceful shutdown.
    private volatile boolean running = true;

    // [Enhancement 3] Blocking queue to buffer file events.
    private final BlockingQueue<Path> fileEventQueue = new LinkedBlockingQueue<>();

    // Maximum retries for cloud sync operations.
    private static final int MAX_RETRIES = 3;

    /**
     * Constructor initializes required components.
     */
    public OfflineSyncManager(String localMusicFolder) {
        this.localStorageManager = new LocalStorageManager(localMusicFolder);
        this.cloudSyncService = new CloudSyncService();
        this.logger = Logger.getInstance();
        this.watchDirectory = Paths.get(localMusicFolder);
    }
    
    // ------------------ Enhancement 1: Separate Thread for File Watching ------------------

    /**
     * Starts the file watcher asynchronously on a separate daemon thread.
     */
    public void startFileWatcherAsync() {
        Thread watcherThread = new Thread(this::startFileWatcher);
        watcherThread.setDaemon(true); // Ensures the thread doesn't block application shutdown.
        watcherThread.start();
        startEventProcessor(); // Also launch the background event processor.
    }
    
    // ------------------ Enhancement 2: Graceful Shutdown ------------------

    /**
     * Stops the file watcher gracefully by setting the running flag to false.
     */
    public void stopFileWatcher() {
        running = false;
        logger.info("File watcher has been instructed to stop.");
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
            
            // Use the running flag for a graceful shutdown.
            while (running) {
                WatchKey key = watchService.poll(); // Use poll() to avoid blocking indefinitely.
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        handleFileChangeEvent(event);
                    }
                    key.reset();
                }
                // Small sleep to prevent busy-waiting.
                Thread.sleep(200);
            }
            logger.info("File watcher stopped gracefully.");
        } catch (Exception e) {
            logger.error("Error in file watcher: " + e.getMessage());
        }
    }
    
    // ------------------ Enhancement 3: Queue-based File Event Processing ------------------

    /**
     * Adds file events to the queue in handleFileChangeEvent instead of processing immediately.
     */
    private void handleFileChangeEvent(WatchEvent<?> event) {
        Path changedFile = watchDirectory.resolve((Path) event.context());
        // Simply add the changed file to the queue.
        fileEventQueue.add(changedFile);
    }
    
    /**
     * Starts a background thread that processes file events from the queue in batches.
     */
    private void startEventProcessor() {
        Thread processorThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(2000); // Process events every 2 seconds.
                    processFileEvents();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        processorThread.setDaemon(true);
        processorThread.start();
    }
    
    /**
     * Processes file events buffered in the queue.
     */
    private void processFileEvents() {
        while (!fileEventQueue.isEmpty()) {
            Path file = fileEventQueue.poll();
            if (file != null) {
                logger.info("Processing file change: " + file);
                localStorageManager.updateFileMetadata(file.toFile());
            }
        }
    }
    
    // ------------------ Enhancement 4 & 5: Parallel Cloud Sync with Retry Mechanism ------------------

    /**
     * Helper method that performs a cloud sync task with a retry mechanism.
     *
     * @param syncTask Runnable that performs the sync.
     * @param taskName Descriptive name for logging.
     */
    private void syncWithRetry(Runnable syncTask, String taskName) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                syncTask.run();
                return;
            } catch (Exception e) {
                attempts++;
                logger.error(taskName + " failed. Retrying (" + attempts + "/" + MAX_RETRIES + ")...");
                try {
                    Thread.sleep(1000 * attempts); // Exponential backoff approximation.
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        logger.error(taskName + " failed after " + MAX_RETRIES + " attempts.");
    }
    
    /**
     * Synchronizes offline changes with the cloud when internet is available.
     * Uses parallel streams and retry mechanism for efficiency.
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
        newFiles.parallelStream().forEach(file -> {
            if (file != null) {
                syncWithRetry(() -> cloudSyncService.uploadFile(file), "Upload " + file.getName());
            }
        });
        
        logger.info("Syncing deleted files...");
        deletedFiles.parallelStream().forEach(file -> {
            if (file != null) {
                syncWithRetry(() -> cloudSyncService.deleteFile(file), "Delete " + file.getName());
            }
        });
        
        logger.info("Syncing modified files...");
        modifiedFiles.parallelStream().forEach(file -> {
            if (file != null) {
                syncWithRetry(() -> cloudSyncService.updateFile(file), "Update " + file.getName());
            }
        });
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