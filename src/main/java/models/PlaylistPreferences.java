package models;

import java.util.List;
import java.util.ArrayList;

public class PlaylistPreferences {
    private List<String> preferredGenres = new ArrayList<>();
    private Integer minPopularity;
    private Integer maxPopularity;
    private Integer targetDuration;
    private Integer songCount;

    public List<String> getPreferredGenres() { return preferredGenres; }
    public void setPreferredGenres(List<String> preferredGenres) { this.preferredGenres = preferredGenres; }

    public Integer getMinPopularity() { return minPopularity; }
    public void setMinPopularity(Integer minPopularity) { this.minPopularity = minPopularity; }

    public Integer getMaxPopularity() { return maxPopularity; }
    public void setMaxPopularity(Integer maxPopularity) { this.maxPopularity = maxPopularity; }

    public Integer getTargetDuration() { return targetDuration; }
    public void setTargetDuration(Integer targetDuration) { this.targetDuration = targetDuration; }

    public Integer getSongCount() { return songCount; }
    public void setSongCount(Integer songCount) { this.songCount = songCount; }
} 