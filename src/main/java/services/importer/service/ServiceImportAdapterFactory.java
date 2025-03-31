package services.importer.service;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;

/**
 * Factory that creates the appropriate ServiceImportAdapter based on service type
 */
public class ServiceImportAdapterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImportAdapterFactory.class);
    private final Map<String, ServiceImportAdapter> adapters;
    
    public ServiceImportAdapterFactory() {
        this.adapters = new HashMap<>();
        
        // Register adapters by service name
        SpotifyServiceImportAdapter spotifyAdapter = new SpotifyServiceImportAdapter();
        adapters.put(spotifyAdapter.getServiceName(), spotifyAdapter);
        
        AppleMusicServiceImportAdapter appleMusicAdapter = new AppleMusicServiceImportAdapter();
        adapters.put(appleMusicAdapter.getServiceName(), appleMusicAdapter);
        
        YouTubeMusicServiceImportAdapter youtubeMusicAdapter = new YouTubeMusicServiceImportAdapter();
        adapters.put(youtubeMusicAdapter.getServiceName(), youtubeMusicAdapter);
    }
    
    /**
     * Gets the appropriate adapter for the given service name
     * @param serviceName Name of the streaming service (case-insensitive)
     * @return A suitable adapter for the service
     * @throws ImportException If no suitable adapter is found
     */
    public ServiceImportAdapter getAdapter(String serviceName) throws ImportException {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new ImportException("Service name cannot be null or empty");
        }
        
        String normalizedName = serviceName.trim().toLowerCase();
        
        ServiceImportAdapter adapter = adapters.get(normalizedName);
        if (adapter != null) {
            LOGGER.info("Using {} for service {}", adapter.getClass().getSimpleName(), serviceName);
            return adapter;
        }
        
        throw new ImportException("Unsupported service: " + serviceName);
    }
}
