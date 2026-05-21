package de.digitalservice.cop.micronaut;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// transactional = false: each DB operation commits immediately,
// so we can observe the real interplay between cache and DB
@MicronautTest(transactional = false)
class BookServiceCacheTest {

    @Inject BookService service;
    @Inject BookRepository repository;
    @Inject CacheManager<SyncCache<?>> cacheManager;

    private Long testBookId;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("books").invalidateAll();
        testBookId = repository.save(new Book(null, "Cache Test Book", "Test Author", 2024)).getId();
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCache("books").invalidateAll();
        repository.findById(testBookId).ifPresent(b -> repository.deleteById(testBookId));
    }

    @Test
    @DisplayName("Cache hit: second findById returns stale cached value, not DB update")
    void findByIdReturnsCachedResult() {
        service.findById(testBookId); // cache miss → loads "Cache Test Book" into cache

        // Update DB directly, bypassing service — cache stays stale
        Book stale = repository.findById(testBookId).orElseThrow();
        stale.setTitle("Updated Directly in DB");
        repository.update(stale);

        Optional<Book> result = service.findById(testBookId); // cache HIT → returns old title
        assertThat(result.map(Book::getTitle)).hasValue("Cache Test Book");
    }

    @Test
    @DisplayName("save() evicts cache — next findById reads fresh value from DB")
    void saveInvalidatesCache() {
        service.findById(testBookId); // populate cache

        Book updated = repository.findById(testBookId).orElseThrow();
        updated.setTitle("Updated via Service");
        service.save(updated); // @CacheInvalidate — clears all entries

        Optional<Book> result = service.findById(testBookId); // cache miss → fresh DB read
        assertThat(result.map(Book::getTitle)).hasValue("Updated via Service");
    }

    @Test
    @DisplayName("Micronaut: warmupCache() goes through compile-time interceptor → cache works!")
    void selfInvocationRespectsCacheInMicronaut() {
        service.findById(testBookId); // populate cache with "Cache Test Book"

        // Update DB directly (bypassing service and its cache invalidation)
        Book updated = repository.findById(testBookId).orElseThrow();
        updated.setTitle("Updated in DB");
        repository.update(updated);

        // warmupCache() calls this.findById() — Micronaut's 'this' = BookService$Intercepted
        // → @Cacheable interceptor IS triggered → cache HIT → returns original cached value
        Optional<Book> result = service.warmupCache(testBookId);
        assertThat(result.map(Book::getTitle)).hasValue("Cache Test Book"); // served from cache!
    }
}
