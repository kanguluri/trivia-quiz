package com.exercise.async.controller;

import com.exercise.async.model.TriviaQuizWrapperResponse;
import com.exercise.async.model.downstream.TriviaQuestion;
import com.exercise.async.model.TriviaQuizResponse;
import com.exercise.async.model.downstream.TriviaResult;
import com.exercise.async.service.TriviaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class TriviaController {

    @Autowired
    private TriviaService service;

    @GetMapping("/coding/exercise/quiz")
    public List<TriviaQuizWrapperResponse> findAll() throws InterruptedException {
        CompletableFuture<TriviaResult> resultsByBookCategory = service.getTriviaQuestions("5", "11");
        CompletableFuture<TriviaResult> resultsByFilmCategory = service.getTriviaQuestions("5", "12");

        List<TriviaResult> allCompletableFuture = Stream.of(resultsByBookCategory, resultsByFilmCategory).map(CompletableFuture::join).collect(Collectors.toList());

        List<Map<String, List<TriviaQuestion>>> groupCategories = new ArrayList<>();
        for (TriviaResult result : allCompletableFuture) {
            List<TriviaQuestion> questions = result.getQuestions();
            Map<String, List<TriviaQuestion>> groupByCategory = questions.stream().collect(Collectors.groupingBy(TriviaQuestion::getCategory));
            groupCategories.add(groupByCategory);
        }

        Map<String, List<TriviaQuestion>> result = new HashMap<>();
        groupCategories.forEach(map -> result.putAll(map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))));

        List<TriviaQuizWrapperResponse> quizResults = new ArrayList<>();
        for (Map.Entry<String, List<TriviaQuestion>> entry : result.entrySet()) {
            TriviaQuizWrapperResponse triviaQuizWrapperResponse = new TriviaQuizWrapperResponse();
            triviaQuizWrapperResponse.setCategory(entry.getKey());
            List<TriviaQuizResponse> response = entry.getValue().stream().
                    map(e -> convertToQuizResponse(e)).collect(Collectors.toList());

            triviaQuizWrapperResponse.setResults(response);
            quizResults.add(triviaQuizWrapperResponse);
        }
        return quizResults;
    }

    private TriviaQuizResponse convertToQuizResponse(TriviaQuestion triviaQuestion) {
        TriviaQuizResponse response = new TriviaQuizResponse();
        response.setQuestion(triviaQuestion.getQuestion());
        response.setType(triviaQuestion.getType());
        response.setDifficulty(triviaQuestion.getDifficulty());
        response.setCorrectAnswer(triviaQuestion.getCorrectAnswer());
        response.setAllAnswers(triviaQuestion.getIncorrectAnswers());
        response.getAllAnswers().add(triviaQuestion.getCorrectAnswer());
        return response;
    }
}