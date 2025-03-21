package services;

import models.UserMusicData;
import services.importer.*;
import java.nio.file.*;
import java.util.*;

/**
 * Service for importing user music data from various sources
 */
public class DataImportService {
    private final DataImportFactory importFactory;
    private final BatchImportService batchImportService;
    
    public DataImportService() {
        this.importFactory = new DataImportFactory();
        this.batchImportService = new BatchImportService();
    }
    
    /**
     * Import data from a file with automatic format detection
     * @param filePath Path to the file to import
     * @return The imported user music data
     */
    public UserMusicData importFromFile(String filePath) throws ImportException {
        Path path = Paths.get(filePath);
        return importFromFile(path);
    }
    
    /**
     * Import data from a file with automatic format detection
     * @param path Path object representing the file to import
     * @return The imported user music data
     */
    public UserMusicData importFromFile(Path path) throws ImportException {
        DataImportAdapter adapter = importFactory.getAdapter(path);
        return adapter.importFromFile(path);
    }
    
    /**
     * Import data from a streaming service
     * @param serviceName Name of the streaming service
     * @param accessToken Authentication token for the service
     * @param userId User ID for the service
     * @return The imported user music data
     */
    public UserMusicData importFromStreamingService(String serviceName, String accessToken, String userId) throws ImportException {
        StreamingServiceImportAdapter adapter = importFactory.getStreamingAdapter(serviceName);
        return adapter.importFromService(accessToken, userId);
    }
    
    /**
     * Import data from multiple files
     * @param filePaths List of paths to import
     * @return Combined user music data from all sources
     */
    public UserMusicData importFromMultipleFiles(List<String> filePaths) throws ImportException {
        List<Path> paths = new ArrayList<>();
        for (String file : filePaths) {
            paths.add(Paths.get(file));
        }
        return batchImportService.importFromMultipleFiles(paths);
    }
    
    /**
     * Import all data files from a directory
     * @param directoryPath Path to the directory containing files to import
     * @return Combined user music data from all compatible files in the directory
     */
    public UserMusicData importFromDirectory(String directoryPath) throws ImportException {
        Path path = Paths.get(directoryPath);
        return batchImportService.importFromDirectory(path);
    }
    
    /**
     * Get list of supported streaming services
     * @return List of names of supported streaming services
     */
    public List<String> getSupportedStreamingServices() {
        List<String> services = new ArrayList<>();
        for (StreamingServiceImportAdapter adapter : importFactory.getAllStreamingAdapters()) {
            services.add(adapter.getServiceName());
        }
        return services;
    }
}
