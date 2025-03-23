package services.generation;

import services.utils.Logger;
import services.music.Song;
import services.music.Playlist;
import services.utils.FilterManager;
import services.recommendation.RecommendationEngine;
import services.sync.OfflineSyncManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generator class responsible for dynamically creating playlists based on user preferences,
 * filters, and recommendations.
 */
public class Generator {

    private final Logger logger;
    private final FilterManager filterManager;
    private final RecommendationEngine recommendationEngine;
    private final OfflineSyncManager offlineSyncManager;

    /**
     * Constructor initializing necessary services.
     */
    public Generator() {
        this.logger = new Logger();
        this.filterManager = new FilterManager();
        this.recommendationEngine = new RecommendationEngine();
        this.offlineSyncManager = new OfflineSyncManager();
    }

    /**
     * Generates a playlist based on a given criteria.
     *
     * @param userId   The ID of the user requesting the playlist.
     * @param criteria The filtering and recommendation criteria.
     * @return A dynamically generated Playlist.
     */
    public Playlist generatePlaylist(String userId, String criteria) {
        logger.logInfo("Starting playlist generation for User ID: " + userId + " with criteria: " + criteria);

        // Fetch recommended songs
        List<Song> recommendedSongs = recommendationEngine.getRecommendations(userId);
        logger.logInfo("Fetched " + recommendedSongs.size() + " recommended songs.");

        // Apply filters based on criteria
        List<Song> filteredSongs = filterManager.applyFilters(recommendedSongs, criteria);
        logger.logInfo("After applying filters, " + filteredSongs.size() + " songs remain.");

        if (filteredSongs.isEmpty()) {
            logger.logWarning("No songs available after filtering.");
            return new Playlist("Generated Playlist", List.of());
        }

        // Generate playlist
        Playlist playlist = new Playlist("Generated Playlist", filteredSongs);
        logger.logInfo("Playlist generated successfully with " + playlist.getSongs().size() + " songs.");

        // Check if offline sync is needed
        offlineSyncManager.syncPlaylist(playlist, userId);

        return playlist;
    }
}
