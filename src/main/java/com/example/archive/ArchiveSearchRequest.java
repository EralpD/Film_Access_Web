package com.example.archive;

import com.example.omdb.dto.OmdbType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


public class ArchiveSearchRequest {


    private String query;


    @Min(value = 1888, message = "Start year isn't available.")
    @Max(value = 2100, message = "Start year isn't available.")
    private Integer yearFrom;


    @Min(value = 1888, message = "Final year isn't available.")
    @Max(value = 2100, message = "Final year isn't available.")
    private Integer yearTo;


    private OmdbType type;


    private String genre;


    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }




    public Integer getYearFrom() {
        return yearFrom;
    }


    public void setYearFrom(Integer yearFrom) {
        this.yearFrom = yearFrom;
    }




    public Integer getYearTo() {
        return yearTo;
    }


    public void setYearTo(Integer yearTo) {
        this.yearTo = yearTo;
    }




    public OmdbType getType() {
        return type;
    }


    public void setType(OmdbType type) {
        this.type = type;
    }




    public String getGenre() {
        return genre;
    }


    public void setGenre(String genre) {
        this.genre = genre;
    }




    @AssertTrue(
        message = "Başlangıç yılı bitiş yılından büyük olamaz."
    )
    public boolean isYearRangeValid() {


        if (yearFrom == null || yearTo == null) {
            return true;
        }


        return yearFrom <= yearTo;
    }




    public boolean hasSemanticQuery() {
        return query != null && !query.isBlank();
    }




    public boolean hasGenre() {
        return genre != null && !genre.isBlank();
    }




    public boolean hasAnyFilter() {


        return hasSemanticQuery()
                || yearFrom != null
                || yearTo != null
                || type != null
                || hasGenre();
    }
}
