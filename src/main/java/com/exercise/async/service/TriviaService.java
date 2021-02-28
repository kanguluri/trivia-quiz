package com.exercise.async.service;

import com.exercise.async.model.TriviaQuizResponse;
import com.exercise.async.model.TriviaQuizWrapperResponse;
import com.exercise.async.model.downstream.TriviaQuestion;
import com.exercise.async.model.downstream.TriviaResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TriviaService {

    @Autowired
    private DownstreamService downstreamService;

    public List<TriviaQuizWrapperResponse> getTriviaQuiz() {
        CompletableFuture<TriviaResult> resultsByBookCategory = downstreamService.getTriviaQuestions("5", "11");
        CompletableFuture<TriviaResult> resultsByFilmCategory = downstreamService.getTriviaQuestions("5", "12");

        List<TriviaResult> allCompletableFuture = Stream.of(resultsByBookCategory, resultsByFilmCategory).map(CompletableFuture::join).collect(Collectors.toList());

        return transformResponse(allCompletableFuture);
    }


    private List<TriviaQuizWrapperResponse> transformResponse(List<TriviaResult> allCompletableFuture) {
        List<Map<String, List<TriviaQuestion>>> listOfGroupCategoryMaps = new ArrayList<>();
        for (TriviaResult result : allCompletableFuture) {
            List<TriviaQuestion> questions = result.getQuestions();
            Map<String, List<TriviaQuestion>> groupByCategory = questions.stream().collect(Collectors.groupingBy(TriviaQuestion::getCategory));
            listOfGroupCategoryMaps.add(groupByCategory);
        }

        Map<String, List<TriviaQuestion>> flattenMap = new HashMap<>();
        listOfGroupCategoryMaps.forEach(map -> flattenMap.putAll(map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))));

        List<TriviaQuizWrapperResponse> quizWrapperResponseList = new ArrayList<>();
        for (Map.Entry<String, List<TriviaQuestion>> entry : flattenMap.entrySet()) {
            TriviaQuizWrapperResponse triviaQuizWrapperResponse = new TriviaQuizWrapperResponse();
            triviaQuizWrapperResponse.setCategory(entry.getKey());
            List<TriviaQuizResponse> response = entry.getValue().stream().
                    map(this::convertToQuizResponse).collect(Collectors.toList());

            triviaQuizWrapperResponse.setResults(response);
            quizWrapperResponseList.add(triviaQuizWrapperResponse);
        }
        return quizWrapperResponseList;
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
