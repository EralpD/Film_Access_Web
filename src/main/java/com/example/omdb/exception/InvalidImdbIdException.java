package com.example.omdb.exception;

public class InvalidImdbIdException extends RuntimeException {
    
    public InvalidImdbIdException(String message){
        super(message);
    }
}
