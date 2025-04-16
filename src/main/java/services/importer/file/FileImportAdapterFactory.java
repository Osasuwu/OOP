package services.importer.file;

import java.nio.file.Path;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;

/**
 * Factory that creates the appropriate FileImportAdapter based on file type
 */
public class FileImportAdapterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImportAdapterFactory.class);
    private final List<FileImportAdapter> adapters;
    
    public FileImportAdapterFactory() {
        this.adapters = new ArrayList<>();
        
        // Register adapters in order of priority
        adapters.add(new CsvFileImportAdapter());
        adapters.add(new JsonFileImportAdapter());
        adapters.add(new XmlFileImportAdapter());
    }
    
    /**
     * Gets the appropriate adapter for the given file path
     * @param filePath The path of the file to import
     * @return A suitable adapter for the file format
     * @throws ImportException If no suitable adapter is found
     */
    public FileImportAdapter getAdapter(Path filePath) throws ImportException {
        if (filePath == null) {
            throw new ImportException("File path cannot be null");
        }
        
        for (FileImportAdapter adapter : adapters) {
            if (adapter.canHandle(filePath)) {
                LOGGER.info("Using {} for file {}", adapter.getClass().getSimpleName(), filePath.getFileName());
                return adapter;
            }
        }
        
        throw new ImportException("No suitable adapter found for file: " + filePath.getFileName());
    }
}
