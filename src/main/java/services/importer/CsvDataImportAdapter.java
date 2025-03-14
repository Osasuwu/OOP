package services.importer;

import models.UserMusicData;
import models.Song;
import models.Artist;
import models.PlayHistory;
import utils.GenreMapper;
import interfaces.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Implementation of DataImportAdapter for CSV file format
 */
public class CsvDataImportAdapter implements DataImportAdapter {
    private static final String[] SUPPORTED_EXTENSIONS = {".csv", ".tsv"};
    private static final SimpleDateFormat SPOTIFY_DATE_FORMAT = new SimpleDateFormat("\"MMMM d, yyyy 'at' hh:mma\"", Locale.ENGLISH);
    private final GenreMapper genreMapper;
    
    public CsvDataImportAdapter() {
        this.genreMapper = new GenreMapper();
    }
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFromStream(inputStream, filePath.getFileName().toString());
        } catch (IOException e) {
            throw new ImportException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    @Override
    public UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        UserMusicData userData = new UserMusicData();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            String[] header = null;
            
            // Read header line
            if ((line = reader.readLine()) != null) {
                header = parseCsvLine(line);
            }
            
            if (header == null) {
                throw new ImportException("Empty or invalid CSV file");
            }
            
            // Detect if this is a Spotify export file
            boolean isSpotifyFormat = isSpotifyFormat(header);
            
            // Process data lines
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] values = parseCsvLine(line);
                
                if (isSpotifyFormat) {
                    processSpotifyRow(values, userData);
                } else {
                    processStandardCsvRow(values, header, userData);
                }
            }
            
            return userData;
        } catch (IOException e) {
            throw new ImportException("Error reading CSV data: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canHandle(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isSpotifyFormat(String[] header) {
        // Check for Spotify export format which typically has timestamp as first field
        // and spotify track ID and URL in later fields
        if (header.length >= 4) {
            String firstHeader = header[0].toLowerCase().trim();
            return firstHeader.contains("timestamp") || 
                   (header.length >= 5 && header[3].toLowerCase().contains("id") && 
                    header[4].toLowerCase().contains("url"));
        }
        return false;
    }
    
    private void processSpotifyRow(String[] values, UserMusicData userData) throws ImportException {
        if (values.length < 5) {
            return; // Not enough data for Spotify format
        }
        
        try {
            String timestamp = values[0];
            String trackName = values[1];
            String artistName = values[2];
            String spotifyId = values[3];
            String spotifyUrl = values[4];
            
            // Create artist
            Artist artist = new Artist(artistName);
            userData.addArtist(artist);
            
            // Create song
            Song song = new Song(trackName, artistName);
            song.setSpotifyId(spotifyId);
            userData.addSong(song);
            
            // Create play history entry if timestamp can be parsed
            try {
                Date playDate = SPOTIFY_DATE_FORMAT.parse(timestamp);
                PlayHistory playEntry = new PlayHistory();
                playEntry.setSong(song);
                playEntry.setTimestamp(playDate);
                userData.getPlayHistory().add(playEntry);
            } catch (ParseException e) {
                // Unable to parse date, continue without play history
            }
        } catch (Exception e) {
            throw new ImportException("Error processing Spotify data row: " + e.getMessage(), e);
        }
    }
    
    private void processStandardCsvRow(String[] values, String[] header, UserMusicData userData) {
        if (values.length < 2) {
            return; // Skip rows without minimum data
        }
        
        // Map CSV data to object model
        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < Math.min(header.length, values.length); i++) {
            rowData.put(header[i].toLowerCase(), values[i]);
        }
        
        // Create and add song
        if (rowData.containsKey("title") && rowData.containsKey("artist")) {
            String title = rowData.get("title");
            String artistName = rowData.get("artist");
            
            // Create artist if needed
            Artist artist = new Artist(artistName);
            userData.addArtist(artist);
            
            // Create song
            Song song = new Song(title, artistName);
            
            // Handle additional fields if available
            if (rowData.containsKey("album")) {
                song.setAlbum(rowData.get("album"));
            }
            
            if (rowData.containsKey("genre")) {
                String normalizedGenre = genreMapper.normalizeGenre(rowData.get("genre"));
                List<String> genres = new ArrayList<>();
                genres.add(normalizedGenre);
                song.setGenres(genres);
            }
            
            // Add to user data
            userData.addSong(song);
        }
    }
    
    private String[] parseCsvLine(String line) {
        // Auto-detect if this is a TSV file based on tab presence and comma absence
        if (line.contains("\t") && !line.contains(",")) {
            return line.split("\t");
        } else {
            // Handle quoted values with commas inside them
            List<String> tokens = new ArrayList<>();
            boolean inQuotes = false;
            StringBuilder currentToken = new StringBuilder();
            
            for (char c : line.toCharArray()) {
                if (c == '\"') {
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) {
                    tokens.add(currentToken.toString().trim());
                    currentToken = new StringBuilder();
                } else {
                    currentToken.append(c);
                }
            }
            
            // Add the last token
            tokens.add(currentToken.toString().trim());
            return tokens.toArray(new String[0]);
        }
    }
}
