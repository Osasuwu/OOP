package services.importer;

import models.UserMusicData;
import models.Song;
import models.Artist;
import interfaces.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Implementation of DataImportAdapter for JSON file format
 */
public class JsonDataImportAdapter implements DataImportAdapter {
    private static final String[] SUPPORTED_EXTENSIONS = {".json"};
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFromStream(inputStream, filePath.getFileName().toString());
        } catch (IOException e) {
            throw new ImportException("Failed to read JSON file: " + e.getMessage(), e);
        }
    }

    @Override
    public UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        UserMusicData userData = new UserMusicData();
        
        try {
            // Read JSON content as string
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            
            // TODO: Use a JSON parser library to parse the content
            // This is a placeholder that would be replaced with actual JSON parsing
            parseJsonContent(content.toString(), userData);
            
            return userData;
        } catch (IOException e) {
            throw new ImportException("Error reading JSON data: " + e.getMessage(), e);
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
    
    private void parseJsonContent(String jsonContent, UserMusicData userData) {
        // Placeholder implementation - in a real application would use a JSON library like Gson or Jackson
        // This would parse the JSON content and populate the UserMusicData object
        
        // Simplified example:
        if (jsonContent.contains("\"title\"") && jsonContent.contains("\"artist\"")) {
            // Create and add a sample song and artist
            Artist artist = new Artist("Sample JSON Artist");
            
            Song song = new Song("Sample JSON Song", artist.getName());
            
            userData.addArtist(artist);
            userData.addSong(song);
        }
    }
}
