package com.chatbot.tele.service;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.InputFile;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

@Service
public class MediaMessageHandler {
    private final OkHttpClient httpClient;
    private final SpeechRecognitionService speechRecognitionService;
    private final TelegramService telegramService;
    private final TranslationService translationService;
    private final TextToSpeechService textToSpeechService;

    @Autowired
    public MediaMessageHandler(OkHttpClient httpClient, SpeechRecognitionService speechRecognitionService,
                               TelegramService telegramService, TranslationService translationService,
                               TextToSpeechService textToSpeechService) {
        this.httpClient = httpClient;
        this.speechRecognitionService = speechRecognitionService;
        this.telegramService = telegramService;
        this.translationService = translationService;
        this.textToSpeechService = textToSpeechService;
    }
    @Async
    public CompletableFuture<Void> handleMedia(String fileId, long chatId, String mediaType) {
        return CompletableFuture.supplyAsync(() -> {
                    File file = telegramService.getFile(fileId);
                    String filePath = file.filePath();
                    String mediaUrl = telegramService.getFullFilePath(filePath);
                    return mediaUrl;
                }).thenCompose(mediaUrl -> speechRecognitionService.recognizeSpeechFromMedia(mediaUrl, mediaType))
                .thenCompose(recognizedText -> translationService.translateTextToKyrgyz(recognizedText))
                .thenCompose(translatedText -> textToSpeechService.sendTextForSpeech(translatedText, 1))
                .thenCompose(ttsResponse -> processTTSResponse(ttsResponse, chatId))
                .exceptionally(ex -> {
                    telegramService.sendMessage(chatId, "Произошла ошибка при обработке медиа: " + ex.getMessage());
                    return null;
                });
    }

    public CompletableFuture<Void> processTTSResponse(Response ttsResponse, long chatId) {
        if (!ttsResponse.isSuccessful()) {
            telegramService.sendMessage(chatId, "Не удалось преобразовать текст в речь.");
            return CompletableFuture.completedFuture(null);
        }

        ResponseBody responseBody = ttsResponse.body();
        if (responseBody == null) {
            telegramService.sendMessage(chatId, "Ответ от сервиса TTS был пустым.");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Path ttsFile = Files.createTempFile("tts-", ".mp3");
                Files.copy(responseBody.byteStream(), ttsFile, StandardCopyOption.REPLACE_EXISTING);
                InputFile audioToSend = new InputFile(ttsFile.toFile(), "tts_audio.mp3", "audio/mp3");
                telegramService.getBot().execute(new com.pengrad.telegrambot.request.SendAudio(chatId, audioToSend.getFile()));
                Files.deleteIfExists(ttsFile);
            } catch (IOException e) {
                throw new RuntimeException("Ошибка при обработке ответа TTS: " + e.getMessage(), e);
            }
        });
    }

}
