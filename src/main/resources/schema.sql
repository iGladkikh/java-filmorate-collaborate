DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS friendship CASCADE;
DROP TABLE IF EXISTS genres CASCADE;
DROP TABLE IF EXISTS mpa_rating CASCADE;
DROP TABLE IF EXISTS films CASCADE;
DROP TABLE IF EXISTS likes CASCADE;
DROP TABLE IF EXISTS film_genres CASCADE;

create table if not exists users
(
    user_id  int generated by default as identity primary key,
    email    varchar      NOT NULL,
    login    varchar(255) NOT NULL,
    name     varchar(255),
    birthday date         NOT NULL
);

create unique index if not exists USER_EMAIL_UINDEX on USERS (email);
create unique index if not exists USER_LOGIN_UINDEX on USERS (login);

create table if not exists friendship
(
    PRIMARY KEY (user_id, friend_id),
    user_id   int,
    friend_id int,
    accepted  boolean,
    FOREIGN KEY (user_id) REFERENCES users (user_id),
    FOREIGN KEY (friend_id) REFERENCES users (user_id)
);

create table if not exists mpa_rating
(
    rating_id int auto_increment,
    name      varchar(255)
);

create table if not exists films
(
    film_id     int generated by default as identity primary key,
    name        varchar(255) NOT NULL,
    description varchar(200) NOT NULL,
    releaseDate date         NOT NULL,
    duration    int,
    rating_id   int
);

create table if not exists likes
(
    film_id int,
    user_id int,
    PRIMARY KEY (user_id, film_id),
    FOREIGN KEY (film_id) REFERENCES films (film_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id)
);

create table if not exists genres
(
    genre_id int auto_increment primary key,
    name     varchar(255)
);

create table if not exists film_genres
(
    id       int auto_increment,
    film_id  int,
    genre_id int,
    FOREIGN KEY (film_id) REFERENCES films (film_id),
    FOREIGN KEY (genre_id) REFERENCES genres (genre_id)
);

