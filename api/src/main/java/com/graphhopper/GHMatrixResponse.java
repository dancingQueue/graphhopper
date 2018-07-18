package com.graphhopper;


import java.util.ArrayList;
import java.util.List;


public class GHMatrixResponse {
    private final List<GHResponse> responses = new ArrayList<>();

    public GHMatrixResponse() {
    }

    public void addResponse(GHResponse response) {
        responses.add(response);
    }

    public List<GHResponse> getResponses() {
        return responses;
    }

    public boolean hasErrors() {
        for (GHResponse response : responses) {
            if (response.hasErrors()) {
                return true;
            }
        }
        return false;
    }
}
