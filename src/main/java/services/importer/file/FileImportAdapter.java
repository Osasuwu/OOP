package services.importer.file;

import models.UserMusicData;
import java.nio.file.Path;
import services.importer.ImportException;

/**
 * Interface for file import adapters supporting different file formats.
 */
public interface FileImportAdapter {
    /**
     * Imports user music data from a file path
     * @param filePath The path to the file to import
     * @return The imported user music data
     */
    UserMusicData importFromFile(Path filePath) throws ImportException;
    
    /**
     * Checks if this adapter can handle the given file based on its format
     * @param filePath The path to the file to check
     * @return true if this adapter can handle the file format
     */
    boolean canHandle(Path filePath);
}
