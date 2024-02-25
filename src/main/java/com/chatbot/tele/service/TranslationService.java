package com.chatbot.tele.service;
import okhttp3.*;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
public class TranslationService {

    private String apiKey; // Your OpenAI API key
    private OkHttpClient client; // Reuse your OkHttpClient instance

    @Autowired
    public TranslationService(String apiKey, OkHttpClient client) {
        this.apiKey = apiKey;
        this.client = client;
    }

    @Async
    public CompletableFuture<String> translateTextToKyrgyz(String text) {
        return CompletableFuture.supplyAsync(() -> {
            // Создание тела запроса
            JSONObject body = new JSONObject();
            body.put("model", "gpt-4-0125-preview");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "Translate the following English text to Kyrgyz."));
            messages.put(new JSONObject().put("role", "user").put("content", text));
            body.put("messages", messages);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(body.toString(), JSON);

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new RuntimeException("Unexpected code " + response);

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                String translatedText = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                return translatedText;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
