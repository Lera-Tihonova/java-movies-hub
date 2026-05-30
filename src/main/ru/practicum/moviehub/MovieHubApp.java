package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieHubApp {
    public static void main(String[] args) throws IOException {
        MoviesStore store = new MoviesStore();
        MoviesServer server = new MoviesServer(store, 8080);
        server.start();
    }
}