package ai.jarvis.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Text-to-speech using OS native commands.
 *
 * FIXES (CodeRabbit):
 * 1. Process leak fix: destroyForcibly() on timeout
 *    in ALL generate* methods (not just playText).
 *    Previously generate* methods ignored waitFor
 *    return value — timed-out processes leaked.
 *
 * 2. Linux file output fix: use text2wave
 *    festival --tts is for PLAYBACK, not file output.
 *    text2wave -o <file> correctly writes WAV.
 *    Without this: generateLinuxAudio() produced
 *    empty bytes on festival-only systems.
 */
@Slf4j
@Service
public class SystemTextToSpeechService
        implements TextToSpeechService {

    private static final int TIMEOUT_SECONDS = 30;

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
                                "text2wave",
                                "--version")
                                || isCommandAvailable(
                                "festival",
                                "--version");
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

    private byte[] generateAudio(String text)
            throws Exception {

        File tempFile = File.createTempFile(
                "jarvis-tts-", ".wav");
        tempFile.deleteOnExit();

        try {
            if (IS_WINDOWS) {
                generateWindowsAudio(text, tempFile);
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

    private void playText(String text)
            throws Exception {

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
            if (isCommandAvailable(
                    "espeak", "--version")) {
                process = new ProcessBuilder(
                        "espeak", safe)
                        .start();
            } else {
                process = new ProcessBuilder(
                        "festival", "--tts")
                        .start();
                // Write text to festival stdin
                process.getOutputStream()
                        .write(safe.getBytes());
                process.getOutputStream().close();
            }
        } else {
            log.warn(
                    "No TTS for OS: {}", OS);
            return;
        }

        // FIX Issue 1: destroyForcibly on timeout
        boolean finished = process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("TTS playback process timed out");
        }
    }

    /**
     * FIX Issue 1: destroyForcibly on timeout.
     * Previously: waitFor() return value ignored.
     * Timed-out child processes leaked silently.
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

        // FIX Issue 1: check return + destroyForcibly
        if (!process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn(
                    "Windows TTS generation timed out");
        }
    }

    /**
     * FIX Issue 1: destroyForcibly on timeout.
     * Applied to both say and afconvert processes.
     */
    private void generateMacAudio(
            String text, File outputFile)
            throws Exception {

        File aiffFile = File.createTempFile(
                "jarvis-tts-", ".aiff");
        aiffFile.deleteOnExit();

        try {
            Process sayProcess =
                    new ProcessBuilder(
                            "say",
                            "-o",
                            aiffFile.getAbsolutePath(),
                            text)
                            .start();

            // FIX Issue 1: destroyForcibly on timeout
            if (!sayProcess.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                sayProcess.destroyForcibly();
                log.warn(
                        "macOS say process timed out");
                return;
            }

            if (aiffFile.exists()) {
                Process convertProcess =
                        new ProcessBuilder(
                                "afconvert",
                                "-f", "WAVE",
                                "-d", "LEI16",
                                aiffFile
                                        .getAbsolutePath(),
                                outputFile
                                        .getAbsolutePath())
                                .start();

                // FIX Issue 1: destroyForcibly
                if (!convertProcess.waitFor(
                        TIMEOUT_SECONDS,
                        TimeUnit.SECONDS)) {
                    convertProcess.destroyForcibly();
                    log.warn(
                            "afconvert timed out");
                }
            }

        } finally {
            aiffFile.delete();
        }
    }

    /**
     * FIX Issue 1: destroyForcibly on timeout.
     * FIX Issue 2: Use text2wave for file output.
     *
     * festival --tts = PLAYBACK only, not file output.
     * text2wave -o <file> = correct WAV file writer.
     * espeak -w <file> = direct WAV writer (preferred).
     */
    private void generateLinuxAudio(
            String text, File outputFile)
            throws Exception {

        Process process;

        if (isCommandAvailable(
                "espeak", "--version")) {
            // espeak: writes WAV directly
            process = new ProcessBuilder(
                    "espeak",
                    "-w",
                    outputFile.getAbsolutePath(),
                    text)
                    .start();

        } else if (isCommandAvailable(
                "text2wave", "--version")) {
            // FIX Issue 2: text2wave for WAV output
            // text2wave = festival's WAV file writer
            // festival --tts = playback only (WRONG)
            process = new ProcessBuilder(
                    "bash", "-c",
                    "echo '"
                            + sanitizeForShell(text)
                            + "' | text2wave -o "
                            + outputFile
                            .getAbsolutePath())
                    .start();

        } else {
            // Last resort: festival scheme approach
            // Uses Festival's Scheme interface
            // to save WAV programmatically
            process = new ProcessBuilder(
                    "bash", "-c",
                    "echo '(voice_kal_diphone) "
                            + "(utt.save.wave "
                            + "(SayText \""
                            + sanitizeForShell(text)
                            + "\") \""
                            + outputFile
                            .getAbsolutePath()
                            + "\")' | festival")
                    .start();
        }

        // FIX Issue 1: destroyForcibly on timeout
        if (!process.waitFor(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn(
                    "Linux TTS generation timed out");
        }
    }

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

    private String sanitizeForShell(String text) {
        if (text == null) return "";

        return text
                .replace("'", " ")
                .replace("`", " ")
                .replace(";", " ")
                .replace("|", " ")
                .replace("&", " and ")
                .replace("$", " ")
                .replace("<", " ")
                .replace(">", " ")
                .trim();
    }
}