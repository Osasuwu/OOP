package services;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import models.Playlist;
import models.PlaylistParameters;
import models.Song;
<<<<<<< HEAD
import services.database.MusicDatabaseManager;
import utils.GenreMapper;
import utils.Logger;

/**
 * Service that generates playlists based on user preferences and parameters
=======
import models.UserPreferences;
import services.database.MusicDatabaseManager;

/**
 * Service that generates playlists based on user preferences and database songs
 * @param <Genre>
>>>>>>> generator
 */
public class PlaylistGenerator<Genre> {
    private static final Logger LOGGER = Logger.getLogger(PlaylistGenerator.class.getName());
    private MusicDatabaseManager dbManager;
    
    private MusicDatabaseManager dbManager;
    private Logger logger;
    
    public PlaylistGenerator() {
        this.logger = Logger.getInstance();
    }
    
    /**
<<<<<<< HEAD
     * Sets the database manager for retrieving songs
     * @param dbManager The music database manager
     */
    public void setDatabaseManager(MusicDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    /**
     * Generate a playlist based on parameters and preferences
=======
     * Generate a playlist based on user preferences and parameters
>>>>>>> generator
     * @param params Playlist generation parameters
     * @param preferences User's preferences
     * @return A generated playlist with songs from the database
     */
<<<<<<< HEAD
    public Playlist generatePlaylist(PlaylistParameters params, PlaylistPreferences preferences) {
        // Create a new playlist with the specified name
        Playlist playlist = new Playlist(params.getName());
        
        try {
            // Get songs from database based on preferences
            List<Song> candidateSongs = fetchCandidateSongs(params, preferences);
            
            // If no songs were found, return empty playlist
            if (candidateSongs.isEmpty()) {
                logger.warning("No candidate songs found for playlist generation");
                return playlist;
            }
            
            // Apply filters and select songs based on preferences
            List<Song> selectedSongs = filterAndSelectSongs(candidateSongs, preferences);
            playlist.setSongs(selectedSongs);
            
            logger.info("Generated playlist with " + selectedSongs.size() + " songs");
        } catch (Exception e) {
            logger.error("Error generating playlist: " + e.getMessage());
            e.printStackTrace();
        }
        
        return playlist;
    }
    
    /**
     * Fetches candidate songs from the database based on parameters and preferences
     */
    private List<Song> fetchCandidateSongs(PlaylistParameters params, PlaylistPreferences preferences) {
        if (dbManager == null) {
            logger.error("Database manager is not initialized");
            return new ArrayList<>();
        }
        
        // Fetch songs based on parameters
        List<Song> candidateSongs = new ArrayList<>();
        
        // If specific genres are specified in parameters, use them
        if (params.getGenres() != null && !params.getGenres().isEmpty()) {
            for (String genre : params.getGenres()) {
                candidateSongs.addAll(dbManager.getSongsByGenre(genre));
            }
        }
        // If specific artists are specified in parameters, use them
        else if (params.getArtists() != null && !params.getArtists().isEmpty()) {
            for (String artist : params.getArtists()) {
                candidateSongs.addAll(dbManager.getSongsByArtist(artist));
            }
        }
        // Otherwise, use preferences from user's history
        else {
            // If preferences indicate genres, use them
            if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
                for (String genre : preferences.getGenres()) {
                    candidateSongs.addAll(dbManager.getSongsByGenre(genre));
                }
            } else {
                // Get user's top songs
                candidateSongs = dbManager.getTopSongs(preferences.getSongCount() * 3);
            }
        }
        
        // If we still don't have enough songs, get popular songs
        if (candidateSongs.size() < preferences.getSongCount()) {
            candidateSongs.addAll(dbManager.getPopularSongs(preferences.getSongCount() * 2));
        }
        
        return candidateSongs;
    }
    
    /**
     * For basic testing and backward compatibility
     */
    public Playlist generate(PlaylistParameters params) {
        Playlist playlist = new Playlist(params.getName());
        List<Song> songs = new ArrayList<>();
    
        int count = params.getSongCount();
        for (int i = 0; i < count; i++) {
            Song song = new Song("Song " + (i + 1), "Artist " + (i + 1));
            songs.add(song);
        }
    
        playlist.setSongs(songs);
        return playlist;
    }
    
    private List<Song> filterAndSelectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        // Filter by genres if specified in the preferences
        if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
            Set<String> normalizedGenres = preferences.getGenres().stream()
                .map(GenreMapper::normalizeGenre)
                .collect(Collectors.toSet());
            
