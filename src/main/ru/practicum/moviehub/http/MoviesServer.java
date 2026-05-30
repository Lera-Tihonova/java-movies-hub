package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer(MoviesStore store, int port) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/movies", new MoviesHandler(store));
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен на порту 8080");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}