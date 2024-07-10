package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import ru.yandex.practicum.filmorate.exception.*;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.*;

import javax.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class FilmService {
    private final FilmStorage filmStorage;
    private final MpaStorage mpaStorage;
    private final GenreStorage genreStorage;
    private final LikeStorage likeStorage;
    private final UserStorage userStorage;

    public List<Film> findAllFilms() {
        List<Film> films = filmStorage.findAllFilms();
        //genreStorage.findAllGenresByFilm(films);
        return films;
    }

    public Film create(Film film) {
        Optional<Mpa> mpa = mpaStorage.findMpaById(film.getMpa().getId());
        if (mpa.isEmpty()) {
            throw new ValidationException("Mpa not found");
        }

        if (film.getGenres() != null){
            Optional<Genre> genres = genreStorage.findGenreByIds(film.getGenres().stream().map(Genre::getId).collect(Collectors.toList()));
            if (genres.isEmpty()) {
                throw new ValidationException("Genres not found");
            }
        }

        return filmStorage.create(film);
    }

    public Film update(Film film) {
        if (filmStorage.findFilmById(film.getId()).isEmpty()) {
            throw new FilmNotFoundException("Фильм не найден.");
        }
        return filmStorage.update(film);
    }

    public Film findFilmById(int id) {
        Film film = filmStorage.findFilmById(id).orElseThrow(() -> new FilmNotFoundException("Фильм не найден."));

        List<Genre> genres = genreStorage.findAllGenresByFilmID(id);
        LinkedHashSet<Genre> genresSet = new LinkedHashSet<>(genres);

        genresSet.addAll(genres);

        film.setGenres(genresSet);


        return film;
    }

    public void addLike(int id, int userId) {
        User user = userStorage.findUserById(userId).orElseThrow(() -> new FilmNotFoundException("Пользователь не найден."));
        Film film = filmStorage.findFilmById(id).orElseThrow(() -> new FilmNotFoundException("Фильм не найден."));

        likeStorage.addLike(id, userId);
    }

    public void removeLike(int id, int userId) {
        User user = userStorage.findUserById(userId).orElseThrow(() -> new FilmNotFoundException("Пользователь не найден."));
        Film film = filmStorage.findFilmById(id).orElseThrow(() -> new FilmNotFoundException("Фильм не найден."));

        likeStorage.removeLike(id, userId);
    }

    public List<Film> findPopular(int count) {
        List<Film> films = filmStorage.findPopular(count);
        //genreStorage.findAllGenresByFilm(films);
        return films;
    }

    public List<Mpa> findAllMpa() {
        return mpaStorage.findAllMpa();
    }

    public Mpa findMpaById(int id) {
        return mpaStorage.findMpaById(id).orElseThrow(() -> new MpaNotFoundException("Рейтинг MPA не найден."));
    }

    public List<Genre> findAllGenres() {
        return genreStorage.findAllGenres();
    }

    public Genre findGenreById(int id) {
        return genreStorage.findGenreById(id).orElseThrow(() -> new GenreNotFoundException("Жанр не найден."));
    }
}