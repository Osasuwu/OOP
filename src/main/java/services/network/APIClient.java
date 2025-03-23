package services.network;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * ApiClient is a utility class for interacting with external music APIs.
 */
public class APIClient {

    /**
     * Fetches music data from an external API.
     *
     * @param query The search query.
     * @return A list of music data matching the query.
     * @throws IOException If an error occurs during the API call.
     */
    public List<String> fetchMusicData(String query) throws IOException {
        // Simulate fetching data from an API
        return List.of("Song1", "Song2", "Song3"); // Mock data
    }

    /**
     * Fetches album details from an external API.
     *
     * @param albumId The album's unique identifier.
     * @return Album details as a string wrapped in an Optional.
     * @throws IOException If an error occurs during the API call.
     */
    public Optional<String> fetchAlbumDetails(String albumId) throws IOException {
        // Simulate fetching album details
        return Optional.of("Album Details for ID: " + albumId); // Mock data
    }

    /**
     * Fetches artist details from an external API.
     *
     * @param artistName The artist's name.
     * @return Artist details as a string wrapped in an Optional.
     * @throws IOException If an error occurs during the API call.
     */
    public Optional<String> fetchArtistDetails(String artistName) throws IOException {
        // Simulate fetching artist details
        return Optional.of("Artist Details for: " + artistName); // Mock data
    }
}