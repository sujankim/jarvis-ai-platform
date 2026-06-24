package ai.jarvis.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Text-to-speech using OS native commands.
 *
 * CROSS-PLATFORM SUPPORT:
 * Windows → PowerShell + System.Speech.Synthesis
 *           Saves to temp .wav then reads bytes
 *
 * macOS   → say -o output.aiff command
 *           Converts to wav if needed
 *
 * Linux   → espeak or festival
 *           espeak -w output.wav "text"
 *
 * WHY SYSTEM TTS:
 * → Zero extra dependencies
 * → Works immediately on any OS
 * → Good enough for Phase 5
 * → Interface allows easy upgrade later
 *
 * ALL METHODS run on boundedElastic thread pool
 * because ProcessBuilder is blocking I/O.
 */
@Slf4j
@Service
public class SystemTextToSpeechService
        implements TextToSpeechService {

    private static final int TIMEOUT_SECONDS = 30;

    // Detect OS once at startup
    private static final String OS =
            System.getProperty("os.name")
                    .toLowerCase();

    private static final boolean IS_WINDOWS =
            OS.contains("win");

    private static final boolean IS_MAC =
            OS.contains("mac");

    private static final boolean IS_LINUX =
            OS.contains("nux")
                    || OS.contains("nix");

    @Override
    public Mono<byte[]> speak(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new byte[0]);
        }

        return Mono.fromCallable(() ->
                        generateAudio(text))
                .subscribeOn(
                        Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn(
                            "TTS failed: {}",
                            error.getMessage());
                    return Mono.just(new byte[0]);
                });
    }

    @Override
    public Mono<Void> speakAndPlay(String text) {
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    playText(text);
                    return null;
                })
                .subscribeOn(
                        Schedulers.boundedElastic())
                .then()
                .onErrorResume(error -> {
                    log.warn(
                            "TTS playback failed: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.fromCallable(() -> {
                    if (IS_WINDOWS) {
                        return isCommandAvailable(
                                "powershell", "-Command",
                                "Write-Host ok");
                    } else if (IS_MAC) {
                        return isCommandAvailable(
                                "say", "--version");
                    } else if (IS_LINUX) {
                        return isCommandAvailable(
                                "espeak", "--version")
                                || isCommandAvailable(
                                "festival", "--version");
                    }
                    return false;
                })
                .subscribeOn(
                        Schedulers.boundedElastic())
                .onErrorReturn(false);
    }

    @Override
    public String getName() {
        if (IS_WINDOWS) return "system-windows";
        if (IS_MAC) return "system-macos";
        if (IS_LINUX) return "system-linux";
        return "system-unknown";
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Generate audio bytes from text.
     * Writes to temp file, reads bytes back.
     *
     * @param text text to convert
     * @return audio bytes (wav format)
     */
    private byte[] generateAudio(String text)
            throws Exception {

        File tempFile = File.createTempFile(
                "jarvis-tts-", ".wav");
        tempFile.deleteOnExit();

        try {
            if (IS_WINDOWS) {
                generateWindowsAudio(
                        text, tempFile);
            } else if (IS_MAC) {
                generateMacAudio(text, tempFile);
            } else if (IS_LINUX) {
                generateLinuxAudio(text, tempFile);
            } else {
                log.warn(
                        "Unsupported OS for TTS: {}",
                        OS);
                return new byte[0];
            }

            if (tempFile.exists()
                    && tempFile.length() > 0) {
                return Files.readAllBytes(
                        tempFile.toPath());
            }

            return new byte[0];

        } finally {
            tempFile.delete();
        }
    }

    /**
     * Play text through system speakers.
     * Does NOT generate file — plays directly.
     */
    private void playText(String text)
            throws Exception {

        // Sanitize text for shell injection safety
        String safe = sanitizeForShell(text);

        Process process;

        if (IS_WINDOWS) {
            process = new ProcessBuilder(
                    "powershell", "-Command",
                    "Add-Type -AssemblyName "
                            + "System.Speech; "
                            + "$s = New-Object "
                            + "System.Speech.Synthesis"
                            + ".SpeechSynthesizer; "
                            + "$s.Speak('"
                            + safe + "')")
                    .start();

        } else if (IS_MAC) {
            process = new ProcessBuilder(
                    "say", safe)
                    .start();

        } else if (IS_LINUX) {
            // Try espeak first, fall back to festival
            if (isCommandAvailable(
                    "espeak", "--version")) {
                process = new ProcessBuilder(
                        "espeak", safe)
                        .start();
            } else {
                process = new ProcessBuilder(
                        "bash", "-c",
                        "echo '" + safe
                                + "' | festival --tts")
                        .start();
            }
        } else {
            log.warn(
                    "No TTS available for OS: {}",
                    OS);
            return;
        }

        boolean finished = process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("TTS process timed out");
        }
    }

    /**
     * Generate audio file on Windows.
     * Uses PowerShell System.Speech to write WAV.
     */
    private void generateWindowsAudio(
            String text, File outputFile)
            throws Exception {

        String safe = sanitizeForShell(text);
        String path = outputFile
                .getAbsolutePath()
                .replace("\\", "\\\\");

        Process process = new ProcessBuilder(
                "powershell", "-Command",
                "Add-Type -AssemblyName "
                        + "System.Speech; "
                        + "$s = New-Object "
                        + "System.Speech.Synthesis"
                        + ".SpeechSynthesizer; "
                        + "$s.SetOutputToWaveFile('"
                        + path + "'); "
                        + "$s.Speak('"
                        + safe + "'); "
                        + "$s.SetOutputToDefaultAudioDevice()")
                .start();

        process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Generate audio file on macOS.
     * Uses built-in `say` command.
     */
    private void generateMacAudio(
            String text, File outputFile)
            throws Exception {

        // macOS say outputs AIFF — rename temp file
        File aiffFile = File.createTempFile(
                "jarvis-tts-", ".aiff");
        aiffFile.deleteOnExit();

        Process process = new ProcessBuilder(
                "say",
                "-o", aiffFile.getAbsolutePath(),
                text)
                .start();

        process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Convert AIFF to WAV via afconvert
        if (aiffFile.exists()) {
            Process convert = new ProcessBuilder(
                    "afconvert",
                    "-f", "WAVE",
                    "-d", "LEI16",
                    aiffFile.getAbsolutePath(),
                    outputFile.getAbsolutePath())
                    .start();
            convert.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        }

        aiffFile.delete();
    }

    /**
     * Generate audio file on Linux.
     * Uses espeak (preferred) or festival.
     */
    private void generateLinuxAudio(
            String text, File outputFile)
            throws Exception {

        Process process;

        if (isCommandAvailable(
                "espeak", "--version")) {
            process = new ProcessBuilder(
                    "espeak",
                    "-w", outputFile.getAbsolutePath(),
                    text)
                    .start();
        } else {
            // festival outputs raw audio
            process = new ProcessBuilder(
                    "bash", "-c",
                    "echo '" + sanitizeForShell(text)
                            + "' | festival "
                            + "--tts --output "
                            + outputFile
                            .getAbsolutePath())
                    .start();
        }

        process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Check if a command exists on the system.
     *
     * @param command command + args to test
     * @return true if command runs without error
     */
    private boolean isCommandAvailable(
            String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sanitize text for shell command injection.
     *
     * SECURITY: Prevents shell injection via
     * text-to-speak content.
     * Removes/replaces shell-dangerous characters.
     *
     * @param text raw text
     * @return sanitized text safe for shell
     */
    private String sanitizeForShell(String text) {
        if (text == null) return "";

        return text
                // Remove single quotes (shell injection)
                .replace("'", " ")
                // Remove backticks
                .replace("`", " ")
                // Remove semicolons
                .replace(";", " ")
                // Remove pipe characters
                .replace("|", " ")
                // Remove ampersands
                .replace("&", " and ")
                // Remove dollar signs
                .replace("$", " ")
                // Remove angle brackets
                .replace("<", " ")
                .replace(">", " ")
                .trim();
    }
}