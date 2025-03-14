package interfaces;

import models.UserMusicData;
import java.io.InputStream;
import java.nio.file.Path;
import services.importer.ImportException;

/**
 * Interface for data import adapters supporting different data formats.
 */
public interface DataImportAdapter {
    /**
     * Imports user music data from a file path
     * @param filePath The path to the file to import
     * @return The imported user music data
     */
    UserMusicData importFromFile(Path filePath) throws ImportException;
    
    /**
     * Imports user music data from an input stream
     * @param inputStream The input stream to read data from
     * @param sourceName Optional name of the source for logging
     * @return The imported user music data
     */
    UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException;
    
    /**
     * Checks if this adapter can handle the given file based on its format
     * @param filePath The path to the file to check
     * @return true if this adapter can handle the file format
     */
    boolean canHandle(Path filePath);
}