            candidateSongs = candidateSongs.stream()
                .filter(song -> hasMatchingGenre(song, normalizedGenres))
                .collect(Collectors.toList());
        }
        
        // Further filter: exclude songs by artists specified to be excluded
        if (preferences.getExcludeArtists() != null && !preferences.getExcludeArtists().isEmpty()) {
            Set<String> excludedArtists = new HashSet<>(preferences.getExcludeArtists());
            candidateSongs = candidateSongs.stream()
                .filter(song -> !excludedArtists.contains(song.getArtist().getName()))
                .collect(Collectors.toList());
        }
        
        // Select songs from the filtered candidate list based on the selection strategy
        return selectSongs(candidateSongs, preferences);
    }
    
    private boolean hasMatchingGenre(Song song, Set<String> targetGenres) {
        if (song.getGenres() == null || song.getGenres().isEmpty()) {
            return false;
        }
        
        return song.getGenres().stream()
            .map(GenreMapper::normalizeGenre)
            .anyMatch(targetGenres::contains);
    }
    
    private List<Song> selectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        int count = preferences.getSongCount();
        if (count <= 0) {
            count = 20; // Use 20 as the default song count
        }
        
        // If there are not enough candidate songs, return them all
        if (candidateSongs.size() <= count) {
            return new ArrayList<>(candidateSongs);
        }
        
        List<Song> selectedSongs;
        // Choose song selection strategy based on preferences
        switch (preferences.getSelectionStrategy()) {
            case RANDOM:
                selectedSongs = selectRandomSongs(candidateSongs, count);
                break;
            case POPULAR:
                selectedSongs = selectPopularSongs(candidateSongs, count);
                break;
            case DIVERSE:
                selectedSongs = selectDiverseSongs(candidateSongs, count);
                break;
            default:
                selectedSongs = selectBalancedSongs(candidateSongs, count);
        }
        
        return selectedSongs;
    }
    
    private List<Song> selectRandomSongs(List<Song> candidateSongs, int count) {
        List<Song> shuffled = new ArrayList<>(candidateSongs);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
    
    private List<Song> selectPopularSongs(List<Song> candidateSongs, int count) {
        // Sort by popularity and take the top ones
        List<Song> sorted = candidateSongs.stream()
            .sorted((s1, s2) -> Integer.compare(s2.getPopularity(), s1.getPopularity()))
            .collect(Collectors.toList());
        
        return sorted.subList(0, Math.min(count, sorted.size()));
    }
    
    private List<Song> selectDiverseSongs(List<Song> candidateSongs, int count) {
        // Select songs so that a wide range of artists is represented
        List<Song> selected = new ArrayList<>();
        Set<String> includedArtists = new HashSet<>();
        
        // First pass: pick one song per artist
        for (Song song : candidateSongs) {
            if (selected.size() >= count)
                break;
            
            if (!includedArtists.contains(song.getArtist().getName())) {
                selected.add(song);
                includedArtists.add(song.getArtist().getName());
=======
    public Playlist generatePlaylist(PlaylistParameters params, UserPreferences preferences) {
        Playlist playlist = new Playlist();
        playlist.setName(params.getName());
        
        try {
            // Load songs from database that match criteria
            List<Song> selectedSongs = fetchSongsFromDatabase(params, preferences);
            
            if (selectedSongs.isEmpty()) {
                LOGGER.warning("No songs found matching the criteria");
                return playlist;
>>>>>>> generator
            }
            
            playlist.setSongs(selectedSongs);
            LOGGER.info("Generated playlist '" + params.getName() + "' with " + selectedSongs.size() + " songs");
            return playlist;
        } catch (Exception e) {
            LOGGER.severe("Error generating playlist: " + e.getMessage());
            e.printStackTrace();
            return playlist;
        }
    }
    
    /**
     * Fetch songs from database that match the given parameters and preferences
     */
    private List<Song> fetchSongsFromDatabase(PlaylistParameters params, UserPreferences preferences) {
        List<Song> result = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection()) {
            // Build SQL query based on parameters
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT s.id, s.title, s.artist_id, s.duration_ms, s.popularity, ");
            queryBuilder.append("s.spotify_id, s.release_date, s.album_name, s.preview_url, s.image_url, ");
            queryBuilder.append("a.id as a_id, a.name as artist_name ");
            queryBuilder.append("FROM songs s ");
            queryBuilder.append("JOIN artists a ON s.artist_id = a.id ");
            queryBuilder.append("WHERE 1=1 ");
            
            // Add duration filters
            if (params.getMinDuration() > 0) {
                queryBuilder.append("AND s.duration_ms >= ? ");
            }
            
            if (params.getMaxDuration() < Integer.MAX_VALUE) {
                queryBuilder.append("AND s.duration_ms <= ? ");
            }
            
            // Add inclusion criteria
            Map<String, Set<String>> inclusionCriteria = params.getInclusionCriteria();
            if (inclusionCriteria != null && !inclusionCriteria.isEmpty()) {
                if (inclusionCriteria.containsKey("genres") && !inclusionCriteria.get("genres").isEmpty()) {
                    queryBuilder.append("AND g.name IN (");
                    queryBuilder.append(String.join(",", Collections.nCopies(inclusionCriteria.get("genres").size(), "?")));
                    queryBuilder.append(") ");
                }
                
                if (inclusionCriteria.containsKey("artists") && !inclusionCriteria.get("artists").isEmpty()) {
                    queryBuilder.append("AND a.name IN (");
                    queryBuilder.append(String.join(",", Collections.nCopies(inclusionCriteria.get("artists").size(), "?")));
                    queryBuilder.append(") ");
                }
            }
            
            // Add exclusion criteria
            Map<String, Set<String>> exclusionCriteria = params.getExclusionCriteria();
            if (exclusionCriteria != null && !exclusionCriteria.isEmpty()) {
                if (exclusionCriteria.containsKey("genres") && !exclusionCriteria.get("genres").isEmpty()) {
                    queryBuilder.append("AND g.name NOT IN (");
                    queryBuilder.append(String.join(",", Collections.nCopies(exclusionCriteria.get("genres").size(), "?")));
                    queryBuilder.append(") ");
                }
                
                if (exclusionCriteria.containsKey("artists") && !exclusionCriteria.get("artists").isEmpty()) {
                    queryBuilder.append("AND a.name NOT IN (");
                    queryBuilder.append(String.join(",", Collections.nCopies(exclusionCriteria.get("artists").size(), "?")));
                    queryBuilder.append(") ");
                }
            }
            
            // Add GROUP BY to handle multiple genre matches
            queryBuilder.append("GROUP BY s.id, a.id ");
            
            // Order based on selection strategy
            switch (params.getSelectionStrategy()) {
                case POPULAR:
                    queryBuilder.append("ORDER BY s.popularity DESC ");
                    break;
                case RANDOM:
                    queryBuilder.append("ORDER BY RANDOM() ");
                    break;
                case DIVERSE:
                    // For diverse, we'll get songs and filter them later
                    queryBuilder.append("ORDER BY a.name, s.title ");
                    break;
                default: // BALANCED
                    queryBuilder.append("ORDER BY s.popularity DESC, RANDOM() ");
                    break;
            }
            
            // Limit the results
            queryBuilder.append("LIMIT ?");
            
            String sqlQuery = queryBuilder.toString();
            LOGGER.info("Executing SQL: " + sqlQuery);
            
            PreparedStatement stmt = conn.prepareStatement(sqlQuery);
            
            // Set parameters
            int paramIndex = 1;
            if (params.getMinDuration() > 0) {
                stmt.setInt(paramIndex++, params.getMinDuration() * 1000); // Convert seconds to ms
            }
            
            if (params.getMaxDuration() < Integer.MAX_VALUE) {
                stmt.setInt(paramIndex++, params.getMaxDuration() * 1000); // Convert seconds to ms
            }
            
            // Set inclusion genre parameters
            if (inclusionCriteria != null && inclusionCriteria.containsKey("genres") && !inclusionCriteria.get("genres").isEmpty()) {
                for (String genre : inclusionCriteria.get("genres")) {
                    stmt.setString(paramIndex++, genre);
                }
            }
            
            // Set inclusion artist parameters
            if (inclusionCriteria != null && inclusionCriteria.containsKey("artists") && !inclusionCriteria.get("artists").isEmpty()) {
                for (String artist : inclusionCriteria.get("artists")) {
                    stmt.setString(paramIndex++, artist);
                }
            }
            
            // Set exclusion genre parameters
            if (exclusionCriteria != null && exclusionCriteria.containsKey("genres") && !exclusionCriteria.get("genres").isEmpty()) {
                for (String genre : exclusionCriteria.get("genres")) {
                    stmt.setString(paramIndex++, genre);
                }
            }
            
            // Set exclusion artist parameters
            if (exclusionCriteria != null && exclusionCriteria.containsKey("artists") && !exclusionCriteria.get("artists").isEmpty()) {
                for (String artist : exclusionCriteria.get("artists")) {
                    stmt.setString(paramIndex++, artist);
                }
            }
            
            // Set the limit based on song count
            // For diverse strategy, request more songs to ensure diversity after filtering
            int fetchCount = params.getSelectionStrategy() == PlaylistParameters.PlaylistSelectionStrategy.DIVERSE ? 
                                params.getSongCount() * 3 : params.getSongCount();
            stmt.setInt(paramIndex, fetchCount);
            
            ResultSet rs = stmt.executeQuery();
            Set<String> includedArtists = new HashSet<>();
            
            // Convert results to Song objects
            while (rs.next()) {
                String songId = rs.getString("id");
                String title = rs.getString("title");
                String artistId = rs.getString("artist_id");
                String artistName = rs.getString("artist_name");
                long duration = rs.getLong("duration_ms");
                int popularity = rs.getInt("popularity");
                String spotifyId = rs.getString("spotify_id");
                Date releaseDate = rs.getDate("release_date");
                String albumName = rs.getString("album_name");
                String previewUrl = rs.getString("preview_url");
                String imageUrl = rs.getString("image_url");
                // Fetch and set the artist's genres
                List<String> genres = new ArrayList<>();
                String artistNameForGenres = artistName;

                // We need a separate query to get all genres for this artist
                try (PreparedStatement genreStmt = conn.prepareStatement(
                    "SELECT genre FROM artist_genres WHERE artist_name = ?")) {
                    genreStmt.setString(1, artistNameForGenres);
                    ResultSet genreRs = genreStmt.executeQuery();
                    
                    while (genreRs.next()) {
                    String genre = genreRs.getString("genre");
                    if (genre != null && !genre.isEmpty()) {
                        genres.add(genre);
                    }
                    }
                    genreRs.close();
                } catch (SQLException e) {
                    LOGGER.warning("Could not fetch genres for artist: " + artistName + ". Error: " + e.getMessage());
                }
                
                Artist artist = new Artist(artistId, artistName);
                artist.setGenres(genres);
                Song song = new Song(songId, title, artist);
                song.setDurationMs(duration);
                song.setPopularity(popularity);
                song.setSpotifyId(spotifyId);
                song.setAlbumName(albumName);
                song.setReleaseDate(releaseDate);
                song.setPreviewUrl(previewUrl);
                song.setImageUrl(imageUrl);
                song.setGenres(artist.getGenres());
                
                // For diverse strategy, limit the number of songs per artist
                if (params.getSelectionStrategy() == PlaylistParameters.PlaylistSelectionStrategy.DIVERSE) {
                    // Add one song per artist first
                    if (!includedArtists.contains(artistId) || result.size() < params.getSongCount() / 2) {
                        includedArtists.add(artistId);
                        result.add(song);
                    }
                } else {
                    result.add(song);
                }
                
                if (result.size() >= params.getSongCount()) {
                    break;
                }
            }
            
            // If we still need more songs and are using diverse strategy, add more
            if (result.size() < params.getSongCount() && 
                params.getSelectionStrategy() == PlaylistParameters.PlaylistSelectionStrategy.DIVERSE) {
                // Continue adding songs, possibly more than one per artist
                rs = stmt.executeQuery(); // Re-execute the query
                while (rs.next() && result.size() < params.getSongCount()) {
                    String songId = rs.getString("id");
                    String title = rs.getString("title");
                    String artistId = rs.getString("artist_id");
                    String artistName = rs.getString("artist_name");
                    long duration = rs.getLong("duration_ms");
                    int popularity = rs.getInt("popularity");
                    String spotifyId = rs.getString("spotify_id");
                    String albumName = rs.getString("album_name");
                    
                    Artist artist = new Artist(artistId, artistName);
                    Song song = new Song(songId, title, artist);
                    song.setDurationMs(duration);
                    song.setPopularity(popularity);
                    song.setSpotifyId(spotifyId);
                    song.setAlbumName(albumName);
                    
                    // Add only if not already included
                    if (!containsSong(result, song)) {
                        result.add(song);
                    }
                }
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            LOGGER.severe("Database error while fetching songs: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Check if a song is already in the list to avoid duplicates
     */
    private boolean containsSong(List<Song> songs, Song song) {
        for (Song s : songs) {
            if (s.getId().equals(song.getId())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Sets the database manager instance
     * @param dbManager The database manager to use for fetching songs
     */
    public void setDatabaseManager(MusicDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
}