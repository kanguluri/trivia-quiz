package com.exercise.async.controller;

import com.exercise.async.model.TriviaQuizWrapperResponse;
import com.exercise.async.model.downstream.TriviaQuestion;
import com.exercise.async.model.TriviaQuizResponse;
import com.exercise.async.model.downstream.TriviaResult;
import com.exercise.async.service.DownstreamService;
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
    private TriviaService triviaService;

    @GetMapping("/coding/exercise/quiz")
    public List<TriviaQuizWrapperResponse> findAll() {
        return triviaService.getTriviaQuiz();
    }
}