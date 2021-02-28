package com.exercise.async.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TriviaQuizResponse {

    private String type;
    private String difficulty;
    private String question;
    @JsonProperty("all_answers")
    private List<String> allAnswers;
    @JsonProperty("correct_answer")
    private String correctAnswer;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public List<String> getAllAnswers() {
        return allAnswers;
    }

    public void setAllAnswers(List<String> allAnswers) {
        this.allAnswers = allAnswers;
    }
}
