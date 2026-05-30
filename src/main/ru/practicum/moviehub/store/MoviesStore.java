package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MoviesStore {
    private final Map<Long, Movie> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public List<Movie> getAll() {
        return new ArrayList<>(storage.values());
    }

    public Optional<Movie> getById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Movie add(Movie movie) {
        Long id = idGenerator.getAndIncrement();
        Movie newMovie = new Movie(id, movie.getTitle(), movie.getYear(), movie.getGenre());
        storage.put(id, newMovie);
        return newMovie;
    }

    public boolean delete(Long id) {
        return storage.remove(id) != null;
    }

    public void clear() {
        storage.clear();
        idGenerator.set(1);
    }
}