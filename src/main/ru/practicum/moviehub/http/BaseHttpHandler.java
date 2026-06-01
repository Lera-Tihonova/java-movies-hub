package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new GsonBuilder().create();

    protected void sendJson(HttpExchange ex, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        ex.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    protected void sendError(HttpExchange ex, int statusCode, String errorMessage) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}