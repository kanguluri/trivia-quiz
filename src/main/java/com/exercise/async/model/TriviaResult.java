package com.exercise.async.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

public class TriviaResult {
    @JsonProperty("results")
    ArrayList<TriviaQuestion> questions;

    public ArrayList<TriviaQuestion> getQuestions()   {
        return questions;
    }

}
