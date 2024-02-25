package com.chatbot.tele.service;

import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class TextToSpeechService {

    private final OkHttpClient client;
    private final String ttsApiUrl = "http://tts.ulut.kg/api/tts";
    private final String bearerToken;

    @Autowired
    public TextToSpeechService(OkHttpClient client, @Value("${tts.api.bearer.token}") String bearerToken) {
        this.client = client;
        this.bearerToken = bearerToken;
    }
    @Async
    public CompletableFuture<Response> sendTextForSpeech(String text, int speakerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("speaker_id", speakerId);

                Request request = new Request.Builder()
                        .url(ttsApiUrl)
                        .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                        .addHeader("Authorization", "Bearer " + this.bearerToken)
                        .build();

                return client.newCall(request).execute();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    @Async
    public CompletableFuture<Path> synthesizeSpeech(String text) {
        return CompletableFuture.supplyAsync(() -> {
            int speakerId = 2;
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("speaker_id", speakerId);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(body.toString(), JSON);

            Request request = new Request.Builder()
                    .url(ttsApiUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + bearerToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                // Создание временного файла для сохранения аудио
                Path tempAudioFile = Files.createTempFile("tts-", ".mp3");

                // Запись тела ответа в файл
                Files.write(tempAudioFile, response.body().bytes(), StandardOpenOption.WRITE);

                return tempAudioFile;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    /*
    @Async
    public Path synthesizeSpeech(String text) throws IOException {
        int speakerId = 2;
        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("speaker_id", speakerId);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(ttsApiUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + bearerToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Создание временного файла для сохранения аудио
            Path tempAudioFile = Files.createTempFile("tts-", ".mp3");

            // Запись тела ответа в файл
            Files.write(tempAudioFile, response.body().bytes(), StandardOpenOption.WRITE);

            return tempAudioFile;
        }
    }

     */
}
