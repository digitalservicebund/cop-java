package de.digitalservice.cop.spring;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@CacheConfig(cacheNames = "books")
public class BookService {

    private final BookRepository repository;

    BookService(BookRepository repository) {
        this.repository = repository;
    }

    @Cacheable
    public Optional<Book> findById(Long id) {
        return repository.findById(id);
    }

    @Cacheable
    public List<Book> findAll() {
        return repository.findAll();
    }

    @CacheEvict(allEntries = true)
    public Book save(Book book) {
        return repository.save(book);
    }

    @CacheEvict(allEntries = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Book> findByAuthor(String author) {
        return repository.findByAuthorContainingIgnoreCase(author);
    }

    // ⚠ GOTCHA: Spring uses a runtime proxy. 'this' inside the bean refers to the
    // unwrapped original, not the proxy. So this.findById() bypasses @Cacheable entirely.
    // Run BookServiceCacheTest.selfInvocationBypassesCacheInSpring to see it fail.
    public Optional<Book> warmupCache(Long id) {
        return findById(id);
    }
}
