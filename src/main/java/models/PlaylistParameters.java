package models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parameters for playlist generation
 */
public class PlaylistParameters {
    private String name;
    private int songCount;
    private int minDuration; // in seconds
    private int maxDuration; // in seconds
    private boolean preferImportedMusic;
    private PlaylistSelectionStrategy selectionStrategy;
    private Map<String, Set<String>> inclusionCriteria = new HashMap<>();
    private Map<String, Set<String>> exclusionCriteria = new HashMap<>();
    
    {
        // Initialize all criteria sets
        String[] criteriaTypes = {"genres", "artists", "albums", "moods", "decades", "languages", "countries"};
        for (String type : criteriaTypes) {
            inclusionCriteria.put(type, new HashSet<>());
            exclusionCriteria.put(type, new HashSet<>());
        }
    }
    
    public enum PlaylistSelectionStrategy {
        RANDOM, POPULAR, DIVERSE, BALANCED
    }
    
    public PlaylistParameters() {
        this.name = "New Playlist";
        this.songCount = 20;
        this.minDuration = 0;
        this.maxDuration = Integer.MAX_VALUE;
        this.preferImportedMusic = false;
        this.selectionStrategy = PlaylistSelectionStrategy.BALANCED;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public int getMinDuration() {
        return minDuration;
    }

    public void setMinDuration(int minDuration) {
        this.minDuration = minDuration;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(int maxDuration) {
        this.maxDuration = maxDuration;
    }

    public boolean isPreferImportedMusic() {
        return preferImportedMusic;
    }

    public void setPreferImportedMusic(boolean preferImportedMusic) {
        this.preferImportedMusic = preferImportedMusic;
    }

    public PlaylistSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public void setSelectionStrategy(PlaylistSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }

    public Map<String, Set<String>> getInclusionCriteria() {
        return inclusionCriteria;
    }

    public void setInclusionCriteria(Map<String, Set<String>> inclusionCriteria) {
        this.inclusionCriteria = inclusionCriteria;
    }

    public void setInclusionCriteria(String type, Set<String> values) {
        inclusionCriteria.put(type, values);
    }

    public Map<String, Set<String>> getExclusionCriteria() {
        return exclusionCriteria;
    }

    public void setExclusionCriteria(Map<String, Set<String>> exclusionCriteria) {
        this.exclusionCriteria = exclusionCriteria;
    }

    public void setExclusionCriteria(String type, Set<String> values) {
        exclusionCriteria.put(type, values);
    }

    public void addInclusionCriterion(String type, String value) {
        inclusionCriteria.computeIfAbsent(type, k -> new HashSet<>()).add(value);
    }

    public void addExclusionCriterion(String type, String value) {
        exclusionCriteria.computeIfAbsent(type, k -> new HashSet<>()).add(value);
    }

    public void removeInclusionCriterion(String type, String value) {
        Set<String> criteria = inclusionCriteria.get(type);
        if (criteria != null) {
            criteria.remove(value);
            if (criteria.isEmpty()) {
                inclusionCriteria.remove(type);
            }
        }
    }

    public void removeExclusionCriterion(String type, String value) {
        Set<String> criteria = exclusionCriteria.get(type);
        if (criteria != null) {
            criteria.remove(value);
            if (criteria.isEmpty()) {
                exclusionCriteria.remove(type);
            }
        }
    }
}
