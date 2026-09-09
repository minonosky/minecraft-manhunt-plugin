package com.manhunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

// stores the current manhunt roles and each hunter's selected runner

public class ManhuntGame {

    private final List<UUID> runners = new ArrayList<>();
    private final Set<UUID> hunters = new LinkedHashSet<>();
    private final Map<UUID, Integer> trackingIndexes = new HashMap<>();

    public boolean addRunner(UUID playerId) {
        removeHunter(playerId);
        if (runners.contains(playerId)) {
            return false;
        }
        runners.add(playerId);
        return true;
    }

    public boolean addHunter(UUID playerId) {
        removeRunner(playerId);
        trackingIndexes.put(playerId, 0);
        return hunters.add(playerId);
    }

    public boolean removeRunner(UUID playerId) {
        return runners.remove(playerId);
    }

    public boolean removeHunter(UUID playerId) {
        trackingIndexes.remove(playerId);
        return hunters.remove(playerId);
    }

    public boolean isRunner(UUID playerId) {
        return runners.contains(playerId);
    }

    public boolean isHunter(UUID playerId) {
        return hunters.contains(playerId);
    }

    public int getTrackingIndex(UUID hunterId) {
        return trackingIndexes.getOrDefault(hunterId, 0);
    }

    public void setTrackingIndex(UUID hunterId, int index) {
        if (hunters.contains(hunterId)) {
            trackingIndexes.put(hunterId, index);
        }
    }

    public List<UUID> getRunners() {
        return Collections.unmodifiableList(runners);
    }

    public Set<UUID> getHunters() {
        return Collections.unmodifiableSet(hunters);
    }

    public void reset() {
        runners.clear();
        hunters.clear();
        trackingIndexes.clear();
    }
}
