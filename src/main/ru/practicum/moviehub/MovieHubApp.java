package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;

import java.io.IOException;

public class MovieHubApp {
    public static void main(String[] args) throws IOException {
        MoviesServer server = new MoviesServer(8080);
        server.start();
        System.out.println("MovieHub API is running on http://localhost:8080");
        System.out.println("Available endpoints:");
        System.out.println("  GET    /movies");
        System.out.println("  GET    /movies?year=YYYY");
        System.out.println("  GET    /movies/{id}");
        System.out.println("  POST   /movies");
        System.out.println("  DELETE /movies/{id}");
    }
}