package services.importer.file;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;
import utils.GenreMapper;

import java.io.*;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of FileImportAdapter for CSV file format.
 * Handles both CSV and TSV files with intelligent format detection.
 * Features:
 * - Automatic header detection and column mapping
 * - Flexible date parsing with multiple format support
 * - Artist and song deduplication
 * - Multiple column name variations for flexible import
 * - Tab-separated value support with auto-detection
 */
public class CsvFileImportAdapter implements FileImportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvFileImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".csv", ".tsv", ".txt"};
    
    // Patterns for automatic field detection
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}|^\"?[A-Z][a-z]+ \\d{1,2}, \\d{4}");
    private static final Pattern SPOTIFY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{22}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://");
    
    // Column index mappings
    private Map<Integer, String> columnMappings = new HashMap<>();
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        LOGGER.info("Starting CSV import from file: {}", filePath);
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFromStream(inputStream, filePath.getFileName().toString());
        } catch (IOException e) {
            LOGGER.error("Failed to read CSV file: {}", filePath, e);
            throw new ImportException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Import data from an input stream
     * @param inputStream The input stream to read from
     * @param sourceName Name of the source for logging
     * @return UserMusicData containing the imported data
     * @throws ImportException If there's an error during import
     */
    private UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        LOGGER.info("Processing data from stream: {}", sourceName);
        UserMusicData userData = new UserMusicData();
        
        try {
            // Read all lines from the input stream
            List<String> lines = readLines(inputStream);
            
            if (lines.isEmpty()) {
                LOGGER.warn("Empty file provided: {}", sourceName);
                throw new ImportException("Empty file");
            }

            LOGGER.info("Read {} lines from {}", lines.size(), sourceName);
            
            // Process the first line to determine if it's a header
            String firstLine = lines.get(0);
            String[] firstLineValues = parseCsvLine(firstLine);
            
            String[] header;
            boolean hasHeader = isHeaderLine(firstLineValues);
            
            if (hasHeader) {
                LOGGER.info("Header detected: {}", String.join(", ", firstLineValues));
                header = firstLineValues;
                lines.remove(0); // Skip header for processing
            } else {
                LOGGER.info("No header detected, inferring column names");
                header = inferHeaderFromData(firstLineValues);
                LOGGER.info("Inferred headers: {}", String.join(", ", header));
            }

            LOGGER.info("Starting to process {} data lines", lines.size());
            
            int processedRows = 0;
            int skippedRows = 0;
            int successfulEntries = 0;
            
            // Process all data lines
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    skippedRows++;
                    continue;
                }
                
                String[] values = parseCsvLine(line);
                boolean processed = processRow(values, header, userData);
                if (processed) {
                    successfulEntries++;
                }
                
                processedRows++;
                
                // Log progress for large files
                if (processedRows % 1000 == 0) {
                    LOGGER.info("Processed {} rows so far...", processedRows);
                }
            }

            LOGGER.info("CSV import completed. Processed: {} rows, Skipped: {} empty rows, Successfully imported: {} entries", 
                      processedRows, skippedRows, successfulEntries);
            
            LOGGER.info("Found {} unique songs, {} unique artists, {} play history entries", 
                       userData.getSongs().size(), userData.getArtists().size(), userData.getPlayHistory().size());
            
            return userData;
        } catch (IOException e) {
            LOGGER.error("Error reading CSV data: {}", sourceName, e);
            throw new ImportException("Error reading CSV data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Read all non-empty lines from an input stream
     */
    private List<String> readLines(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Check if a line is likely a header based on content analysis
     * Uses heuristic detection:
     * 1. Headers typically contain keywords like "artist", "title"
     * 2. Headers typically don't contain dates or IDs
     */
    private boolean isHeaderLine(String[] values) {
        if (values.length == 0) return false;
        
        // Common header keywords to look for
        String[] commonHeaders = {"artist", "title", "track", "song", "album", "genre", "date", "timestamp", "id"};
        
        int headerKeywordMatches = 0;
        int potentialDataFields = 0;
        
        for (String value : values) {
            String lowerValue = value.toLowerCase().trim();
            
            // Check for common header names
            for (String header : commonHeaders) {
                if (lowerValue.contains(header)) {
                    headerKeywordMatches++;
                    break;
                }
            }
            
            // Check for patterns that suggest this is data, not a header
            if (DATE_PATTERN.matcher(value).find() || 
                SPOTIFY_ID_PATTERN.matcher(value).matches() || 
                URL_PATTERN.matcher(value).find()) {
                potentialDataFields++;
            }
        }
        
        // If we find multiple header keywords and few data patterns, it's likely a header
        return headerKeywordMatches >= 2 || (headerKeywordMatches > 0 && potentialDataFields == 0);
    }
    
    /**
     * Infer column headers from data when no explicit headers are present
     * Analyzes content patterns to guess column meanings
     */
    private String[] inferHeaderFromData(String[] dataLine) {
        String[] inferredHeaders = new String[dataLine.length];
        
        for (int i = 0; i < dataLine.length; i++) {
            String value = dataLine[i].trim();
            
            // Check for various patterns to infer column meaning
            if (i == 0 && DATE_PATTERN.matcher(value).find()) {
                inferredHeaders[i] = "timestamp";
            } else if (SPOTIFY_ID_PATTERN.matcher(value).matches()) {
                inferredHeaders[i] = "spotify_id";
            } else if (URL_PATTERN.matcher(value).find()) {
                inferredHeaders[i] = "url";
            } else if (i == 1) {
                // Common pattern: second column is often the song title
                inferredHeaders[i] = "title";
            } else if (i == 2) {
                // Common pattern: third column is often the artist
                inferredHeaders[i] = "artist";
            } else {
                // Default column name
                inferredHeaders[i] = "column_" + (i + 1);
            }
        }
        
        return inferredHeaders;
    }
    
    /**
     * Process a single row of CSV data and add it to the UserMusicData object
     */
    private boolean processRow(String[] values, String[] header, UserMusicData userData) {
        if (values.length < 2) {
            LOGGER.debug("Skipping row with insufficient data (fewer than 2 columns)");
            return false; // Skip rows without minimum data
        }
        
        // Map CSV data to object model
        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < Math.min(header.length, values.length); i++) {
            rowData.put(header[i].toLowerCase(), values[i]);
        }
        
        // Extract title and artist with flexible mapping
        String title = null;
        String artistName = null;
        
        // Look for title in various column names
        for (String titleKey : Arrays.asList("title", "track", "track_name", "name", "song", "column_2")) {
            if (rowData.containsKey(titleKey) && !rowData.get(titleKey).isEmpty()) {
                title = rowData.get(titleKey);
                break;
            }
        }
        
        // Look for artist in various column names
        for (String artistKey : Arrays.asList("artist", "artist_name", "performer", "column_3")) {
            if (rowData.containsKey(artistKey) && !rowData.get(artistKey).isEmpty()) {
                artistName = rowData.get(artistKey);
                break;
            }
        }
        
        // Create and add song if we have title and artist
        if (title != null && artistName != null && !title.isEmpty() && !artistName.isEmpty()) {
            LOGGER.debug("Processing song: '{}' by '{}'", title, artistName);
            
            // Create artist if needed - using proper method to prevent duplicates
            Artist artist = userData.findOrCreateArtist(artistName);
            
            // Create song and link to artist
            Song song = new Song(title, artistName);
            song.setArtist(artist);
            
            // Handle additional fields if available
            for (String key : Arrays.asList("album", "album_name", "album_title")) {
                if (rowData.containsKey(key)) {
                    song.setAlbumName(rowData.get(key));
                }
            }
            
            
            // Look for Spotify ID
            for (String key : Arrays.asList("spotify_id", "id", "track_id")) {
                if (rowData.containsKey(key) && SPOTIFY_ID_PATTERN.matcher(rowData.get(key)).matches()) {
                    song.setSpotifyId(rowData.get(key));
                    break;
                }
            }
            
            // Look for URL/Link
            for (String key : Arrays.asList("url", "link", "spotify_url")) {
                if (rowData.containsKey(key) && rowData.get(key).startsWith("http")) {
                    // Store URL if needed
                    break;
                }
            }
            
            // Add song to userData (will handle deduplication)
            userData.addSong(song);
            
            // Try to find and parse date from various columns
            Date playDate = null;
            for (String dateKey : Arrays.asList("timestamp", "date", "played_at", "time", "column_1")) {
                if (rowData.containsKey(dateKey) && !rowData.get(dateKey).isEmpty()) {
                    playDate = tryParseDate(rowData.get(dateKey));
                    if (playDate != null) {
                        break;
                    }
                }
            }
            
            // Create play history entry if we have a date
            if (playDate != null) {
                userData.addPlayHistory(new PlayHistory(song, playDate));
                LOGGER.debug("Added play history entry with date: {}", playDate);
            } else {
                // Still create a history entry with current date if no date found
                // This represents that the song exists in the user's library
                userData.addPlayHistory(new PlayHistory(song));
                LOGGER.debug("Added play history entry with current date (no date found in data)");
            }
            
            return true;
        } else {
            LOGGER.debug("Skipping row with missing title or artist");
            return false;
        }
    }
    
    /**
     * Try to parse a date string using multiple common formats
     */
    private Date tryParseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        // Clean the date string
        dateStr = dateStr.replace("\"", "").trim();
        
        // List of date formats to try
        SimpleDateFormat[] formats = {
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("yyyy/MM/dd"),
            new SimpleDateFormat("MM/dd/yyyy"),
            new SimpleDateFormat("dd/MM/yyyy"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
            new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma")
        };
        
        // Try each format
        for (SimpleDateFormat format : formats) {
            try {
                format.setLenient(true);
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }
        
        // Try to parse epoch milliseconds
        try {
            long epochMillis = Long.parseLong(dateStr);
            return new Date(epochMillis);
        } catch (NumberFormatException e) {
            // Not a number
        }
        
        // Try parsing with manual AM/PM handling
        try {
            Pattern p = Pattern.compile("(.*?)(\\d{1,2}:\\d{2})(AM|PM).*", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(dateStr);
            if (m.matches()) {
                String datePart = m.group(1).trim();
                String timePart = m.group(2);
                String ampm = m.group(3).toUpperCase();
                
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US);
                String reformattedDate = datePart + " at " + timePart + ampm;
                return dateFormat.parse(reformattedDate);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse date with pattern matching: {}", dateStr);
        }

        LOGGER.debug("Failed to parse date: '{}'", dateStr);
        return null;
    }
    
    /**
     * Parse a CSV line into an array of values
     * Handles quoted fields properly
     */
    private String[] parseCsvLine(String line) {
        // Auto-detect if this is a TSV file based on tab presence and comma absence
        if (line.contains("\t") && !line.contains(",")) {
            return line.split("\t");
        }
        
        // Handle quoted values with commas inside them
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                currentToken.append(c);
            } else if (c == ',' && !inQuotes) {
                tokens.add(currentToken.toString().trim());
                currentToken = new StringBuilder();
            } else {
                currentToken.append(c);
            }
        }
        
        // Add the last token
        tokens.add(currentToken.toString().trim());
        
        // Clean up quotes from the tokens
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                tokens.set(i, token.substring(1, token.length() - 1));
            }
        }
        
        return tokens.toArray(new String[0]);
    }
    
    @Override
    public boolean canHandle(Path filePath) {
        if (filePath == null) return false;
        
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
