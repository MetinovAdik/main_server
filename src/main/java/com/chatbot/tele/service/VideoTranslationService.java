package com.chatbot.tele.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.InputFile;
import com.pengrad.telegrambot.request.SendVideo;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private TranslationService translationService;

    @Autowired
    private TextToSpeechService textToSpeechService;



    @Async
    public CompletableFuture<Void> processVideo(String videoUrl, long chatId) {
        String baseDir = System.getProperty("java.io.tmpdir"); // Using the system's temporary directory
        String outputFileName = "merged-output-" + System.currentTimeMillis() + ".mp4";
        String outputPath = Paths.get(baseDir, outputFileName).toString();
        File sfile = telegramService.getFile(videoUrl);
        String sfilePath = sfile.filePath();
        String mediaUrls = telegramService.getFullFilePath(sfilePath);
        // Step 1: Download the video file from Telegram
        return CompletableFuture.supplyAsync(() -> telegramService.getFile(videoUrl))
                // Step 2: Extract speech from the downloaded video
                .thenCompose(file -> {
                    String filePath = file.filePath();
                    String mediaUrl = telegramService.getFullFilePath(filePath);
                    return speechRecognitionService.recognizeSpeechFromMedia(mediaUrl, "video");

                })
                // Step 3: Translate the recognized text
                .thenCompose(recognizedText -> translationService.translateTextToKyrgyz(recognizedText))
                // Step 4: Synthesize the translated text to speech
                .thenCompose(translatedText -> textToSpeechService.synthesizeSpeech(translatedText))
                // Step 6: Merge the original video with the new audio track
                .thenCompose(audioPath -> mergeAudioWithVideo(mediaUrls, audioPath, outputPath, chatId))
                .exceptionally(ex -> {
                    logger.error("Error processing video for chatId: " + chatId, ex);
                    telegramService.sendMessage(chatId, "An error occurred during video processing.");
                    return null;
                });
    }
    @Async
    private CompletableFuture<Void> mergeAudioWithVideo(String videoPath, Path audioPath, String outputPath, Long chatId) {
        return CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        "ffmpeg", "-i", videoPath, "-i", audioPath.toString(), "-y", "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-shortest", outputPath);
                builder.redirectErrorStream(true); // Redirects error stream to the input stream
                Process process = builder.start();

                // Capture and log the output
                try (InputStream inputStream = process.getInputStream()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info(line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("ffmpeg process exited with error code: " + exitCode);
                }

                // Send the generated video to the user
                sendVideoToUser(chatId, outputPath);
            } catch (IOException | InterruptedException e) {
                logger.error("Error merging audio with video for chatId: {}", chatId, e);
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new RuntimeException(e);
            }
        });
    }


    public void sendVideoToUser(long chatId, String videoPath) {
        java.io.File videoFile = new java.io.File(videoPath);
        if (!videoFile.exists()) {
            logger.error("File not found: {}", videoPath);
            return; // Or handle the error appropriately
        }
        bot.execute(new SendVideo(chatId, videoFile));
    }
}
