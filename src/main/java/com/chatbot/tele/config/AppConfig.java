package com.chatbot.tele.config;

import com.chatbot.tele.service.TranslationService;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Duration;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(3000)) // 30 seconds in milliseconds
                .readTimeout(Duration.ofSeconds(3000))    // 30 seconds in milliseconds
                .writeTimeout(Duration.ofSeconds(3000))   // 30 seconds in milliseconds
                .build();
    }
    @Bean
    public TranslationService translationService(@Value("${openai.api.key}") String apiKey, OkHttpClient client) {
        return new TranslationService(apiKey, client);
    }
}
