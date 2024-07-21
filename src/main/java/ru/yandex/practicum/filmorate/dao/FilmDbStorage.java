package ru.yandex.practicum.filmorate.dao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.FilmStorage;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Repository
public class FilmDbStorage implements FilmStorage {
    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_FILMS_WITH_ALL_FIELDS = """
            SELECT f.film_id,
                   f.name,
                   f.description,
                   f.releaseDate,
                   f.duration,
                   f.rating_id,
                   l.user_id AS liked_user_id,
                   fg.genre_id,
                   g.name AS genre_name,
                   mpa.name AS rating_name,
                   d.director_id,
                   d.name AS director_name
            FROM films AS f
            LEFT JOIN likes AS l ON l.film_id = f.film_id
            LEFT JOIN film_genres AS fg ON fg.film_id = f.film_id
            LEFT JOIN genres AS g ON g.genre_id = fg.genre_id
            LEFT JOIN mpa_rating AS mpa ON mpa.rating_id = f.rating_id
            LEFT JOIN film_directors AS fd ON fd.film_id = f.film_id
            LEFT JOIN directors AS d ON d.director_id = fd.director_id
            """;

    private static final String SELECT_POPULAR_FILMS_WITH_ALL_FIELDS = """
            SELECT sub.*,
                   l.user_id AS liked_user_id,
                   g.genre_id,
                   g.name    AS genre_name,
                   mpa.name  AS rating_name,
                   d.director_id,
                   d.name    AS director_name
            FROM (SELECT f.film_id,
                         f.name,
                         f.description,
                         f.releaseDate,
                         f.duration,
                         f.rating_id,
                         COUNT(l.film_id) AS liked_users_count
                  FROM films AS f
                           LEFT JOIN likes AS l ON f.film_id = l.film_id
                  GROUP BY f.film_id
                  ORDER BY liked_users_count DESC
                  LIMIT ?) AS sub
                     LEFT JOIN likes AS l ON l.film_id = sub.film_id
                     LEFT JOIN film_genres AS fg ON fg.film_id = sub.film_id
                     LEFT JOIN genres AS g ON g.genre_id = fg.genre_id
                     LEFT JOIN mpa_rating AS mpa ON mpa.rating_id = sub.rating_id
                     LEFT JOIN film_directors AS fd ON fd.film_id = sub.film_id
                     LEFT JOIN directors AS d ON d.director_id = fd.director_id
            """;

    private static final String SELECT_FILMS = "SELECT f.film_id, f.name, f.description, f.releaseDate, f.duration, " +
            "mpa.rating_id, mpa.name AS mpa_name " +
            "FROM films AS f " +
            "INNER JOIN mpa_rating AS mpa ON f.rating_id = mpa.rating_id ";

