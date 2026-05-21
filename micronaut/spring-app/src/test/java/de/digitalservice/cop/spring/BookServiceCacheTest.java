package de.digitalservice.cop.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookServiceCacheTest {

    @Autowired BookService service;
    @Autowired BookRepository repository;
    @Autowired CacheManager cacheManager;

    private Long testBookId;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(name ->
            Objects.requireNonNull(cacheManager.getCache(name)).clear());
        testBookId = repository.save(new Book(null, "Cache Test Book", "Test Author", 2024)).getId();
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames().forEach(name ->
            Objects.requireNonNull(cacheManager.getCache(name)).clear());
        repository.findById(testBookId).ifPresent(repository::delete);
    }

    @Test
    @DisplayName("Cache hit: second findById returns stale cached value, not DB update")
    void findByIdReturnsCachedResult() {
        service.findById(testBookId); // cache miss → loads "Cache Test Book" into cache

        // Update DB directly, bypassing service — cache stays stale
        Book stale = repository.findById(testBookId).orElseThrow();
        stale.setTitle("Updated Directly in DB");
        repository.save(stale);

        Optional<Book> result = service.findById(testBookId); // cache HIT → returns old title
        assertThat(result.map(Book::getTitle)).hasValue("Cache Test Book");
    }

    @Test
    @DisplayName("save() evicts cache — next findById reads fresh value from DB")
    void saveInvalidatesCache() {
        service.findById(testBookId); // populate cache

        Book updated = repository.findById(testBookId).orElseThrow();
        updated.setTitle("Updated via Service");
        service.save(updated); // @CacheEvict — clears all entries in "books" cache

        Optional<Book> result = service.findById(testBookId); // cache miss → fresh DB read
        assertThat(result.map(Book::getTitle)).hasValue("Updated via Service");
    }

    @Test
    @DisplayName("Spring gotcha: warmupCache() bypasses the CGLIB proxy → always hits DB")
    void selfInvocationBypassesCacheInSpring() {
        service.findById(testBookId); // populate cache with "Cache Test Book"

        // Update DB directly (bypassing service and its cache eviction)
        Book updated = repository.findById(testBookId).orElseThrow();
        updated.setTitle("Updated in DB");
        repository.save(updated);

        // warmupCache() calls this.findById() — Spring's 'this' = original bean, not the proxy
        // → @Cacheable annotation is ignored → direct DB call → returns DB value
        Optional<Book> result = service.warmupCache(testBookId);
        assertThat(result.map(Book::getTitle)).hasValue("Updated in DB"); // got stale DB value
    }
}
