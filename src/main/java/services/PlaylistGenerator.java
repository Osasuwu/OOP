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

import models.Artist;
import models.Playlist;
import models.PlaylistParameters;
import models.Song;
import models.UserPreferences;
import services.database.MusicDatabaseManager;

/**
 * Service that generates playlists based on user preferences and database songs
 * @param <Genre>
 */
public class PlaylistGenerator<Genre> {
    private static final Logger LOGGER = Logger.getLogger(PlaylistGenerator.class.getName());
    private MusicDatabaseManager dbManager;
    
    /**
     * Generate a playlist based on user preferences and parameters
     * @param params Playlist generation parameters
     * @param preferences User's preferences
     * @return A generated playlist with songs from the database
     */
    public Playlist generatePlaylist(PlaylistParameters params, UserPreferences preferences) {
        Playlist playlist = new Playlist();
        playlist.setName(params.getName());
        
        try {
            // Load songs from database that match criteria
            List<Song> selectedSongs = fetchSongsFromDatabase(params, preferences);
            
            if (selectedSongs.isEmpty()) {
                LOGGER.warning("No songs found matching the criteria");
                return playlist;
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