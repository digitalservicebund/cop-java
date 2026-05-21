package de.digitalservice.cop.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Starts the full application on a random port — the same binary that runs in production
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BookControllerIntegrationTest {

    @Autowired TestRestTemplate http;

    @Test
    @DisplayName("GET /books returns the 4 seeded books")
    void getAll_returns4Books() {
        ResponseEntity<List<Book>> response = http.exchange(
            "/books", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(4);
    }

    @Test
    @DisplayName("GET /books/1 returns Clean Code")
    void getById_returnsCorrectBook() {
        ResponseEntity<Book> response = http.getForEntity("/books/1", Book.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("GET /books/search?author=Martin filters by author")
    void search_filtersByAuthor() {
        ResponseEntity<List<Book>> response = http.exchange(
            "/books/search?author=Martin", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).allMatch(b -> b.getAuthor().contains("Martin"));
    }

    @Test
    @DisplayName("GET /books/999 returns 404")
    void getById_returns404ForMissingBook() {
        ResponseEntity<Void> response = http.getForEntity("/books/999", Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