    @Override
    public Film create(Film film) {
        String sql = "INSERT INTO films (name, description, releaseDate, duration, rating_id) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, new String[]{"film_id"});
                    ps.setString(1, film.getName());
                    ps.setString(2, film.getDescription());
                    ps.setDate(3, Date.valueOf(film.getReleaseDate()));
                    ps.setInt(4, film.getDuration());
                    ps.setInt(5, film.getMpa().getId());
                    return ps;
                }, keyHolder);
        film.setId(Objects.requireNonNull(keyHolder.getKey()).intValue());
        updateGenres(film.getGenres(), film.getId());
        return film;
    }

    @Override
    public Film update(Film film) {
        int id = film.getId();
        String sql = "UPDATE films SET name = ?, description = ?, releaseDate = ?, duration = ?, rating_id = ? " +
                "WHERE film_id = ?";
        jdbcTemplate.update(sql, film.getName(), film.getDescription(), film.getReleaseDate(), film.getDuration(),
                film.getMpa().getId(), id);
        updateGenres(film.getGenres(), id);
        updateDirectors(film.getDirectors(), id);
        return film;
    }

    @Override
    public List<Film> findAllFilms() {
        String sql = "ORDER BY f.film_id";
        return jdbcTemplate.query(SELECT_FILMS + sql, (rs, rowNum) -> makeFilm(rs));
    }

    @Override
    public Optional<Film> findFilmById(int id) {
        String sql = "WHERE f.film_id = ?";
        return jdbcTemplate.query(SELECT_FILMS + sql, (rs, rowNum) -> makeFilm(rs), id).stream().findFirst();
    }

    @Override
    public void deleteFilmById(int id) {
        String sqlFilmGenres = "DELETE FROM FILM_GENRES WHERE film_id = ? ";
        jdbcTemplate.update(sqlFilmGenres, id);
        String sqlFilmLikes = "DELETE FROM LIKES WHERE film_id = ? ";
        jdbcTemplate.update(sqlFilmLikes, id);
        String sql = "DELETE FROM FILMS WHERE film_id = ? ";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public List<Film> findPopular(Integer count, Integer genreId, Integer year) {
        StringBuilder sqlQuery = new StringBuilder();
        sqlQuery.append("SELECT f.film_id, f.name, f.description, f.releaseDate, f.duration, " +
                "mpa.rating_id, mpa.name AS mpa_name, COUNT(l.film_id) AS likes_count " +
                "FROM films AS f " +
                "INNER JOIN mpa_rating AS mpa ON f.rating_id = mpa.rating_id " +
                "LEFT JOIN likes l ON f.film_id = l.film_id " +
                "LEFT JOIN film_genres fg ON f.film_id = fg.film_id ");
        if (genreId != null && year != null) {
            sqlQuery.append(String.format("WHERE fg.genre_id = %d AND EXTRACT(YEAR FROM f.releasedate) = %d ", genreId, year));
        } else if (genreId != null) {
            sqlQuery.append(String.format("WHERE fg.genre_id = %d ", genreId));
        } else if (year != null) {
            sqlQuery.append(String.format("WHERE EXTRACT(YEAR FROM f.releasedate) = %d ", year));
        }
        sqlQuery.append("GROUP BY f.film_id ");
        sqlQuery.append("ORDER BY COUNT(l.film_id) DESC ");
        if (count != null) {
            sqlQuery.append(String.format("LIMIT %d", count));
        }
        sqlQuery.append(";");

        log.info("SQL запрос для получения сымых популярных фильмов: {}", sqlQuery);
        List<Film> list = jdbcTemplate.query(sqlQuery.toString(), (rs, rowNum) -> makeFilm(rs));
        log.info("Список самых популярных фильмов, полученный из БД: {}", list);
        return list;
    }

    @Override
    public List<Film> findFilmsByDirectorID(int id, String sortedBy) {
        String sql = "LEFT JOIN FILM_DIRECTORS fd ON fd.film_id = f.film_id ";

        String sql2 = "";
        if (!sortedBy.isEmpty()) {
            if (sortedBy.equals("year")) {
                sql2 += "GROUP BY f.film_id ";
                sql2 += "ORDER BY f.releasedate ASC, f.film_id ASC ";
            } else if (sortedBy.equals("likes")) {
                sql += "LEFT JOIN likes ON f.film_id = likes.film_id ";
                sql2 += "GROUP BY f.film_id " +
                        "ORDER BY COUNT(likes.film_id) DESC, f.film_id ASC ";
            }
        }
        sql += "WHERE fd.director_id = ? ";
        log.info(SELECT_FILMS + sql + sql2);
        List<Film> films = jdbcTemplate.query(SELECT_FILMS + sql + sql2, (rs, rowNum) -> makeFilm(rs), id);
        addDirectorsInFilms(films);
        return films;
    }

    @Override
    public List<Film> findRecommendedFilms(int userId) {
        String sql = "SELECT user_id, film_id FROM likes WHERE user_id IN (" +
                "SELECT user_id FROM likes WHERE film_id IN (" +
                "SELECT film_id FROM likes WHERE user_id = ?))";

        Map<Integer, LinkedHashSet<Integer>> res = jdbcTemplate.query(sql, new UserFilmExtractor(), userId);
        if (res == null || res.get(userId) == null || res.get(userId).isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Integer> userLikedFilms = res.get(userId);
        res.remove(userId);

        Map<Integer, HashSet<Integer>> countToIntersections = new HashMap<>();
        int maxCount = 0;
        for (Set<Integer> films : res.values()) {

            Set<Integer> userIntersections = new HashSet<>(userLikedFilms);
            userIntersections.retainAll(films);

            int intersectionsCount = userIntersections.size();
            if (intersectionsCount > maxCount) {
                maxCount = intersectionsCount;
            }

            if (!countToIntersections.containsKey(intersectionsCount)) {
                countToIntersections.put(intersectionsCount, new HashSet<>());
            }
            countToIntersections.get(intersectionsCount).addAll(films);
        }

        if (maxCount == 0) {
            return Collections.emptyList();
        }

        List<Integer> recommendedFilmIds = countToIntersections.get(maxCount).stream()
                .filter(filmId -> !userLikedFilms.contains(filmId))
                .toList();

        if (recommendedFilmIds.isEmpty()) {
            return Collections.emptyList();
        }

        return jdbcTemplate.query(
                SELECT_FILMS_WITH_ALL_FIELDS + "WHERE f.film_id IN (?)",
                new FilmListExtractor(),
                recommendedFilmIds.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "))
        );
    }

    @Override
    public List<Film> findCommonFilms(int userId, int friendId) {
        String sql = SELECT_POPULAR_FILMS_WITH_ALL_FIELDS +
                "WHERE sub.film_id IN (" +
                "SELECT film_id FROM likes WHERE user_id = ? " +
                "INTERSECT " +
                "SELECT film_id FROM likes WHERE user_id = ?)";
        int limit = 100;

        return jdbcTemplate.query(sql, new FilmListExtractor(), limit, userId, friendId);
    }

    private Film makeFilm(ResultSet rs) throws SQLException {
        Integer id = rs.getInt("film_id");
        return Film.builder()
                .id(id)
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .releaseDate(rs.getDate("releaseDate").toLocalDate())
                .duration(rs.getInt("duration"))
                .mpa(new Mpa(rs.getInt("rating_id"), rs.getString("mpa_name")))
                .build();
    }

    private void updateGenres(Set<Genre> genres, int id) {
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", id);
        if (genres != null && !genres.isEmpty()) {
            String sql = "INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)";
            Genre[] g = genres.toArray(new Genre[0]);
            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setInt(1, id);
                            ps.setInt(2, g[i].getId());
                        }

                        public int getBatchSize() {
                            return genres.size();
                        }
                    });
        }
    }

    private void updateDirectors(Set<Director> directors, int director_id) {
        jdbcTemplate.update("DELETE FROM FILM_DIRECTORS WHERE film_id = ?", director_id);
        if (directors != null && !directors.isEmpty()) {
            String sql = "INSERT INTO FILM_DIRECTORS (film_id, director_id) VALUES (?, ?)";
            Director[] g = directors.toArray(new Director[0]);
            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setInt(1, director_id);
                            ps.setInt(2, g[i].getId());
                        }

                        public int getBatchSize() {
                            return directors.size();
                        }
                    });
        }
    }

    private void addDirectorsInFilms(List<Film> films) {
        StringBuilder ids = new StringBuilder();
        films.forEach(film -> {
            if (!ids.isEmpty()) {
                ids.append(",");
            }
            ids.append(film.getId().toString());
        });
        if (ids.isEmpty()) {
            return;
        }

        String idStr = ids.toString();

        String sqlDirectors = "SELECT fd.FILM_ID ,fd.DIRECTOR_ID,d.NAME FROM FILM_DIRECTORS fd JOIN directors d" +
                " ON D.DIRECTOR_ID = fd.DIRECTOR_ID WHERE fd.FILM_ID IN (" + idStr + ");";
        HashMap<Integer, Set<Director>> directorMap = new HashMap<>();

        jdbcTemplate.query(sqlDirectors, rs -> {
            Director director = new Director(rs.getInt("DIRECTOR_ID"), rs.getString("NAME"));
            int key = rs.getInt("FILM_ID");
            if (directorMap.containsKey(key)) {
                directorMap.get(key).add(director);
            } else {
                Set<Director> directors = new HashSet<>();
                directors.add(director);
                directorMap.put(key, directors);
            }
        });

        for (Film film : films) {
            if (directorMap.containsKey(film.getId())) {
                film.getDirectors().addAll(directorMap.get(film.getId()));
            }
        }
    }

    private static class UserFilmExtractor implements ResultSetExtractor<Map<Integer, LinkedHashSet<Integer>>> {

        @Override
        public Map<Integer, LinkedHashSet<Integer>> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
            Map<Integer, LinkedHashSet<Integer>> userIdToFilmIds = new HashMap<>();
            while (resultSet.next()) {
                Integer userId = resultSet.getInt("user_id");
                Integer filmId = resultSet.getInt("film_id");

                if (!userIdToFilmIds.containsKey(userId)) {
                    userIdToFilmIds.put(userId, new LinkedHashSet<>());
                }

                userIdToFilmIds.get(userId).add(filmId);
            }
            return userIdToFilmIds;
        }
    }

    private static class FilmListExtractor implements ResultSetExtractor<List<Film>> {

        @Override
        public List<Film> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
            Map<Integer, Film> idToFilm = new HashMap<>();

            while (resultSet.next()) {
                Integer filmId = resultSet.getInt("film_id");
                if (!idToFilm.containsKey(filmId)) {
                    Film film = Film.builder()
                            .id(filmId)
                            .name(resultSet.getString("name"))
                            .description(resultSet.getString("description"))
                            .releaseDate(resultSet.getDate("releaseDate").toLocalDate())
                            .duration(resultSet.getInt("duration"))
                            .genres(new LinkedHashSet<>())
                            .build();
                    idToFilm.put(filmId, film);
                }

                Film film = idToFilm.get(filmId);
                int likedUserId = resultSet.getInt("liked_user_id");
                if (likedUserId > 0) {
                    film.getLikes().add(likedUserId);
                }

                int ratingId = resultSet.getInt("rating_id");
                if (ratingId > 0) {
                    Mpa rating = new Mpa(ratingId, resultSet.getString("rating_name"));
                    film.setMpa(rating);
                }

                int genreId = resultSet.getInt("genre_id");
                if (genreId > 0) {
                    Genre genre = new Genre(genreId, resultSet.getString("genre_name"));
                    film.getGenres().add(genre);
                }

                int directorId = resultSet.getInt("director_id");
                if (directorId > 0) {
                    Director director = new Director(directorId, resultSet.getString("director_name"));
                    film.getDirectors().add(director);
                }
            }

            return new ArrayList<>(idToFilm.values());
        }
    }
}
