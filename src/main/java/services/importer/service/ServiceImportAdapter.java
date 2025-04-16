package services.importer.service;

import models.UserMusicData;
import java.util.Map;
import services.importer.ImportException;

/**
 * Interface for service import adapters supporting different streaming services.
 */
public interface ServiceImportAdapter {
    /**
     * Imports user music data from a streaming service
     * @param credentials Map of credentials needed for authentication (e.g., access token, user ID)
     * @return The imported user music data
     */
    UserMusicData importFromService(Map<String, String> credentials) throws ImportException;
    
    /**
     * Gets the name of the service this adapter handles
     * @return The service name (lowercase)
     */
    String getServiceName();
}
