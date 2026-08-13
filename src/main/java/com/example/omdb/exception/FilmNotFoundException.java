package com.example.omdb.exception;

public class FilmNotFoundException extends RuntimeException{
    
    public FilmNotFoundException(String message){
        super(message);
    }
}   
