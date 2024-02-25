package com.chatbot.tele.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.request.SendVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

@Service
public class VideoTranslationService {

    private TelegramBot bot;

    public VideoTranslationService(@Value("${telegram.bot.token}") String botToken) {
        this.bot = new TelegramBot(botToken);
    }
    private static final Logger logger = LoggerFactory.getLogger(VideoTranslationService.class);
    @Autowired
    private TelegramService telegramService;
    @Autowired
    private SpeechRecognitionService speechRecognitionService;
    @Autowired
    private MediaMessageHandler mediaMessageHandler;
    @Autowired
    private TranslationService translationService;

    @Autowired
    private TextToSpeechService textToSpeechService;



    @Async
    public CompletableFuture<Void> processVideo(String videoUrl, long chatId) {
        return CompletableFuture.supplyAsync(() -> telegramService.getFile(videoUrl))
                .thenCompose(file -> {
                    String filePath = file.filePath();
                    String mediaUrl = telegramService.getFullFilePath(filePath);
                    return speechRecognitionService.recognizeSpeechFromMedia(mediaUrl, "video");
                })
                .thenCompose(recognizedText -> translationService.translateTextToKyrgyz(recognizedText))
                .thenCompose(translatedText -> textToSpeechService.sendTextForSpeech(translatedText, 1))
                .thenCompose(ttsResponse -> mediaMessageHandler.processTTSResponse(ttsResponse, chatId))
                .exceptionally(ex -> {
                    logger.error("Error processing video for chatId: {}", chatId, ex);
                    telegramService.sendMessage(chatId, "Error processing video: " + ex.getMessage());
                    return null;
                });
    }
    @Async
    private CompletableFuture<Void> mergeAudioWithVideo(String videoPath, String audioPath, String outputPath, Long chatId) {
        return CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        "ffmpeg", "-i", videoPath, "-i", audioPath, "-y", "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-shortest", outputPath);
                Process process = builder.start();
                process.waitFor();
                // Отправка сгенерированного видео пользователю
                sendVideoToUser(chatId, outputPath);
            } catch (IOException | InterruptedException e) {
                logger.error("Error merging audio with video for chatId: {}", chatId, e);
                throw new RuntimeException(e);
            }
        });
    }

    // Метод для отправки сгенерированного видео пользователю
    // private void sendVideoToUser(long chatId, String videoPath) {...}
    public void sendVideoToUser(long chatId, String videoPath) {
        java.io.File videoFile = new java.io.File(videoPath);
        bot.execute(new SendVideo(chatId, videoFile));
    }
}
