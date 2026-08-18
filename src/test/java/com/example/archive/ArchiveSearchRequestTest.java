package com.example.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.example.archive.option.ArchiveSortOption;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class ArchiveSearchRequestTest {


    @Test
    void shouldUseDefaultPagination() {

        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        assertEquals(
                0,
                request.normalizedPage()
        );

        assertEquals(
                20,
                request.normalizedSize()
        );
    }


    @Test
    void shouldPreventNegativePage() {

        ArchiveSearchRequest request =
                new ArchiveSearchRequest();

        request.setPage(-50);


        assertEquals(
                0,
                request.normalizedPage()
        );
    }


    @Test
    void shouldLimitPageSizeToOneHundred() {

        ArchiveSearchRequest request =
                new ArchiveSearchRequest();

        request.setSize(50000);


        assertEquals(
                100,
                request.normalizedSize()
        );
    }


    @Test
    void shouldUseAddedAtAsDefaultStructuredSort() {

        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        assertEquals(
                ArchiveSortOption.ADDED_AT_DESC,
                request.resolveSortOption()
        );
    }


    @Test
    void shouldUseRelevanceAsDefaultSemanticSort() {

        ArchiveSearchRequest request =
                new ArchiveSearchRequest();

        request.setQuery(
                "movie about dreams"
        );


        assertEquals(
                ArchiveSortOption.RELEVANCE_DESC,
                request.resolveSortOption()
        );
    }
}

