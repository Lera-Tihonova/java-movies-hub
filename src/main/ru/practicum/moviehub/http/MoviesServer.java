package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer(int port) throws IOException {
        this.store = new MoviesStore();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/movies", new MoviesHandler(store));
    }

    public void start() {
        server.start();
        System.out.println("Movies server started on port 8080");
    }

    public void stop() {
        server.stop(0);
    }
}