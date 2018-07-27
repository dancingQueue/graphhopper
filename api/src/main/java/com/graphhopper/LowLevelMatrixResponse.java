package com.graphhopper;


public class LowLevelMatrixResponse {
    private double[] distances;
    private long[] durations;

    public LowLevelMatrixResponse(int size) {
        distances = new double[size];
        durations = new long[size];
    }

    public void setRoute(int index, double distance, long duration) {
        distances[index] = distance;
        durations[index] = duration;
    }

    public double[] getDistances() {
        return distances;
    }

    public long[] getDurations() {
        return durations;
    }
}
