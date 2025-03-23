package services.generator;

import java.util.List;

import models.Playlist;
import models.Song;
import services.sync.OfflineSyncManager;
import utils.Logger;

public class Generator {

    private final Logger logger;
    private final FilterManager filterManager;
    private final RecommendationEngine recommendationEngine;
    private final OfflineSyncManager offlineSyncManager;

    /**
     * Constructor initializing necessary services.
     */
    public Generator() {
        this.logger = Logger.getInstance();
        this.filterManager = new FilterManager();
        this.recommendationEngine = new RecommendationEngine();
        // Fixed: Provide the needed String argument to OfflineSyncManager.
        this.offlineSyncManager = new OfflineSyncManager("music_folder");
    }

    /**
     * Generates a playlist based on a given criteria.
     *
     * @param userId   The ID of the user requesting the playlist.
     * @param criteria The filtering and recommendation criteria.
     * @return A dynamically generated Playlist.
     */
    public Playlist generatePlaylist(String userId, String criteria) {
        logger.info("Starting playlist generation for User ID: " + userId + " with criteria: " + criteria);

        // Fetch recommended songs (make sure RecommendationEngine implements getRecommendations(String))
        List<Song> recommendedSongs = recommendationEngine.getRecommendations(userId);
        logger.info("Fetched " + recommendedSongs.size() + " recommended songs.");

        // Apply filters based on criteria
        List<Song> filteredSongs = filterManager.applyFilters(recommendedSongs, criteria);
        logger.info("After applying filters, " + filteredSongs.size() + " songs remain.");

        if (filteredSongs.isEmpty()) {
            logger.warning("No songs available after filtering.");
            // Create an empty playlist using the one-argument constructor and then set songs to an empty list.
            Playlist emptyPlaylist = new Playlist("Generated Playlist");
            emptyPlaylist.setSongs(List.of());
            return emptyPlaylist;
        }

        // Generate playlist: Using one-argument constructor then set the song list.
        Playlist playlist = new Playlist("Generated Playlist");
        playlist.setSongs(filteredSongs);
        logger.info("Playlist generated successfully with " + playlist.getSongs().size() + " songs.");

        // Sync offline if needed: Call the method with only the playlist argument.
        offlineSyncManager.syncPlaylist(playlist);

        return playlist;
    }
}