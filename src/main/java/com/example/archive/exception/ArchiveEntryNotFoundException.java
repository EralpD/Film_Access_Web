package com.example.archive.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ArchiveEntryNotFoundException extends RuntimeException{
    public ArchiveEntryNotFoundException(){
        super("Archive register didn't find.");
    }
}
