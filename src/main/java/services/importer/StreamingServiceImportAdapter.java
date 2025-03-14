package services.importer;

import models.UserMusicData;
import models.Song;
import models.Artist;
import interfaces.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Base class for streaming service API import adapters
 */
public abstract class StreamingServiceImportAdapter implements DataImportAdapter {
    private final String serviceName;
    
    public StreamingServiceImportAdapter(String serviceName) {
        this.serviceName = serviceName;
    }
    
    /**
     * Import user music data from a service API using access tokens
     * @param accessToken The authentication token for the service
     * @param userId The user identifier for the service
     * @return The imported user music data
     */
    public abstract UserMusicData importFromService(String accessToken, String userId) throws ImportException;
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        throw new ImportException("Direct file import not supported for streaming service adapters");
    }
    
    @Override
    public UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        throw new ImportException("Stream import not supported for streaming service adapters");
    }
    
    @Override
    public boolean canHandle(Path filePath) {
        return false; // Service adapters don't handle files directly
    }
    
    public String getServiceName() {
        return serviceName;
    }
}
