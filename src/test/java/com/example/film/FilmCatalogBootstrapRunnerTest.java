package com.example.film;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.film.service.FilmCatalogBootstrapService;
import com.example.film.service.FilmCatalogBootstrapService.BootstrapReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmCatalogBootstrapRunnerTest {

    @Mock
    FilmCatalogBootstrapService service;

    @Test
    void doesNotStartWhenDisabled() {
        var runner = new FilmCatalogBootstrapRunner(service, "basics.tsv.gz", false);

        assertThat(runner.start()).isFalse();
        verifyNoInteractions(service);
    }

    @Test
    void runsInBackgroundAndRejectsOverlappingTriggers() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        var report = new BootstrapReport(1, 1, 0, 1, 0, 0, false, Duration.ZERO, List.of());
        when(service.bootstrap(any(Path.class))).thenAnswer(invocation -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test worker timed out");
            }
            return report;
        });
        var runner = new FilmCatalogBootstrapRunner(service, "basics.tsv.gz", true);

        try {
            assertThat(runner.start()).isTrue();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.get()).isNotSameAs(Thread.currentThread());
            assertThat(worker.get().isVirtual()).isTrue();
            assertThat(runner.start()).isFalse();
            verify(service).bootstrap(Path.of("basics.tsv.gz").toAbsolutePath().normalize());
        } finally {
            release.countDown();
        }
        worker.get().join(5_000);

        assertThat(runner.start()).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(service, times(2)).bootstrap(any(Path.class)));
    }

    @Test
    void allowsNextTriggerAfterImportFailure() throws Exception {
        var worker = new AtomicReference<Thread>();
        var entered = new CountDownLatch(1);
        when(service.bootstrap(any(Path.class))).thenAnswer(invocation -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            throw new IOException("Missing dataset");
        });
        var runner = new FilmCatalogBootstrapRunner(service, "basics.tsv.gz", true);

        assertThat(runner.start()).isTrue();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        worker.get().join(5_000);

        assertThat(runner.start()).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(service, times(2)).bootstrap(any(Path.class)));
    }
}
