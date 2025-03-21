package services.importer;

import models.UserMusicData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;

/**
 * Service for handling batch imports from multiple sources
 */
public class BatchImportService {
    private final DataImportFactory importFactory;
    
    public BatchImportService() {
        this.importFactory = new DataImportFactory();
    }
    
    /**
     * Import data from multiple files in a directory
     * @param directoryPath The directory containing files to import
     * @return Combined UserMusicData from all successfully imported files
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
                        DataImportAdapter adapter = importFactory.getAdapter(filePath);
                        UserMusicData data = adapter.importFromFile(filePath);
                        combinedData.merge(data);
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
        for (ImportResult result : results) {
            if (result.isSuccess()) {
                System.out.println("Successfully imported: " + result.getFilename());
            } else {
                System.out.println("Failed to import: " + result.getFilename() + " - " + result.getErrorMessage());
            }
        }
        
        return combinedData;
    }
    
    /**
     * Import data from multiple files in parallel
     * @param filePaths List of file paths to import
     * @return Combined UserMusicData from all successfully imported files
     */
    public UserMusicData importFromMultipleFiles(List<Path> filePaths) throws ImportException {
        ExecutorService executorService = Executors.newFixedThreadPool(
            Math.min(filePaths.size(), Runtime.getRuntime().availableProcessors())
        );
        
        List<Future<ImportTask>> futures = new ArrayList<>();
        
        // Submit import tasks
        for (Path filePath : filePaths) {
            ImportTask task = new ImportTask(filePath, importFactory);
            futures.add(executorService.submit(() -> task));
        }
        
        // Collect results
        UserMusicData combinedData = new UserMusicData();
        List<ImportResult> results = new ArrayList<>();
        
        for (Future<ImportTask> future : futures) {
            try {
                ImportTask task = future.get();
                if (task.isSuccessful()) {
                    combinedData.merge(task.getData());
                }
                results.add(task.getResult());
            } catch (InterruptedException | ExecutionException e) {
                results.add(new ImportResult("Unknown", false, e.getMessage()));
            }
        }
        
        executorService.shutdown();
        
        // Log results
        for (ImportResult result : results) {
            if (result.isSuccess()) {
                System.out.println("Successfully imported: " + result.getFilename());
            } else {
                System.out.println("Failed to import: " + result.getFilename() + " - " + result.getErrorMessage());
            }
        }
        
        return combinedData;
    }
    
    // Helper class for tracking import task results
    private static class ImportTask {
        private final Path filePath;
        private final DataImportFactory importFactory;
        private UserMusicData data;
        private ImportResult result;
        
        public ImportTask(Path filePath, DataImportFactory importFactory) {
            this.filePath = filePath;
            this.importFactory = importFactory;
            this.data = null;
        }
        
        public ImportTask call() {
            String filename = filePath.getFileName().toString();
            
            try {
                DataImportAdapter adapter = importFactory.getAdapter(filePath);
                data = adapter.importFromFile(filePath);
                result = new ImportResult(filename, true);
            } catch (ImportException e) {
                result = new ImportResult(filename, false, e.getMessage());
            }
            
            return this;
        }
        
        public boolean isSuccessful() {
            return data != null;
        }
        
        public UserMusicData getData() {
            return data;
        }
        
        public ImportResult getResult() {
            return result;
        }
    }
    
    // Helper class for tracking import results
    public static class ImportResult {
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
