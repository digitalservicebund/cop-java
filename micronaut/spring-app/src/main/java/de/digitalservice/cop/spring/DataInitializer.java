package de.digitalservice.cop.spring;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final BookRepository books;

    public DataInitializer(BookRepository books) {
        this.books = books;
    }

    @Override
    public void run(ApplicationArguments args) {
        books.save(new Book(null, "Clean Code", "Robert C. Martin", 2008));
        books.save(new Book(null, "The Pragmatic Programmer", "David Thomas", 1999));
        books.save(new Book(null, "Designing Data-Intensive Applications", "Martin Kleppmann", 2017));
        books.save(new Book(null, "Building Microservices", "Sam Newman", 2021));
    }
}
