package com.exercise.async.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonRootName("quiz")
public class TriviaQuizWrapperResponse {
    private String category;
    List<TriviaQuizResponse> results;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<TriviaQuizResponse> getResults() {
        return results;
    }

    public void setResults(List<TriviaQuizResponse> results) {
        this.results = results;
    }
}
