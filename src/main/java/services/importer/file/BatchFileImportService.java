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
    
    public BatchFileImportService() {
        this.importFactory = new FileImportAdapterFactory();
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
        List<ImportResult> results = new ArrayList<>();
        
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directoryPath)) {
            for (Path filePath : directoryStream) {
                if (Files.isRegularFile(filePath)) {
                    try {
                        // Try to find a compatible adapter
                        FileImportAdapter adapter = importFactory.getAdapter(filePath);
                        
                        // Import data from file
                        UserMusicData data = adapter.importFromFile(filePath);
                        
                        // Merge with combined data
                        combinedData.merge(data);
                        
                        // Track result
                        results.add(new ImportResult(filePath.getFileName().toString(), true));
                    } catch (ImportException e) {
                        results.add(new ImportResult(filePath.getFileName().toString(), false, e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            throw new ImportException("Error reading directory: " + e.getMessage(), e);
        }
        
        // Log results
        logResults(results);
        
        if (combinedData.isEmpty()) {
            throw new ImportException("No data could be imported from any files in directory");
        }
        
        return combinedData;
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
     * Helper class for tracking import results
     */
    private static class ImportResult {
        private final String filename;
        private final boolean success;
        private final String errorMessage;
        
        public ImportResult(String filename, boolean success) {
            this(filename, success, null);
        }
        
        public ImportResult(String filename, boolean success, String errorMessage) {
            this.filename = filename;
            this.success = success;
            this.errorMessage = errorMessage;
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
    }
}
