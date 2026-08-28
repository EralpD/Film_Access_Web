package com.example.archive;

import com.example.archive.option.ArchiveSortOption;
import com.example.omdb.dto.OmdbType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;


public class ArchiveSearchRequest {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Integer page = DEFAULT_PAGE;
    private Integer size = DEFAULT_SIZE;
    private String sort = "auto";



    @Size(max = 500, message = "Search text must be 500 characters or fewer.")
    private String query;


    @Min(value = 1888, message = "Start year isn't available.")
    @Max(value = 2100, message = "Start year isn't available.")
    private Integer yearFrom;


    @Min(value = 1888, message = "Final year isn't available.")
    @Max(value = 2100, message = "Final year isn't available.")
    private Integer yearTo;


    private OmdbType type;


    @Size(max = 100, message = "Genre must be 100 characters or fewer.")
    private String genre;

    @Size(max = 200, message = "Actor name must be 200 characters or fewer.")
    private String actor;
    @Size(max = 200, message = "Director name must be 200 characters or fewer.")
    private String director;
    private boolean semantic;
    public boolean isSemantic() { return semantic; }
    public void setSemantic(boolean semantic) { this.semantic = semantic; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public boolean hasPersonFilter() {
        return (actor != null && !actor.isBlank()) || (director != null && !director.isBlank());
    }


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
        message = "Start year cannot be later than end year."
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
                || hasGenre() || hasPersonFilter();
    }

    public Integer getPage() {
    return page;
}


public void setPage(Integer page) {
    this.page = page;
}


public Integer getSize() {
    return size;
}


public void setSize(Integer size) {
    this.size = size;
}


public String getSort() {
    return sort;
}


public void setSort(String sort) {
    this.sort = sort;
}


public int normalizedPage() {

    if (page == null || page < 0) {
        return DEFAULT_PAGE;
    }

    return page;
}


public int normalizedSize() {

    if (size == null || size < 1) {
        return DEFAULT_SIZE;
    }

    return Math.min(
            size,
            MAX_SIZE
    );
}


public ArchiveSortOption resolveSortOption() {

    return ArchiveSortOption.resolve(
            sort,
            hasSemanticQuery()
    );
}

}
