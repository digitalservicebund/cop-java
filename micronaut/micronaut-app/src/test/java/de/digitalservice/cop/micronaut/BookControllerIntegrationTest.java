package de.digitalservice.cop.micronaut;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// One annotation starts the full app on a random port.
// The @Client("/") below auto-wires to that port — no port configuration needed.
@MicronautTest
class BookControllerIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    @DisplayName("GET /books returns the 4 seeded books")
    void getAll_returns4Books() {
        List<Book> books = client.toBlocking()
            .retrieve(HttpRequest.GET("/books"), Argument.listOf(Book.class));

        assertThat(books).hasSize(4);
    }

    @Test
    @DisplayName("GET /books/1 returns Clean Code")
    void getById_returnsCorrectBook() {
        Book book = client.toBlocking()
            .retrieve(HttpRequest.GET("/books/1"), Book.class);

        assertThat(book.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("GET /books/search?author=Martin filters by author")
    void search_filtersByAuthor() {
        List<Book> books = client.toBlocking()
            .retrieve(HttpRequest.GET("/books/search?author=Martin"), Argument.listOf(Book.class));

        assertThat(books).allMatch(b -> b.getAuthor().contains("Martin"));
    }

    @Test
    @DisplayName("GET /books/999 returns 404")
    void getById_returns404ForMissingBook() {
        assertThatThrownBy(() ->
            client.toBlocking().retrieve(HttpRequest.GET("/books/999"), Book.class)
        ).isInstanceOf(HttpClientResponseException.class)
         .satisfies(e -> assertThat(((HttpClientResponseException) e).getStatus().getCode())
             .isEqualTo(404));
    }
}
