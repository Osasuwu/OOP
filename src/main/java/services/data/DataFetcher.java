package services.data;

import services.utils.Logger;
import services.network.ApiClient;
import services.storage.LocalStorageManager;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Fetches music-related data from APIs, local storage, or databases.
 * Implements caching for efficiency and reduces redundant network calls.
 */
public class DataFetcher {

    private final ApiClient apiClient;
    private final LocalStorageManager localStorageManager;
    private final Logger logger;

    /**
     * Constructor initializes dependencies.
     */
    public DataFetcher() {
        this.apiClient = new ApiClient();
        this.localStorageManager = new LocalStorageManager("music_cache.json");
        this.logger = new Logger();
    }

    /**
     * Fetches music data, prioritizing local cache before making API calls.
     *
     * @param query The search keyword (e.g., song name, artist, genre).
     * @return List of music data matching the query.
     */
    public List<String> fetchMusicData(String query) {
        logger.logInfo("Fetching music data for query: " + query);

        // First, check local cache
        Optional<List<String>> cachedData = localStorageManager.getCachedMusic(query);
        if (cachedData.isPresent()) {
            logger.logInfo("Data found in cache for query: " + query);
            return cachedData.get();
        }

        // If not found in cache, fetch from API
        try {
            List<String> musicData = apiClient.fetchMusicData(query);
            if (!musicData.isEmpty()) {
                localStorageManager.cacheMusic(query, musicData);
                logger.logInfo("Music data retrieved from API and cached for query: " + query);
            } else {
                logger.logWarning("No music data found for query: " + query);
            }
            return musicData;
        } catch (IOException e) {
            logger.logError("Error fetching music data: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches album details from local cache or API.
     *
     * @param albumId The album's unique identifier.
     * @return Album details as a string, or an empty Optional if not found.
     */
    public Optional<String> fetchAlbumDetails(String albumId) {
        logger.logInfo("Fetching album details for ID: " + albumId);

        Optional<String> cachedAlbum = localStorageManager.getCachedAlbum(albumId);
        if (cachedAlbum.isPresent()) {
            logger.logInfo("Album details found in cache: " + albumId);
            return cachedAlbum;
        }

        try {
            Optional<String> albumDetails = apiClient.fetchAlbumDetails(albumId);
            albumDetails.ifPresent(details -> localStorageManager.cacheAlbum(albumId, details));
            return albumDetails;
        } catch (IOException e) {
            logger.logError("Error fetching album details: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches artist information, prioritizing cache before making API requests.
     *
     * @param artistName The artist's name.
     * @return Artist details as a string, or an empty Optional if not found.
     */
    public Optional<String> fetchArtistDetails(String artistName) {
        logger.logInfo("Fetching artist details for: " + artistName);

        Optional<String> cachedArtist = localStorageManager.getCachedArtist(artistName);
        if (cachedArtist.isPresent()) {
            logger.logInfo("Artist details found in cache for: " + artistName);
            return cachedArtist;
        }

        try {
            Optional<String> artistDetails = apiClient.fetchArtistDetails(artistName);
            artistDetails.ifPresent(details -> localStorageManager.cacheArtist(artistName, details));
            return artistDetails;
        } catch (IOException e) {
            logger.logError("Error fetching artist details: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clears all cached music data.
     */
    public void clearCache() {
        localStorageManager.clearCache();
        logger.logInfo("Music data cache cleared.");
    }
}
