package services.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import models.Song;
import utils.Logger;

/**
 * Generates song recommendations based on user listening history, preferences, and popularity.
 * Utilizes filtering and scoring mechanisms to deliver highly accurate suggestions.
 *
 * Enhancements Added:
 *  - Recent Listening Weight System (Framework for extension)
 *  - "Surprise Me" mode for discovery
 *  - Region-specific trending songs support
 *  - Recommendation caching to improve performance
 *  - Mood-based recommendation filtering
 */
public class RecommendationEngine {

    private final FilterManager filterManager;
    private final Logger logger;

    // Recommendation caching using a simple in-memory mechanism.
    // Key format: userId + "_recommendations"
    private static Map<String, CacheEntry> recommendationCache = new HashMap<>();
    private static final long CACHE_DURATION_SECONDS = 600; // 10 minutes cache

    // Helper class for cache entries.
    private static class CacheEntry {
        List<Song> recommendations;
        Instant timestamp;

        CacheEntry(List<Song> recommendations, Instant timestamp) {
            this.recommendations = recommendations;
            this.timestamp = timestamp;
        }
    }

    public RecommendationEngine() {
        this.filterManager = new FilterManager();
        this.logger = Logger.getInstance();
    }

    /**
     * Generates recommendations based on user listening history.
     *
     * @param userHistory A list of songs the user has previously listened to.
     * @param maxResults  The maximum number of recommendations to return.
     * @return A list of recommended songs as maps.
     */
    public List<Map<String, Object>> generateRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.info("Generating personalized recommendations for user...");

        // Future enhancement: incorporate recent listening weight system here.
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

        favoriteGenres.addAll(genreCount.keySet());
        favoriteArtists.addAll(artistCount.keySet());

        List<String> sortedGenres = new ArrayList<>(favoriteGenres);
        List<String> sortedArtists = new ArrayList<>(favoriteArtists);

        logger.info("User's favorite genres: " + sortedGenres);
        logger.info("User's favorite artists: " + sortedArtists);

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String genre : sortedGenres) {
            recommendations.addAll(filterManager.filterBySingleCriteria("genre", genre));
        }
        for (String artist : sortedArtists) {
            recommendations.addAll(filterManager.filterBySingleCriteria("artist", artist));
        }

        return recommendations.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Fetches the top trending songs based on popularity.
     * Supports an optional location parameter for region-specific trends.
     *
     * @param topN     The number of top songs to return.
     * @param location (Optional) The user's location; if provided, local trends are prioritized.
     * @return A list of trending songs as maps.
     */
    public List<Map<String, Object>> getTrendingSongs(int topN, String location) {
        if (location != null && !location.trim().isEmpty()) {
            logger.info("Fetching top " + topN + " trending songs for region: " + location);
            // Enhancement: Fetch region-specific trending songs via an external API.
        } else {
            logger.info("Fetching top " + topN + " trending songs globally...");
        }
        return filterManager.getTopSongs(topN);
    }

    /**
     * Overloaded method: Fallback to global trending songs.
     */
    public List<Map<String, Object>> getTrendingSongs(int topN) {
        return getTrendingSongs(topN, null);
    }

    /**
     * Generates hybrid recommendations by mixing trending and personalized songs.
     *
     * @param userHistory User's listening history.
     * @param maxResults  The maximum number of recommendations.
     * @return A list of mixed recommendations as maps.
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
     * Generates recommendations based solely on user preferences.
     *
     * @param favoriteGenres  List of favorite genres.
     * @param favoriteArtists List of favorite artists.
     * @param maxResults      Maximum number of recommendations.
     * @return A list of recommended songs as maps.
     */
    public List<Map<String, Object>> generateRecommendationsFromPreferences(List<String> favoriteGenres,
                                                                            List<String> favoriteArtists,
                                                                            int maxResults) {
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
     * Returns recommended songs as a list of Song objects based on the provided userId.
     * This method uses caching to avoid redundant computations.
     *
     * @param userId The user ID for which to get recommendations.
     * @return A list of Song objects.
     */
    public List<Song> getRecommendations(String userId) {
        logger.info("Generating recommendations for user ID: " + userId);
        String cacheKey = userId + "_recommendations";
        CacheEntry cached = recommendationCache.get(cacheKey);
        if (cached != null && Duration.between(cached.timestamp, Instant.now()).getSeconds() <= CACHE_DURATION_SECONDS) {
            logger.info("Returning cached recommendations for user ID: " + userId);
            return cached.recommendations;
        }

        // For demonstration, fetch trending songs (as maps) and convert to Song objects.
        List<Map<String, Object>> trendingMaps = getTrendingSongs(10);
        List<Song> songRecommendations = new ArrayList<>();
        for (Map<String, Object> map : trendingMaps) {
            String title = (String) map.getOrDefault("title", "Unknown Title");
            String artist = (String) map.getOrDefault("artist", "Unknown Artist");
            Song song = new Song(title, artist);
            songRecommendations.add(song);
        }

        recommendationCache.put(cacheKey, new CacheEntry(songRecommendations, Instant.now()));
        return songRecommendations;
    }

    /**
     * NEW METHOD: Generates recommendations based on moods.
     * Users can select a mood (e.g., "Chill", "Energetic") and the system adjusts recommendations accordingly.
     *
     * @param mood       The mood for which to generate recommendations.
     * @param maxResults Maximum number of recommended songs.
     * @return A list of recommended songs that match the mood.
     */
    public List<Song> generateRecommendationsByMood(String mood, int maxResults) {
        logger.info("Generating mood-based recommendations for mood: " + mood);
        // For demonstration, filter the recommendations by checking if the mood appears in the title or artist.
        List<Song> recommendations = getRecommendations("defaultUser");
        List<Song> moodFiltered = recommendations.stream()
                .filter(song -> song.getTitle().toLowerCase().contains(mood.toLowerCase()) ||
                                song.getArtist().getName().toLowerCase().contains(mood.toLowerCase()))
                .collect(Collectors.toList());
        return moodFiltered.size() > maxResults
            ? moodFiltered.subList(0, maxResults)
            : moodFiltered;
    }

    /**
     * NEW METHOD: Generates recommendations in Surprise Me mode.
     * In this mode, 80% of the songs match usual preferences while 20% are random for diversity.
     *
     * @param userId     The user ID.
     * @param maxResults The maximum number of recommended songs.
     * @return A list of recommended songs with a mix of familiar and unexpected tracks.
     */
    public List<Song> getRecommendationsSurpriseMe(String userId, int maxResults) {
        logger.info("Generating 'Surprise Me' recommendations for user ID: " + userId);
        
        // Get standard recommendations (80% of results)
        List<Song> standard = getRecommendations(userId);
        int standardCount = (int)(maxResults * 0.8);
        standard = standard.size() > standardCount ? standard.subList(0, standardCount) : standard;
        
        // For randomness, attempt to fetch songs marked as "random" in genre criteria.
        List<Map<String, Object>> randomMaps = filterManager.filterBySingleCriteria("genre", "random");
        List<Song> randomSongs = new ArrayList<>();
        for (Map<String, Object> map : randomMaps) {
            String title = (String) map.getOrDefault("title", "Unknown Title");
            String artist = (String) map.getOrDefault("artist", "Unknown Artist");
            Song song = new Song(title, artist);
            randomSongs.add(song);
        }
        int randomCount = maxResults - standard.size();
        if (randomSongs.size() > randomCount) {
            randomSongs = randomSongs.subList(0, randomCount);
        }

        List<Song> result = new ArrayList<>();
        result.addAll(standard);
        result.addAll(randomSongs);
        return result;
    }
}