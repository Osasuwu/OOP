package services.importer;

import java.nio.file.Path;
import java.util.*;
import interfaces.*;

/**
 * Factory that creates the appropriate DataImportAdapter based on file type
 */
public class DataImportFactory {
    private final List<DataImportAdapter> availableAdapters;
    
    public DataImportFactory() {
        // Initialize available adapters
        availableAdapters = new ArrayList<>();
        availableAdapters.add(new CsvDataImportAdapter());
        availableAdapters.add(new JsonDataImportAdapter());
        availableAdapters.add(new XmlDataImportAdapter());
        // Add more adapters as they become available
    }
    
    /**
     * Get the appropriate adapter for the given file path based on file extension
     * @param filePath The path to the file to import
     * @return The appropriate DataImportAdapter instance
     * @throws ImportException if no suitable adapter is found
     */
    public DataImportAdapter getAdapter(Path filePath) throws ImportException {
        for (DataImportAdapter adapter : availableAdapters) {
            if (adapter.canHandle(filePath)) {
                return adapter;
            }
        }
        
        throw new ImportException("No suitable adapter found for file: " + filePath.getFileName());
    }
    
    /**
     * Get a streaming service adapter by name
     * @param serviceName The name of the streaming service
     * @return The appropriate streaming service adapter
     * @throws ImportException if no adapter for the given service is found
     */
    public StreamingServiceImportAdapter getStreamingAdapter(String serviceName) throws ImportException {
        switch (serviceName.toLowerCase()) {
            case "spotify":
                return new SpotifyImportAdapter();
            // Add more streaming services as needed
            default:
                throw new ImportException("No adapter available for streaming service: " + serviceName);
        }
    }
    
    /**
     * Get all available streaming service adapters
     * @return List of all streaming service adapters
     */
    public List<StreamingServiceImportAdapter> getAllStreamingAdapters() {
        List<StreamingServiceImportAdapter> streamingAdapters = new ArrayList<>();
        streamingAdapters.add(new SpotifyImportAdapter());
        // Add more streaming services as they're implemented
        return streamingAdapters;
    }
}
