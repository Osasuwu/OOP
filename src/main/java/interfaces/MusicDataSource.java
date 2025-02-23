package interfaces;

import models.UserMusicData;

public interface MusicDataSource {
    UserMusicData loadMusicData();
    boolean isSourceAvailable();
} 