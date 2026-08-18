package com.example.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.example.archive.option.ArchiveSortOption;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class ArchiveSortOptionTest {


    @Test
    void shouldResolveAllowedSort() {

        ArchiveSortOption result =
                ArchiveSortOption.resolve(
                        "title,asc",
                        false
                );


        assertEquals(
                ArchiveSortOption.TITLE_ASC,
                result
        );
    }


    @Test
    void shouldRejectUnknownSort() {

        ArchiveSortOption result =
                ArchiveSortOption.resolve(
                        "password,asc",
                        false
                );


        assertEquals(
                ArchiveSortOption.ADDED_AT_DESC,
                result
        );
    }


    @Test
    void shouldRejectSqlLikeSortInput() {

        ArchiveSortOption result =
                ArchiveSortOption.resolve(
                        "title; DROP TABLE users;",
                        false
                );


        assertEquals(
                ArchiveSortOption.ADDED_AT_DESC,
                result
        );
    }


    @Test
    void shouldNotAllowRelevanceForStructuredSearch() {

        ArchiveSortOption result =
                ArchiveSortOption.resolve(
                        "relevance,desc",
                        false
                );


        assertEquals(
                ArchiveSortOption.ADDED_AT_DESC,
                result
        );
    }


    @Test
    void shouldAllowRelevanceForSemanticSearch() {

        ArchiveSortOption result =
                ArchiveSortOption.resolve(
                        "relevance,desc",
                        true
                );


        assertEquals(
                ArchiveSortOption.RELEVANCE_DESC,
                result
        );
    }
}

