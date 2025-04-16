package services.importer.file;

import models.UserMusicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for batch importing multiple files from a directory
 */
public class BatchFileImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchFileImportService.class);
    private final FileImportAdapterFactory importFactory;
    private final ExecutorService executorService;
    
    public BatchFileImportService() {
        this.importFactory = new FileImportAdapterFactory();
        // Create thread pool with number of processors available
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Import all compatible files from a directory
     * @param directoryPath The directory containing files to import
     * @return Combined UserMusicData from all successfully imported files
     * @throws ImportException If the directory cannot be read or no files can be imported
     */
    public UserMusicData importFromDirectory(Path directoryPath) throws ImportException {
        if (!Files.isDirectory(directoryPath)) {
            throw new ImportException("Not a directory: " + directoryPath);
        }
        
        UserMusicData combinedData = new UserMusicData();
        List<Future<ImportResult>> futures = new ArrayList<>();
        
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directoryPath)) {
            for (Path filePath : directoryStream) {
                if (Files.isRegularFile(filePath)) {
                    // Submit each file import task to the executor
                    futures.add(executorService.submit(() -> importFile(filePath)));
                }
            }
        } catch (IOException e) {
            throw new ImportException("Error reading directory: " + e.getMessage(), e);
        }
        
        // Collect results and combine data
        List<ImportResult> results = new ArrayList<>();
        for (Future<ImportResult> future : futures) {
            try {
                ImportResult result = future.get();
                results.add(result);
                
                if (result.isSuccess() && result.getData() != null) {
                    synchronized (combinedData) {
                        combinedData.merge(result.getData());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Thread interrupted while importing files", e);
            } catch (ExecutionException e) {
                LOGGER.error("Error during file import", e);
            }
        }
        
        // Log results
        logResults(results);
        
        if (combinedData.isEmpty()) {
            throw new ImportException("No data could be imported from any files in directory");
        }
        
        return combinedData;
    }
    
    private ImportResult importFile(Path filePath) {
        try {
            // Try to find a compatible adapter
            FileImportAdapter adapter = importFactory.getAdapter(filePath);
            
            // Import data from file
            UserMusicData data = adapter.importFromFile(filePath);
            
            // Return successful result with data
            return new ImportResult(filePath.getFileName().toString(), true, data);
        } catch (ImportException e) {
            return new ImportResult(filePath.getFileName().toString(), false, e.getMessage());
        }
    }
    
    private void logResults(List<ImportResult> results) {
        long successful = results.stream().filter(ImportResult::isSuccess).count();
        LOGGER.info("Import results: {} successful, {} failed", successful, results.size() - successful);
        
        for (ImportResult result : results) {
            if (result.isSuccess()) {
                LOGGER.info("Successfully imported: {}", result.getFilename());
            } else {
                LOGGER.warn("Failed to import: {} - {}", result.getFilename(), result.getErrorMessage());
            }
        }
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Helper class for tracking import results
     */
    private static class ImportResult {
        private final String filename;
        private final boolean success;
        private final String errorMessage;
        private final UserMusicData data;
        
        public ImportResult(String filename, boolean success, String errorMessage) {
            this(filename, success, errorMessage, null);
        }
        
        public ImportResult(String filename, boolean success, UserMusicData data) {
            this(filename, success, (String)null, data);
        }
        
        public ImportResult(String filename, boolean success, String errorMessage, UserMusicData data) {
            this.filename = filename;
            this.success = success;
            this.errorMessage = errorMessage;
            this.data = data;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public UserMusicData getData() {
            return data;
        }
    }
}
