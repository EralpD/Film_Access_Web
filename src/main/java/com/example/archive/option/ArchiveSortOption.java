package com.example.archive.option;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Sort;

public enum ArchiveSortOption {

    ADDED_AT_DESC(
            "addedAt,desc",
            "Newest added"
    ),

    ADDED_AT_ASC(
            "addedAt,asc",
            "Oldest added"
    ),

    TITLE_ASC(
            "title,asc",
            "Title A-Z"
    ),

    TITLE_DESC(
            "title,desc",
            "Title Z-A"
    ),

    YEAR_DESC(
            "year,desc",
            "Newest release"
    ),

    YEAR_ASC(
            "year,asc",
            "Oldest release"
    ),

    RELEVANCE_DESC(
            "relevance,desc",
            "Most relevant"
    );


    private final String requestValue;

    private final String label;


    ArchiveSortOption(
            String requestValue,
            String label
    ) {
        this.requestValue = requestValue;
        this.label = label;
    }


    public String getRequestValue() {
        return requestValue;
    }


    public String getLabel() {
        return label;
    }


    public static ArchiveSortOption resolve(
            String rawValue,
            boolean semanticSearch
    ) {

        ArchiveSortOption defaultOption =
                semanticSearch
                        ? RELEVANCE_DESC
                        : ADDED_AT_DESC;


        if (rawValue == null
                || rawValue.isBlank()) {

            return defaultOption;
        }


        ArchiveSortOption resolved =
                Arrays.stream(values())
                        .filter(option ->
                                option.requestValue
                                        .equalsIgnoreCase(
                                                rawValue.trim()
                                        )
                        )
                        .findFirst()
                        .orElse(defaultOption);


        /*
         * Relevance yalnızca semantic search için anlamlıdır.
         */
        if (!semanticSearch
                && resolved == RELEVANCE_DESC) {

            return ADDED_AT_DESC;
        }


        return resolved;
    }


    public static List<ArchiveSortOption> availableFor(
            boolean semanticSearch
    ) {

        if (semanticSearch) {
            return List.of(values());
        }


        return Arrays.stream(values())
                .filter(option ->
                        option != RELEVANCE_DESC
                )
                .toList();
    }


    public Sort toJpaSort() {

        return switch (this) {

            case ADDED_AT_DESC ->
                    Sort.by(
                            Sort.Order.desc("addedAt"),
                            Sort.Order.desc("id")
                    );

            case ADDED_AT_ASC ->
                    Sort.by(
                            Sort.Order.asc("addedAt"),
                            Sort.Order.asc("id")
                    );

            case TITLE_ASC ->
                    Sort.by(
                            Sort.Order.asc("film.title"),
                            Sort.Order.asc("id")
                    );

            case TITLE_DESC ->
                    Sort.by(
                            Sort.Order.desc("film.title"),
                            Sort.Order.desc("id")
                    );

            case YEAR_DESC ->
                    Sort.by(
                            Sort.Order.desc("film.releaseYear"),
                            Sort.Order.desc("id")
                    );

            case YEAR_ASC ->
                    Sort.by(
                            Sort.Order.asc("film.releaseYear"),
                            Sort.Order.asc("id")
                    );

            /*
             * Structured search'te RELEVANCE zaten
             * resolve() tarafından engellenir.
             */
            case RELEVANCE_DESC ->
                    Sort.by(
                            Sort.Order.desc("addedAt"),
                            Sort.Order.desc("id")
                    );
        };
    }
}

