package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoviesApiTest {
    private static final int PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private MoviesServer server;
    private Gson gson;
    private HttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MoviesServer(PORT);
        server.start();

        gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        client = HttpClient.newHttpClient();

        try {
            clearStore();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to clear store", e);
        }
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private void clearStore() throws IOException, InterruptedException {
        // Получаем все фильмы и удаляем их
        HttpResponse<String> response = sendGetRequest("/movies");
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        for (Movie movie : movies) {
            sendDeleteRequest("/movies/" + movie.getId());
        }
    }

    private HttpResponse<String> sendGetRequest(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPostRequest(String path, Object body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPostRequestWithContentType(String path, Object body, String contentType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendDeleteRequest(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPutRequest(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldReturnEmptyListWhenNoMovies() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void shouldReturnListWithPreviouslyAddedMovies() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Test Movie", 2024);
        sendPostRequest("/movies", movieToAdd);

        HttpResponse<String> response = sendGetRequest("/movies");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertEquals(1, movies.size());
        assertEquals("Test Movie", movies.get(0).getTitle());
        assertEquals(2024, movies.get(0).getYear());
    }

    @Test
    void shouldAddMovieWithValidData() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Valid Movie", 2024);
        HttpResponse<String> response = sendPostRequest("/movies", movieToAdd);

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertNotNull(createdMovie.getId());
        assertEquals("Valid Movie", createdMovie.getTitle());
        assertEquals(2024, createdMovie.getYear());
    }

    @Test
    void shouldAddMovieWithMinimalYear() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Oldest Movie", 1888);
        HttpResponse<String> response = sendPostRequest("/movies", movieToAdd);

        assertEquals(201, response.statusCode());
        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(1888, createdMovie.getYear());
    }

    @Test
    void shouldAddMovieWithMaximalYear() throws IOException, InterruptedException {
        int maxYear = Year.now().getValue() + 1;
        Movie movieToAdd = new Movie("Future Movie", maxYear);
        HttpResponse<String> response = sendPostRequest("/movies", movieToAdd);

        assertEquals(201, response.statusCode());
        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(maxYear, createdMovie.getYear());
    }

    @Test
    void shouldReturnErrorWhenTitleIsEmpty() throws IOException, InterruptedException {
        Movie invalidMovie = new Movie("", 2024);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
        Map<String, Object> error = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("Ошибка валидации", error.get("error"));
        assertTrue(((List<?>) error.get("details")).stream()
                .anyMatch(d -> d.toString().contains("название не должно быть пустым")));
    }

    @Test
    void shouldReturnErrorWhenTitleIsBlank() throws IOException, InterruptedException {
        Movie invalidMovie = new Movie("   ", 2024);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenTitleTooLong() throws IOException, InterruptedException {
        String longTitle = "a".repeat(101);
        Movie invalidMovie = new Movie(longTitle, 2024);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
        Map<String, Object> error = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
        assertTrue(((List<?>) error.get("details")).stream()
                .anyMatch(d -> d.toString().contains("не более 100 символов")));
    }

    @Test
    void shouldAcceptTitleWith100Chars() throws IOException, InterruptedException {
        String validTitle = "a".repeat(100);
        Movie validMovie = new Movie(validTitle, 2024);
        HttpResponse<String> response = sendPostRequest("/movies", validMovie);

        assertEquals(201, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenYearTooLow() throws IOException, InterruptedException {
        Movie invalidMovie = new Movie("Old Movie", 1887);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
        Map<String, Object> error = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
        int currentYear = Year.now().getValue();
        assertTrue(((List<?>) error.get("details")).stream()
                .anyMatch(d -> d.toString().contains(String.format("между 1888 и %d", currentYear + 1))));
    }

    @Test
    void shouldReturnErrorWhenYearTooHigh() throws IOException, InterruptedException {
        int currentYear = Year.now().getValue();
        Movie invalidMovie = new Movie("Future Movie", currentYear + 2);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenYearIsNegative() throws IOException, InterruptedException {
        Movie invalidMovie = new Movie("Negative Year", -1);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenYearIsZero() throws IOException, InterruptedException {
        Movie invalidMovie = new Movie("Zero Year", 0);
        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenInvalidContentType() throws IOException, InterruptedException {
        Movie movie = new Movie("Valid Movie", 2024);
        HttpResponse<String> response = sendPostRequestWithContentType("/movies", movie, "text/plain");

        assertEquals(415, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Неподдерживаемый Content-Type", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenNoContentType() throws IOException, InterruptedException {
        Movie movie = new Movie("Valid Movie", 2024);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenInvalidJson() throws IOException, InterruptedException {
        String invalidJson = "{invalid json}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        Map<String, Object> error = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("Ошибка валидации", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenEmptyBody() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnErrorWhenMissingRequiredFields() throws IOException, InterruptedException {
        String jsonWithoutTitle = "{\"year\":2024}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonWithoutTitle))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
    }

    @Test
    void shouldReturnMovieById() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Find Me", 2023);
        HttpResponse<String> postResponse = sendPostRequest("/movies", movieToAdd);
        Movie addedMovie = gson.fromJson(postResponse.body(), Movie.class);

        HttpResponse<String> response = sendGetRequest("/movies/" + addedMovie.getId());

        assertEquals(200, response.statusCode());
        Movie foundMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(addedMovie.getId(), foundMovie.getId());
        assertEquals("Find Me", foundMovie.getTitle());
        assertEquals(2023, foundMovie.getYear());
    }

    @Test
    void shouldReturnErrorWhenMovieNotFound() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies/99999");

        assertEquals(404, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Фильм не найден", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenIdIsNotNumber() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies/abc");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenIdIsNegative() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies/-1");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenIdIsZero() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies/0");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenIdIsFloat() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies/1.5");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldDeleteMovieById() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Delete Me", 2024);
        HttpResponse<String> postResponse = sendPostRequest("/movies", movieToAdd);
        Movie addedMovie = gson.fromJson(postResponse.body(), Movie.class);

        HttpResponse<String> response = sendDeleteRequest("/movies/" + addedMovie.getId());

        assertEquals(204, response.statusCode());

        HttpResponse<String> getResponse = sendGetRequest("/movies/" + addedMovie.getId());
        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void shouldReturnErrorWhenDeletingNonExistentMovie() throws IOException, InterruptedException {
        HttpResponse<String> response = sendDeleteRequest("/movies/99999");

        assertEquals(404, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Фильм не найден", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenDeleteIdIsNotNumber() throws IOException, InterruptedException {
        HttpResponse<String> response = sendDeleteRequest("/movies/abc");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenDeleteIdIsNegative() throws IOException, InterruptedException {
        HttpResponse<String> response = sendDeleteRequest("/movies/-5");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный ID", error.get("error"));
    }

    @Test
    void shouldReturnMoviesByYear() throws IOException, InterruptedException {
        Movie movie2022 = new Movie("Movie 2022", 2022);
        Movie movie2023a = new Movie("Movie 2023 A", 2023);
        Movie movie2023b = new Movie("Movie 2023 B", 2023);
        Movie movie2024 = new Movie("Movie 2024", 2024);

        sendPostRequest("/movies", movie2022);
        sendPostRequest("/movies", movie2023a);
        sendPostRequest("/movies", movie2023b);
        sendPostRequest("/movies", movie2024);

        HttpResponse<String> response = sendGetRequest("/movies?year=2023");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2023));
    }

    @Test
    void shouldReturnEmptyListWhenNoMoviesForYear() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies?year=1990");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenYearParamIsNotNumber() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies?year=abc");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный параметр запроса — 'year'", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenYearParamIsEmpty() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies?year=");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный параметр запроса — 'year'", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenYearParamIsNegative() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies?year=-2020");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный параметр запроса — 'year'", error.get("error"));
    }

    @Test
    void shouldReturnErrorWhenYearParamIsFloat() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/movies?year=2024.5");

        assertEquals(400, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Некорректный параметр запроса — 'year'", error.get("error"));
    }

    @Test
    void shouldReturnMoviesWithYearParamAndOtherParams() throws IOException, InterruptedException {
        Movie movie = new Movie("Test Movie", 2024);
        sendPostRequest("/movies", movie);

        HttpResponse<String> response = sendGetRequest("/movies?year=2024&extra=param");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertEquals(1, movies.size());
        assertEquals(2024, movies.get(0).getYear());
    }

    @Test
    void shouldHandleYearParamWithLeadingZeros() throws IOException, InterruptedException {
        Movie movie = new Movie("Test Movie", 2024);
        sendPostRequest("/movies", movie);

        HttpResponse<String> response = sendGetRequest("/movies?year=02024");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new TypeToken<List<Movie>>(){}.getType());
        assertEquals(1, movies.size());
        assertEquals(2024, movies.get(0).getYear());
    }

    @Test
    void shouldReturn405ForUnsupportedMethod() throws IOException, InterruptedException {
        HttpResponse<String> response = sendPutRequest("/movies");

        assertEquals(405, response.statusCode());
        Map<String, String> error = gson.fromJson(response.body(), new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Метод не поддерживается", error.get("error"));
    }

    @Test
    void shouldReturn405ForUnsupportedMethodWithId() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/1"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    void shouldReturn404ForNonExistentEndpoint() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGetRequest("/unknown");

        assertEquals(404, response.statusCode());
    }

    @Test
    void allSuccessfulResponsesHaveCorrectContentType() throws IOException, InterruptedException {
        HttpResponse<String> getAllResponse = sendGetRequest("/movies");
        assertTrue(getAllResponse.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        Movie movie = new Movie("ContentType Test", 2024);
        HttpResponse<String> postResponse = sendPostRequest("/movies", movie);
        assertTrue(postResponse.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        Movie added = gson.fromJson(postResponse.body(), Movie.class);
        HttpResponse<String> getByIdResponse = sendGetRequest("/movies/" + added.getId());
        assertTrue(getByIdResponse.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        HttpResponse<String> getByYearResponse = sendGetRequest("/movies?year=2024");
        assertTrue(getByYearResponse.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        HttpResponse<String> deleteResponse = sendDeleteRequest("/movies/" + added.getId());
        assertEquals(204, deleteResponse.statusCode());
    }
}