package services.output;

import models.Playlist;
import java.io.FileWriter;
import java.io.IOException;

public class CsvPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        try (FileWriter writer = new FileWriter(destination)) {
            // adding a header row so its clear what each column represents
            writer.write("Title,Artist,Genre\n");

            // looping through each song in the playlist and writing its details
            playlist.getSongs().forEach(song -> {
                try {
                    // writing the song info in csv format (comma-separated)
                    writer.write(song.getTitle() + "," + song.getArtist() + "," + song.getGenres() + "\n");
                } catch (IOException e) {
                    // if something goes wrong while writing a song print the error
                    e.printStackTrace();
                }
            });

            // confirming to the user that the playlist was successfully saved
            System.out.println("playlist exported to csv: " + destination);

        } catch (IOException e) {
            // if there's an issue opening/writing the file print an error message
            System.err.println("error exporting playlist: " + e.getMessage());
        }
    }
}
