package de.digitalservice.cop.micronaut;

import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.Cacheable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * {@code @Singleton} = Spring's {@code @Service} / {@code @Component}.
 * Cache interceptors are generated at compile time into {@code $BookService$Definition$Intercepted.class};
 * Spring generates equivalent CGLIB subclasses in memory at startup.
 */
@Singleton
public class BookService {

    private final BookRepository repository;

    BookService(BookRepository repository) {
        this.repository = repository;
    }

    @Cacheable("books")
    public Optional<Book> findById(Long id) {
        return repository.findById(id);
    }

    @Cacheable("books")
    public List<Book> findAll() {
        return repository.findAll();
    }

    @CacheInvalidate(value = "books", all = true)
    public Book save(Book book) {
        return book.getId() != null ? repository.update(book) : repository.save(book);
    }

    @CacheInvalidate(value = "books", all = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Book> findByAuthor(String author) {
        return repository.findByAuthorContainsIgnoreCase(author);
    }

    public Optional<Book> warmupCache(Long id) {
        return findById(id);
    }
}
