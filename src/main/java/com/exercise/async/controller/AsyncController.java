package com.exercise.async.controller;

import com.exercise.async.model.QuizResult;
import com.exercise.async.model.TriviaQuestion;
import com.exercise.async.model.TriviaQuizList;
import com.exercise.async.model.TriviaResult;
import com.exercise.async.service.AsyncService;
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
public class AsyncController {

    @Autowired
    private AsyncService service;

    @GetMapping("/coding/exercise/quiz")
    public ArrayList<QuizResult> testAsync() throws InterruptedException {
        CompletableFuture<TriviaResult> resultsByGameCategory = service.getTriviaQuestions("5", "11");
        CompletableFuture<TriviaResult> resultsByBookCategory = service.getTriviaQuestions("a", "12");

        List<CompletableFuture<TriviaResult>> completableFutures = new ArrayList<>();
        completableFutures.add(resultsByGameCategory);
        completableFutures.add(resultsByBookCategory);


        List<TriviaResult> allCompletableFuture = Stream.of(resultsByGameCategory, resultsByBookCategory).map(CompletableFuture::join).collect(Collectors.toList());
        List<Map<String, List<TriviaQuestion>>> groupCategories = new ArrayList<>();
        Map<String, List<TriviaQuestion>> groupByCategory = null;
        for (TriviaResult result : allCompletableFuture) {
            List<TriviaQuestion> questions = result.getQuestions();
            groupByCategory = questions.stream().collect(Collectors.groupingBy(TriviaQuestion::getCategory));
            groupCategories.add(groupByCategory);
        }

        Map<String, List<TriviaQuestion>> result = new HashMap<>();
        groupCategories.stream().forEach(map -> {
            result.putAll(map.entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue())));
        });

        ArrayList<QuizResult> quizResults = new ArrayList<>();
        for (Map.Entry<String, List<TriviaQuestion>> entry : result.entrySet()) {
            QuizResult quizResult = new QuizResult();
            quizResult.setCategory(entry.getKey());
            quizResult.setResults(entry.getValue());
            quizResults.add(quizResult);
        }
        return quizResults;
    }

}


