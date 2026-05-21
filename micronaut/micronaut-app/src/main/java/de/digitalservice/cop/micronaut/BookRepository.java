package de.digitalservice.cop.micronaut;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Micronaut Data generates the repository implementation at compile time into bytecode.
 * Spring Data JPA generates it at runtime via reflection-based JDK dynamic proxies.
 */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {
    @Override
    List<Book> findAll();

    /** Method name parsed into a JPQL query at compile time by the {@code micronaut-data-processor}
     *  annotation processor. Spring Data parses the same convention at startup. */
    List<Book> findByAuthorContainsIgnoreCase(String author);
}
