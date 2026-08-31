package com.example.discovery;

import com.example.archive.ArchiveSearchRequest;
import com.example.omdb.dto.OmdbType;
import java.util.Locale;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class DiscoverySearchRequest {
    @Size(max = 500, message = "Search text must be 500 characters or fewer.")
    private String query;
    @Min(1888) @Max(2100)
    private Integer year;
    private String type;
    private String scope = "omdb";
    private String catalogSort = "auto";
    private Integer catalogPage = 0;
    private Integer omdbPage = 1;
    private Integer size = 20;

    // Legacy /catalog parameters retained during compatibility redirects.
    @Min(1888) @Max(2100)
    private Integer yearFrom;
    @Min(1888) @Max(2100)
    private Integer yearTo;
    @Size(max = 100)
    private String genre;
    @Size(max = 200)
    private String actor;
    @Size(max = 200)
    private String director;
    private boolean semantic;

    public boolean hasQuery() { return query != null && !query.isBlank(); }
    @AssertTrue(message = "Start year cannot be later than end year.")
    public boolean isYearRangeValid() {
        return yearFrom == null || yearTo == null || yearFrom <= yearTo;
    }
    public boolean hasSearchCriteria() {
        return hasQuery() || year != null || yearFrom != null || yearTo != null
                || (type != null && !type.isBlank())
                || (genre != null && !genre.isBlank())
                || (actor != null && !actor.isBlank())
                || (director != null && !director.isBlank());
    }
    public String normalizedQuery() { return hasQuery() ? query.trim() : null; }
    public SearchScope resolvedScope() { return SearchScope.from(scope); }
    public int normalizedCatalogPage() { return catalogPage == null || catalogPage < 0 ? 0 : catalogPage; }
    public int normalizedOmdbPage() { return omdbPage == null || omdbPage < 1 ? 1 : omdbPage; }
    public int normalizedSize() { return size == null || size < 1 ? 20 : Math.min(size, 100); }

    public OmdbType resolvedType() {
        if (type == null || type.isBlank()) return null;
        try { return OmdbType.valueOf(type.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public ArchiveSearchRequest toCatalogRequest() {
        ArchiveSearchRequest request = new ArchiveSearchRequest();
        request.setQuery(normalizedQuery());
        request.setYearFrom(yearFrom != null ? yearFrom : year);
        request.setYearTo(yearTo != null ? yearTo : year);
        request.setType(resolvedType());
        request.setGenre(genre);
        request.setActor(actor);
        request.setDirector(director);
        request.setSemantic(semantic);
        request.setSort(catalogSort == null || catalogSort.isBlank() ? "auto" : catalogSort);
        request.setPage(normalizedCatalogPage());
        request.setSize(normalizedSize());
        return request;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = SearchScope.from(scope).requestValue(); }
    public String getCatalogSort() { return catalogSort; }
    public void setCatalogSort(String catalogSort) { this.catalogSort = catalogSort; }
    public Integer getCatalogPage() { return catalogPage; }
    public void setCatalogPage(Integer catalogPage) { this.catalogPage = catalogPage; }
    public Integer getOmdbPage() { return omdbPage; }
    public void setOmdbPage(Integer omdbPage) { this.omdbPage = omdbPage; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Integer getYearFrom() { return yearFrom; }
    public void setYearFrom(Integer yearFrom) { this.yearFrom = yearFrom; }
    public Integer getYearTo() { return yearTo; }
    public void setYearTo(Integer yearTo) { this.yearTo = yearTo; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public boolean isSemantic() { return semantic; }
    public void setSemantic(boolean semantic) { this.semantic = semantic; }

    // Compatibility aliases for old /catalog links.
    public void setSort(String sort) { this.catalogSort = sort; }
    public void setPage(Integer page) { this.catalogPage = page; }
}
