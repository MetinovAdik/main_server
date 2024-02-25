package com.chatbot.tele.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpeechRecognitionService {

    @Autowired
    public SpeechRecognitionService() {
    }

    @Async
    public CompletableFuture<String> recognizeSpeechFromMedia(String mediaFilePath, String mediaType) {
        CompletableFuture<String> future = new CompletableFuture<>();
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://techdragons.tech/transcribe";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("mediaUrl", mediaFilePath);
        requestBody.put("mediaType", mediaType);
        requestBody.put("password", "*YnG}5CgT;7[F-HP%N(`At£aj^*8o/e.}lUPO13H='?K~.h3-9");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    Pattern pattern = Pattern.compile("\\[\\d+:\\d+.\\d+ --> \\d+:\\d+.\\d+]\\s+(.*)");
                    Matcher matcher = pattern.matcher(responseBody);
                    StringBuilder filteredText = new StringBuilder();
                    while (matcher.find()) {
                        filteredText.append(matcher.group(1)).append("\n");
                    }
                    return filteredText.toString().trim();
                } else {
                    throw new IOException("Failed to transcribe media. HTTP status: " + response.getStatusCode());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
