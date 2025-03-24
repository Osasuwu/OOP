package services.importer;

import models.UserMusicData;
import models.Song;
import models.Artist;
import models.PlayHistory;
import utils.GenreMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of DataImportAdapter for CSV file format.
 * Handles both CSV and TSV files with intelligent format detection.
 * Features:
 * - Automatic header detection and column mapping
 * - Flexible date parsing with multiple format support
 * - Artist and song deduplication
 * - Multiple column name variations for flexible import
 * - Tab-separated value support with auto-detection
 */
public class CsvDataImportAdapter implements DataImportAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CsvDataImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".csv", ".tsv"};
    private final GenreMapper genreMapper;
    
    // Patterns for automatic field detection
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}|^\"?[A-Z][a-z]+ \\d{1,2}, \\d{4}");
    private static final Pattern SPOTIFY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{22}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://");
    private static final Pattern CSV_PATTERN = Pattern.compile(".*\\.(csv)$");
    
    // Standard format for Spotify listening history timestamp
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US);

    /**
     * Constructs a new CSV adapter with default settings
     */
    public CsvDataImportAdapter() {
        logger.debug("Initializing CSV data import adapter");
        this.genreMapper = new GenreMapper();
    }
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        logger.info("Starting CSV import from file: {}", filePath);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFromStream(inputStream, filePath.getFileName().toString());
        } catch (IOException e) {
            logger.error("Failed to read CSV file: {}", filePath, e);
            throw new ImportException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    @Override
    public UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        logger.info("Starting CSV import from stream: {}", sourceName);
        UserMusicData userData = new UserMusicData();
        
        try {
            // Read all lines from the input stream first
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            }
            
            if (lines.isEmpty()) {
                logger.warn("Empty file provided: {}", sourceName);
                throw new ImportException("Empty file");
            }

            logger.info("Processing {} lines from {}", lines.size(), sourceName);
            
            // Process the first line to determine if it's a header
            String firstLine = lines.get(0);
            String[] firstLineValues = parseCsvLine(firstLine);
            
            // Check if this is a headerless file
            boolean hasHeader = isHeaderLine(firstLineValues);
            String[] header;
            
            if (hasHeader) {
                logger.info("Header detected: {}", String.join(", ", firstLineValues));
                header = firstLineValues;
                // Skip header line for processing
                lines.remove(0);
            } else {
                logger.info("No header detected, inferring column names");
                // Create inferred header based on first data line
                header = inferHeaderFromData(firstLineValues);
                logger.info("Inferred headers: {}", String.join(", ", header));
            }

            int processedRows = 0;
            int skippedRows = 0;
            int successfulEntries = 0;
            
            // Process data lines
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    skippedRows++;
                    continue;
                }
                String[] values = parseCsvLine(line);
                boolean processed = processStandardCsvRow(values, header, userData);
                if (processed) successfulEntries++;
                processedRows++;
                
                // Log progress for large files
                if (processedRows % 1000 == 0) {
                    logger.info("Processed {} rows so far...", processedRows);
                }
            }

            logger.info("CSV import completed. Processed: {} rows, Skipped: {} empty rows, " +
                      "Successfully imported: {} entries", 
                      processedRows, skippedRows, successfulEntries);
            
            return userData;
        } catch (IOException e) {
            logger.error("Error reading CSV data: {}", sourceName, e);
            throw new ImportException("Error reading CSV data: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the line is likely a header line based on content analysis
     * Uses heuristic detection:
     * 1. Headers typically contain keywords like "artist", "title"
     * 2. Headers typically don't contain dates or IDs
     * 
     * @param values The values from the line
     * @return true if this appears to be a header line
     */
    private boolean isHeaderLine(String[] values) {
        // Header detection heuristic:
        // 1. Headers typically don't contain dates
        // 2. Headers typically have column names like "artist", "title", etc.
        // 3. Headers typically don't have URLs or IDs
        
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
            
            // Check for data patterns that likely aren't headers
            if (DATE_PATTERN.matcher(value).find() || 
                SPOTIFY_ID_PATTERN.matcher(value).matches() || 
                URL_PATTERN.matcher(value).find()) {
                potentialDataFields++;
            }
        }
        
        // If we find multiple potential header names and few data patterns, it's likely a header
        return headerKeywordMatches >= 2 || (headerKeywordMatches > 0 && potentialDataFields == 0);
    }
    
    /**
     * Infer column headers from data when no explicit headers are present
     * Analyzes content patterns to guess column meanings:
     * - Date patterns for timestamps
     * - ID patterns for Spotify IDs
     * - URL patterns for links
     * - Position-based guesses for common fields
     * 
     * @param dataLine First line of data values
     * @return Array of inferred header names
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
            } else if (i == 1 || i == 2) {
                // Assume index 1 is often title, index 2 is often artist
                inferredHeaders[i] = (i == 1) ? "title" : "artist";
            } else {
                // Default column name
                inferredHeaders[i] = "column_" + (i + 1);
            }
        }
        
        return inferredHeaders;
    }
    
    /**
     * Set column mappings manually
     * @param columnMappings Map of column indices to column names
     * @return An updated copy of this adapter with the mappings
     */
    public CsvDataImportAdapter withColumnMappings(Map<Integer, String> columnMappings) {
        // This method would allow users to specify custom column mappings
        // Would need to be expanded to actually use these mappings in processing
        return this;
    }
    
    /**
     * Process a single row of CSV data and add it to the UserMusicData object.
     * Handles flexible mapping of columns and data normalization.
     *
     * @param values Array of values from the CSV row
     * @param header Array of column headers
     * @param userData UserMusicData object to populate
     * @return true if the row was successfully processed and added
     */
    private boolean processStandardCsvRow(String[] values, String[] header, UserMusicData userData) {
        if (values.length < 2) {
            logger.debug("Skipping row with insufficient data (fewer than 2 columns)");
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
        
        // If we still don't have title and artist, try to use the first two columns
        if ((title == null || artistName == null) && values.length >= 2) {
            if (title == null) title = values[0];
            if (artistName == null) artistName = values[1];
        }
        
        // Create and add song if we have title and artist
        if (title != null && artistName != null && !title.isEmpty() && !artistName.isEmpty()) {
            logger.debug("Processing song: '{}' by '{}'", title, artistName);
            
            // Create artist if needed - using proper method to prevent duplicates
            Artist artist = userData.findOrCreateArtist(artistName);
            
            // Create song
            Song song = new Song(title, artistName);
            song.setArtist(artist); // Set the artist object to the song
            
            // Handle additional fields if available
            if (rowData.containsKey("album")) {
                song.setAlbum(rowData.get("album"));
                logger.debug("Added album: '{}'", rowData.get("album"));
            }
            
            if (rowData.containsKey("genre")) {
                String normalizedGenre = GenreMapper.normalizeGenre(rowData.get("genre"));
                List<String> genres = new ArrayList<>();
                genres.add(normalizedGenre);
                song.setGenres(genres);
                logger.debug("Added genre: '{}'", normalizedGenre);
            }
            
            // Try to find and parse dates using various formats
            if (rowData.containsKey("timestamp") || rowData.containsKey("date") || rowData.containsKey("played_at")) {
                String dateStr = rowData.getOrDefault("timestamp", 
                                rowData.getOrDefault("date",
                                rowData.getOrDefault("played_at", "")));
                
                if (!dateStr.isEmpty()) {
                    try {
                        // Try different date formats
                        Date playDate = tryParseDate(dateStr);
                        if (playDate != null) {
                            PlayHistory playEntry = new PlayHistory();
                            playEntry.setSong(song);
                            playEntry.setTimestamp(playDate);
                            userData.getPlayHistory().add(playEntry);
                            logger.debug("Added play history entry with date: {}", playDate);
                        } else {
                            logger.debug("Could not parse date: '{}'", dateStr);
                        }
                    } catch (Exception e) {
                        // Failed to parse date, continue without play history
                        logger.warn("Failed to parse date: '{}' - {}", dateStr, e.getMessage());
                    }
                }
            }
            
            // Add to user data
            userData.addSong(song);
            return true;
        } else {
            logger.warn("Skipping row with missing title or artist: {}", 
                     Arrays.toString(values).substring(0, Math.min(100, Arrays.toString(values).length())));
            return false;
        }
    }
    
    /**
     * Try to parse a date string using multiple common formats
     * @param dateStr The date string to parse
     * @return The parsed Date or null if parsing fails
     */
    private Date tryParseDate(String dateStr) {
        // List of common date formats to try
        SimpleDateFormat[] formats = {
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("yyyy/MM/dd"),
            new SimpleDateFormat("MM/dd/yyyy"),
            new SimpleDateFormat("dd/MM/yyyy"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
            // Fix for Spotify's format with proper locale and lenient parsing
            new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US)
        };
        
        // Clean up the date string
        dateStr = dateStr.replace("\"", "").trim();
        
        for (SimpleDateFormat format : formats) {
            try {
                // Set lenient parsing to handle edge cases
                format.setLenient(true);
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }
        
        // Try parsing with manual AM/PM handling
        try {
            Pattern p = Pattern.compile("(.*?)(\\d{1,2}:\\d{2})(AM|PM).*", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(dateStr);
            if (m.matches()) {
                String datePart = m.group(1).trim();
                String timePart = m.group(2);
                String ampm = m.group(3).toUpperCase();
                String reformattedDate = datePart + " at " + timePart + ampm;
                return dateFormat.parse(reformattedDate);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse date with pattern matching: " + dateStr);
        }

        return null;
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
}
