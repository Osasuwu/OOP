package services.datasources;

import interfaces.MusicDataSource;
import models.UserMusicData;
import java.nio.file.Path;

public class LocalFilesSource implements MusicDataSource {
    private final Path musicPath;

    public LocalFilesSource(String path) {
        this.musicPath = Path.of(path);
    }

    @Override
    public UserMusicData loadMusicData() {
        UserMusicData userData = new UserMusicData();
        // Implementation
        return userData;
    }
    
    @Override
    public boolean isSourceAvailable() {
        return musicPath != null && musicPath.toFile().exists() && musicPath.toFile().isDirectory();
    }
} 