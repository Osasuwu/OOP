package services.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import models.Song;
import services.data.DataFetcher;
import utils.Logger;  // Added import for Song

/**
 * Generates song recommendations based on user listening history, preferences, and popularity.
 * Utilizes filtering and scoring mechanisms to deliver highly accurate suggestions.
 */
public class RecommendationEngine {

    private final DataFetcher dataFetcher;
    private final FilterManager filterManager;
    private final Logger logger;

    /**
     * Constructor initializes required services.
     */
    public RecommendationEngine() {
        this.dataFetcher = new DataFetcher();
        this.filterManager = new FilterManager();
        this.logger = Logger.getInstance();
    }

    /**
     * Generates recommendations based on user listening history.
     *
     * @param userHistory A list of songs the user has previously listened to.
     * @param maxResults  The maximum number of recommendations to return.
     * @return A list of recommended songs.
     */
    public List<Map<String, Object>> generateRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.info("Generating personalized recommendations for user...");

        if (userHistory.isEmpty()) {
            logger.warning("User history is empty. Returning top trending songs instead.");
            return getTrendingSongs(maxResults);
        }

        Set<String> favoriteGenres = new HashSet<>();
        Set<String> favoriteArtists = new HashSet<>();
        Map<String, Integer> genreCount = userHistory.stream()
                .collect(Collectors.toMap(song -> (String) song.get("genre"), song -> 1, Integer::sum));
        Map<String, Integer> artistCount = userHistory.stream()
                .collect(Collectors.toMap(song -> (String) song.get("artist"), song -> 1, Integer::sum));

        // Aggregate favorites (for demonstration, we simply use the keys)
        favoriteGenres.addAll(genreCount.keySet());
        favoriteArtists.addAll(artistCount.keySet());

        // Sort genres and artists by frequency (dummy implementation)
        List<String> sortedGenres = new ArrayList<>(favoriteGenres);
        List<String> sortedArtists = new ArrayList<>(favoriteArtists);

        logger.info("User's favorite genres: " + sortedGenres);
        logger.info("User's favorite artists: " + sortedArtists);

        // Fetch songs matching user preferences
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String genre : sortedGenres) {
            recommendations.addAll(filterManager.filterBySingleCriteria("genre", genre));
        }
        for (String artist : sortedArtists) {
            recommendations.addAll(filterManager.filterBySingleCriteria("artist", artist));
        }

        // Remove duplicates and limit results
        return recommendations.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Fetches the top trending songs based on popularity.
     *
     * @param topN The number of top songs to return.
     * @return A list of the most popular songs.
     */
    public List<Map<String, Object>> getTrendingSongs(int topN) {
        logger.info("Fetching top " + topN + " trending songs...");
        return filterManager.getTopSongs(topN);
    }

    /**
     * Generates recommendations by mixing trending and personalized songs.
     *
     * @param userHistory User's listening history.
     * @param maxResults  The maximum number of recommendations.
     * @return A list of mixed recommendations.
     */
    public List<Map<String, Object>> generateHybridRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.info("Generating hybrid recommendations...");
        List<Map<String, Object>> personalized = generateRecommendations(userHistory, maxResults / 2);
        List<Map<String, Object>> trending = getTrendingSongs(maxResults / 2);

        List<Map<String, Object>> combined = new ArrayList<>();
        combined.addAll(personalized);
        combined.addAll(trending);

        return combined.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Generates recommendations based on user preferences.
     *
     * @param favoriteGenres  List of favorite genres.
     * @param favoriteArtists List of favorite artists.
     * @param maxResults      Maximum number of recommendations.
     * @return A list of recommended songs.
     */
    public List<Map<String, Object>> generateRecommendationsFromPreferences(List<String> favoriteGenres,  List<String> favoriteArtists,int maxResults) {
        logger.info("Generating recommendations based on user preferences...");

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String genre : favoriteGenres) {
            recommendations.addAll(filterManager.filterBySingleCriteria("genre", genre));
        }
        for (String artist : favoriteArtists) {
            recommendations.addAll(filterManager.filterBySingleCriteria("artist", artist));
        }

        return recommendations.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * NEW METHOD: Returns recommended songs as a list of Song objects based on the provided userId.
     * This method uses a dummy implementation by fetching trending songs from getTrendingSongs and converting
     * each map into a Song. Adjust the mapping logic according to your actual data structure.
     *
     * @param userId The user ID for which to get recommendations.
     * @return A list of Song objects.
     */
    public List<Song> getRecommendations(String userId) {
        logger.info("Generating recommendations for user ID: " + userId);
        // For demonstration, fetch trending songs (as maps)
        List<Map<String, Object>> trendingMaps = getTrendingSongs(10);
        List<Song> songRecommendations = new ArrayList<>();
        for (Map<String, Object> map : trendingMaps) {
            // Assume the map has keys "title" and "artist" (set default if missing)
            String title = (String) map.getOrDefault("title", "Unknown Title");
            String artist = (String) map.getOrDefault("artist", "Unknown Artist");
            // Create a new Song using your Song constructor.
            Song song = new Song(title, artist);
            songRecommendations.add(song);
        }
        return songRecommendations;
    }
}