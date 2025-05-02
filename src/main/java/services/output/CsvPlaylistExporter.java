package services.output;

import models.Playlist;
import models.Song;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CsvPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        // Create the directory if it doesn't exist
        File directory = new File(destination);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                System.err.println("Failed to create directory: " + destination);
                return;
            }
        }
        
        // Create the full file path
        String fileName = playlist.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".csv"; // Replace invalid characters
        String fullPath = destination + File.separator + fileName;
        
        try (FileWriter writer = new FileWriter(fullPath)) {
            // Adding a header row so it's clear what each column represents
            writer.write("Title,Artist,Album,Duration(ms),Popularity\n");

            // Looping through each song in the playlist and writing its details
            for (Song song : playlist.getSongs()) {
                try {
                    // Writing the song info in csv format (comma-separated)
                    // Escape commas in fields with quotes
                    String title = "\"" + song.getTitle().replace("\"", "\"\"") + "\"";
                    String artist = "\"" + song.getArtist().getName().replace("\"", "\"\"") + "\"";
                    String album = song.getAlbumName() != null ? 
                                  "\"" + song.getAlbumName().replace("\"", "\"\"") + "\"" : "\"\"";
                    
                    writer.write(title + "," + 
                                artist + "," + 
                                album + "," + 
                                song.getDurationMs() + "," + 
                                song.getPopularity() + "\n");
                } catch (IOException e) {
                    // If something goes wrong while writing a song, print the error
                    e.printStackTrace();
                }
            }

            // Confirming to the user that the playlist was successfully saved
            System.out.println("Playlist exported to CSV: " + fullPath);

        } catch (IOException e) {
            // If there's an issue opening/writing the file, print an error message
            System.err.println("Error exporting playlist: " + e.getMessage());
        }
    }
}
