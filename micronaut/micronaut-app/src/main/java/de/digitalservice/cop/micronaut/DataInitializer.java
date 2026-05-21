package de.digitalservice.cop.micronaut;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;

@Singleton
public class DataInitializer implements ApplicationEventListener<ServerStartupEvent> {

    private final BookRepository books;

    public DataInitializer(BookRepository books) {
        this.books = books;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        books.save(new Book(null, "Clean Code", "Robert C. Martin", 2008));
        books.save(new Book(null, "The Pragmatic Programmer", "David Thomas", 1999));
        books.save(new Book(null, "Designing Data-Intensive Applications", "Martin Kleppmann", 2017));
        books.save(new Book(null, "Building Microservices", "Sam Newman", 2021));
    }
}
