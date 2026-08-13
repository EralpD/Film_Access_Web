package com.example.omdb.dto;


public enum OmdbType {

    MOVIE("movie"),
    SERIES("series"),
    EPISODE("episode");


    private final String apiValue;


    OmdbType(String apiValue) {
        this.apiValue = apiValue;
    }


    public String getApiValue() {
        return apiValue;
    }
}

