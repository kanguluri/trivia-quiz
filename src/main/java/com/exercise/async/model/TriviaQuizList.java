package com.exercise.async.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TriviaQuizList {
    @JsonProperty("quiz")
    List<QuizResult> questions;

    public TriviaQuizList(List<QuizResult> questions) {
        this.questions = questions;
    }

    public List<QuizResult> getQuestions() {
        return questions;
    }

}
