package com.example.archive.dto;

import com.example.archive.option.ArchiveSortOption;
import com.example.omdb.dto.OmdbType;

public class ArchiveSearchRequest {

    public static final int DEFAULT_PAGE = 0;

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;


    private String query;

    private Integer yearFrom;

    private Integer yearTo;

    private OmdbType type;

    private String genre;


    private Integer page = DEFAULT_PAGE;

    private Integer size = DEFAULT_SIZE;

    private String sort;


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


    public boolean hasSemanticQuery() {

        return query != null
                && !query.isBlank();
    }


    public boolean hasGenre() {

        return genre != null
                && !genre.isBlank();
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
