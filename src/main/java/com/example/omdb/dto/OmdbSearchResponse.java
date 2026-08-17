package com.example.omdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;


public class OmdbSearchResponse {

    @JsonProperty("Search")
    private List<OmdbSearchItem> search;

    @JsonProperty("totalResults")
    private String totalResults;

    @JsonProperty("Response")
    private String response;

    @JsonProperty("Error")
    private String error;


    public OmdbSearchResponse() {
    }


    public List<OmdbSearchItem> getSearch() {
        return search;
    }


    public void setSearch(List<OmdbSearchItem> search) {
        this.search = search;
    }


    public String getTotalResults() {
        return totalResults;
    }


    public void setTotalResults(String totalResults) {
        this.totalResults = totalResults;
    }


    public String getResponse() {
        return response;
    }


    public void setResponse(String response) {
        this.response = response;
    }


    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }


    public boolean isSuccessful() {
        return "True".equalsIgnoreCase(response);
    }
}
