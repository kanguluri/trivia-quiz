package com.exercise.async.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonRootName("quiz")
public class QuizResult {
    private String category;
    List<TriviaQuestion> results;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<TriviaQuestion> getResults() {
        return results;
    }

    public void setResults(List<TriviaQuestion> results) {
        this.results = results;
    }
}
