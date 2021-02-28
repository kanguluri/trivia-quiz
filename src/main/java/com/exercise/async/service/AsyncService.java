package com.exercise.async.service;

import com.exercise.async.exception.RestTemplateResponseErrorHandler;
import com.exercise.async.model.TriviaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncService {

    private static Logger log = LoggerFactory.getLogger(AsyncService.class);

    private static final String BASE_URL = "https://opentdb.com/api.php";

    private RestTemplate restTemplate;

    @Autowired
    public AsyncService(RestTemplateBuilder restTemplateBuilder) {
         restTemplate = restTemplateBuilder
               .errorHandler(new RestTemplateResponseErrorHandler())
                .build();
    }

    @Async("asyncExecutor")
    public CompletableFuture<TriviaResult> getTriviaQuestions(String amount,String category) throws InterruptedException
    {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("amount",amount)
                .queryParam("category",category);
        log.info("Retrieving downStream Results for {}",builder.toUriString());
        TriviaResult result = restTemplate.getForObject(builder.toUriString(),TriviaResult.class);
        return CompletableFuture.completedFuture(result);
    }
}
