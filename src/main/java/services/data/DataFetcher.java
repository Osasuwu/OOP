package services.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import services.network.APIClient;
import services.storage.LocalStorageManager;
import utils.Logger;


/**
 * Fetches music-related data from APIs, local storage, or databases.
 * Implements caching for efficiency and reduces redundant network calls.
 */
public class DataFetcher {

    private final APIClient apiClient;
    private final LocalStorageManager localStorageManager;
    private final Logger logger = Logger.getInstance(); // Initialize logger here

    /**
     * Constructor initializes dependencies.
     */
    public DataFetcher() {
        this.apiClient = new APIClient();
        this.localStorageManager = new LocalStorageManager("music_cache.json");
    }

    /**
     * Fetches music data, prioritizing local cache before making API calls.
     *
     * @param query The search keyword (e.g., song name, artist, genre).
     * @return List of music data matching the query.
     */
    public List<String> fetchMusicData(String query) {
        logger.info("Fetching music data for query: " + query);

        // First, check local cache: getCachedMusic returns an Optional<Map<String,String>>
        Optional<Map<String, String>> cachedDataMap = localStorageManager.getCachedMusic(query);
        // Convert the Optional<Map<String,String>> into an Optional<List<String>>
        Optional<List<String>> cachedData = cachedDataMap.map(map -> new ArrayList<>(map.values()));
        if (cachedData.isPresent()) {
            logger.info("Data found in cache for query: " + query);
            return cachedData.get();
        }

        // If not found in cache, fetch from API
        try {
            List<String> musicData = apiClient.fetchMusicData(query);
            if (!musicData.isEmpty()) {
                // Convert List<String> to Map<String, String>
                Map<String, String> musicDataMap = new HashMap<>();
                for (String data : musicData) {
                    // Here, using the song data as key and the query as value.
                    // You may adjust this logic as needed.
                    musicDataMap.put(data, query);
                }

                // Cache the transformed map
                localStorageManager.cacheMusic(query, musicDataMap);
                logger.info("Music data retrieved from API and cached for query: " + query);
            } else {
                logger.warning("No music data found for query: " + query);
            }
            return musicData;
        } catch (IOException e) {
            logger.error("Error fetching music data: " + e.getMessage());
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
        logger.info("Fetching album details for ID: " + albumId);

        Optional<String> cachedAlbum = localStorageManager.getCachedAlbum(albumId);
        if (cachedAlbum.isPresent()) {
            logger.info("Album details found in cache: " + albumId);
            return cachedAlbum;
        }

        try {
            Optional<String> albumDetails = apiClient.fetchAlbumDetails(albumId);
            albumDetails.ifPresent(details -> localStorageManager.cacheAlbum(albumId, details));
            return albumDetails;
        } catch (IOException e) {
            logger.error("Error fetching album details: " + e.getMessage());
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
        logger.info("Fetching artist details for: " + artistName);

        Optional<String> cachedArtist = localStorageManager.getCachedArtist(artistName);
        if (cachedArtist.isPresent()) {
            logger.info("Artist details found in cache for: " + artistName);
            return cachedArtist;
        }

        try {
            Optional<String> artistDetails = apiClient.fetchArtistDetails(artistName);
            artistDetails.ifPresent(details -> localStorageManager.cacheArtist(artistName, details));
            return artistDetails;
        } catch (IOException e) {
            logger.error("Error fetching artist details: " + e.getMessage());
            return Optional.empty();
        }
    }
    /**
 * Fetches songs as a list of maps with song details.
 * This method calls fetchMusicData("") (using an empty query) and converts
 * each returned String (assumed to be a song title) into a Map with default values.
 *
 * @return A list of songs represented as maps.
 */

public List<Map<String, Object>> fetchSongs() {
    List<String> songTitles = fetchMusicData("");
    List<Map<String, Object>> songs = new ArrayList<>();
    
    for (String title : songTitles) {
        Map<String, Object> song = new HashMap<>();
        song.put("title", title);
        // Set default/dummy values for keys used in FilterManager predicates.
        song.put("genre", "unknown");
        song.put("artist", "unknown");
        song.put("popularity", 0);
        song.put("duration", 0);
        song.put("year", 0);
        songs.add(song);
    }
    return songs;
}

    /**
     * Clears all cached music data.
     */
    public void clearCache() {
        localStorageManager.clearCache();
        logger.info("Music data cache cleared.");
    }
}

/**
 * Fetches songs as a list of maps with song details.
 * This method calls fetchMusicData("") (using an empty query) and converts
 * each returned String (assumed to be a song title) into a Map with default values.
 *
 * @return A list of songs represented as maps.
 */