package com.example.omdb.exception;

public class OmdbInvalidResponseException extends RuntimeException {
    
    public OmdbInvalidResponseException (String message){
        super(message);
    }
}
