package services.output;

import models.Playlist;

public interface PlaylistExporter {
    void export(Playlist playlist, String destination);
}