package de.digitalservice.cop.micronaut;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;

import java.util.List;

/**
 * {@code @Controller} is the Micronaut equivalent of Spring's {@code @RestController}
 * ({@code @Controller} + {@code @ResponseBody} combined). The HTTP route table is generated
 * at compile time into {@code $BookController$Definition.class} — not discovered via reflection at startup.
 */
@Controller("/books")
public class BookController {

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    @Get
    public List<Book> list() {
        return books.findAll();
    }

    /** {@code @Body} = Spring's {@code @RequestBody}. {@code HttpResponse} = Spring's {@code ResponseEntity}. */
    @Post
    public HttpResponse<Book> create(@Body Book book) {
        return HttpResponse.created(books.save(book));
    }

    /** No {@code @PathVariable} annotation needed — Micronaut binds path segments by parameter name
     *  using compile-time {@code -parameters} flag metadata, not runtime reflection. */
    @Get("/{id}")
    public HttpResponse<Book> get(Long id) {
        return books.findById(id)
            .map(HttpResponse::ok)
            .orElse(HttpResponse.notFound());
    }

    /** {@code @QueryValue} = Spring's {@code @RequestParam}. */
    @Get("/search")
    public List<Book> search(@QueryValue String author) {
        return books.findByAuthor(author);
    }
}
