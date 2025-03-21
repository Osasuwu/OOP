package services.output;

import models.Playlist;
import java.io.FileWriter;
import java.io.IOException;

public class CsvPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        try (FileWriter writer = new FileWriter(destination)) {
            // writing the first row (header) so we know what each column means
            writer.write("Title,Artist,Genre\n");

            // going through each song in the playlist and adding it to the file
            playlist.getSongs().forEach(song -> {
                try {
                    // writing the song details as a new line in the csv file
                    writer.write(song.getTitle() + "," + song.getArtist() + "," + song.getGenres() + "\n");
                } catch (IOException e) {
                    // if something goes wrong while writing, print the error
                    e.printStackTrace();
                }
            });

            // letting the user know the file was successfully created
            System.out.println("playlist exported to csv: " + destination);

        } catch (IOException e) {
            // if there's an issue opening or writing the file, print an error message
            System.err.println("error exporting playlist: " + e.getMessage());
        }
    }
}
