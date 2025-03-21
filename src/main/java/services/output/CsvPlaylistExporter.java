package services.output;

import models.Playlist;
import java.io.FileWriter;
import java.io.IOException;

public class CsvPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        try (FileWriter writer = new FileWriter(destination)) {
            writer.write("Title,Artist,Genre\n");
            playlist.getSongs().forEach(song -> {
                try {
                    writer.write(song.getTitle() + "," + song.getArtist() + "," + song.getGenres() + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            System.out.println("Playlist exported to CSV: " + destination);
        } catch (IOException e) {
            System.err.println("Error exporting playlist: " + e.getMessage());
        }
    }
}
