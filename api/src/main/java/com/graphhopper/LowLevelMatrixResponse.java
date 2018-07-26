package com.graphhopper;

import java.util.ArrayList;
import java.util.List;

public class LowLevelMatrixResponse {
    private List<Double> distances = new ArrayList<>();
    private List<Long> durations = new ArrayList<>();

    public void addDistance(double distance) {
        distances.add(distance);
    }

    public void addDuration(long duration) {
        durations.add(duration);
    }

    public List<Double> getDistances() {
        return distances;
    }

    public List<Long> getDurations() {
        return durations;
    }
}
