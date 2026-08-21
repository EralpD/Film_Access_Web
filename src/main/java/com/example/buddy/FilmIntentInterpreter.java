package com.example.buddy;

import com.example.buddy.record.FilmIntent;

public interface FilmIntentInterpreter {

    FilmIntent interpret(String userPrompt);
}