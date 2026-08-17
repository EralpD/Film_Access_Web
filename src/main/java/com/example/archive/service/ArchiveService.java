package com.example.archive.service;

import org.springframework.stereotype.Service;

import com.example.archive.UserFilmRepository;
import com.example.film.FilmRepository;
import com.example.omdb.service.OmdbService;
import com.example.user.UserRepository;

@Service
public class ArchiveService {
    
    private final UserRepository userRepository;
    private final FilmRepository filmRepository;
    private final UserFilmRepository userFilmRepository;
    private final OmdbService ombdService;

    public ArchiveService(
        UserRepository userRepository,
        FilmRepository filmRepository,
        UserFilmRepository userFilmRepository,
        OmdbService ombdService
    ){
        this.userRepository = userRepository;
        this.filmRepository = filmRepository;
        this.userFilmRepository = userFilmRepository;
        this.ombdService = ombdService;
    }

    
}
