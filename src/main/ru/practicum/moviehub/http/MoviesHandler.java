package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        System.out.println("Request: " + method + " " + path + (query != null ? "?" + query : ""));

        try {
            // GET /movies с query параметрами
            if ("GET".equals(method) && "/movies".equals(path) && query != null) {
                if (query.startsWith("year=") || query.contains("&year=")) {
                    String yearValue = extractYearValue(query);
                    handleGetMoviesByYear(exchange, yearValue);
                    return;
                }
                handleGetAllMovies(exchange);
                return;
            }

            // GET /movies
            if ("GET".equals(method) && "/movies".equals(path)) {
                handleGetAllMovies(exchange);
                return;
            }

            // POST /movies
            if ("POST".equals(method) && "/movies".equals(path)) {
                handlePostMovie(exchange);
                return;
            }

            // Обработка /movies/{id}
            Pattern pattern = Pattern.compile("/movies/([^/]+)");
            Matcher matcher = pattern.matcher(path);

            if (matcher.matches()) {
                String idStr = matcher.group(1);

                OptionalInt optionalId = parseId(idStr);
                if (optionalId.isEmpty()) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Некорректный ID");
                    sendJson(exchange, 400, error);
                    return;
                }

                int id = optionalId.getAsInt();
                if (id <= 0) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Некорректный ID");
                    sendJson(exchange, 400, error);
                    return;
                }

                if ("GET".equals(method)) {
                    handleGetMovieById(exchange, id);
                    return;
                }
                if ("DELETE".equals(method)) {
                    handleDeleteMovie(exchange, id);
                    return;
                }
                Map<String, String> error = new HashMap<>();
                error.put("error", "Метод не поддерживается");
                sendJson(exchange, 405, error);
                return;
            }

            if ("/movies".equals(path) && !"GET".equals(method) && !"POST".equals(method)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Метод не поддерживается");
                sendJson(exchange, 405, error);
                return;
            }

            Map<String, String> error = new HashMap<>();
            error.put("error", "Эндпоинт не найден");
            sendJson(exchange, 404, error);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", "Внутренняя ошибка сервера");
            sendJson(exchange, 500, error);
        }
    }

    private OptionalInt parseId(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(idStr));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private OptionalInt parseYear(String yearParam) {
        if (yearParam == null || yearParam.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(yearParam));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private String extractYearValue(String query) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.startsWith("year=")) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    return keyValue[1];
                } else if (keyValue.length == 1) {
                    return "";
                }
            }
        }
        return null;
    }

    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getAllMovies();
        sendJson(exchange, 200, movies);
    }

    private void handleGetMoviesByYear(HttpExchange exchange, String yearParam) throws IOException {
        if (yearParam == null || yearParam.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Некорректный параметр запроса — 'year'");
            sendJson(exchange, 400, error);
            return;
        }

        OptionalInt optionalYear = parseYear(yearParam);
        if (optionalYear.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Некорректный параметр запроса — 'year'");
            sendJson(exchange, 400, error);
            return;
        }

        int year = optionalYear.getAsInt();
        if (year < 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Некорректный параметр запроса — 'year'");
            sendJson(exchange, 400, error);
            return;
        }

        List<Movie> movies = store.getMoviesByYear(year);
        sendJson(exchange, 200, movies);
    }

    private void handlePostMovie(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.equals("application/json")) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Неподдерживаемый Content-Type");
            sendJson(exchange, 415, error);
            return;
        }

        String body = readBody(exchange);

        if (body == null || body.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка валидации");
            List<String> details = new ArrayList<>();
            details.add("Некорректный JSON");
            error.put("details", details);
            sendJson(exchange, 422, error);
            return;
        }

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка валидации");
            List<String> details = new ArrayList<>();
            details.add("Некорректный JSON");
            error.put("details", details);
            sendJson(exchange, 422, error);
            return;
        }

        List<String> validationErrors = validateMovie(movie);
        if (!validationErrors.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка валидации");
            error.put("details", validationErrors);
            sendJson(exchange, 422, error);
            return;
        }

        Movie createdMovie = store.addMovie(movie);
        sendJson(exchange, 201, createdMovie);
    }

    private void handleGetMovieById(HttpExchange exchange, int id) throws IOException {
        Movie movie = store.getMovieById(id);
        if (movie != null) {
            sendJson(exchange, 200, movie);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Фильм не найден");
            sendJson(exchange, 404, error);
        }
    }

    private void handleDeleteMovie(HttpExchange exchange, int id) throws IOException {
        boolean deleted = store.deleteMovie(id);
        if (deleted) {
            sendNoContent(exchange);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Фильм не найден");
            sendJson(exchange, 404, error);
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("Некорректные данные фильма");
            return errors;
        }

        int currentYear = Year.now().getValue();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название должно быть не более 100 символов");
        }

        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add(String.format("год должен быть между 1888 и %d", currentYear + 1));
        }

        return errors;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}