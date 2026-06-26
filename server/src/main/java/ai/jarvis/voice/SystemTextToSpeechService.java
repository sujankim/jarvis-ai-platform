package ai.jarvis.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Text-to-speech using OS native commands.
 *
 * Supports Windows, macOS, and Linux.
 * Voice name and speed configurable via:
 *   jarvis.voice.tts.voice
 *   jarvis.voice.tts.speed
 */
@Slf4j
@Service
public class SystemTextToSpeechService
        implements TextToSpeechService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAC_DEFAULT_WPM = 175;
    private static final int LINUX_DEFAULT_WPM = 175;

    private static final String OS =
            System.getProperty("os.name")
                    .toLowerCase();

    private static final boolean IS_WINDOWS =
            OS.contains("win");
    private static final boolean IS_MAC =
            OS.contains("mac");
    private static final boolean IS_LINUX =
            OS.contains("nux") || OS.contains("nix");

    private final String voiceName;
    private final double voiceSpeed;

    public SystemTextToSpeechService(
            @Value("${jarvis.voice.tts.voice:}")
            String voiceName,
            @Value("${jarvis.voice.tts.speed:1.0}")
            double voiceSpeed) {

        this.voiceName = voiceName != null
                ? voiceName : "";
        this.voiceSpeed = voiceSpeed;

        log.info(
                "SystemTextToSpeechService: "
                        + "os={} voice='{}' speed={}",
                getName(),
                this.voiceName.isBlank()
                        ? "system-default"
                        : this.voiceName,
                voiceSpeed);
    }

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
                            "TTS speak() failed: {}",
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
                                "powershell",
                                "-Command",
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

    // ── Audio generation (speak() path) ──────────

    private byte[] generateAudio(String text)
            throws Exception {

        File tempFile = File.createTempFile(
                "jarvis-tts-", ".wav");
        tempFile.deleteOnExit();

        try {
            if (IS_WINDOWS) {
                runProcess(new ProcessBuilder(
                        "powershell", "-Command",
                        buildWindowsCommand(
                                sanitizeForShell(text),
                                tempFile.getAbsolutePath()
                                        .replace("\\", "\\\\"))));

            } else if (IS_MAC) {
                generateMacAudio(text, tempFile);

            } else if (IS_LINUX) {
                generateLinuxAudio(text, tempFile);
            }

            if (tempFile.exists()
                    && tempFile.length() > 0) {
                return Files.readAllBytes(
                        tempFile.toPath());
            }
            return new byte[0];

        } finally {
            // deleteOnExit handles cleanup at JVM exit
            // explicit delete for immediate cleanup
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.debug(
                            "Temp TTS file will be "
                                    + "deleted on JVM exit");
                }
            }
        }
    }

    // ── Playback (speakAndPlay() path) ────────────

    private void playText(String text)
            throws Exception {

        String safe = sanitizeForShell(text);

        if (IS_WINDOWS) {
            runProcess(new ProcessBuilder(
                    "powershell", "-Command",
                    buildWindowsCommand(safe, null)));

        } else if (IS_MAC) {
            // FIX: buildMacArgs() returns List<String>
            // new ProcessBuilder(List) — then .start()
            runProcess(new ProcessBuilder(
                    buildMacArgs(safe, null)));

        } else if (IS_LINUX) {
            playLinuxText(safe);

        } else {
            log.warn(
                    "No TTS available for OS: {}",
                    OS);
        }
    }

    // ── Windows ───────────────────────────────────

    /**
     * Build Windows PowerShell TTS command string.
     * Shared by both speak() and speakAndPlay() paths.
     *
     * @param safe       sanitized text
     * @param outputPath null = play, non-null = save WAV
     */
    private String buildWindowsCommand(
            String safe, String outputPath) {

        StringBuilder cmd = new StringBuilder(
                "Add-Type -AssemblyName System.Speech; "
                        + "$s = New-Object "
                        + "System.Speech.Synthesis"
                        + ".SpeechSynthesizer; ");

        if (!voiceName.isBlank()) {
            cmd.append("$s.SelectVoice('")
                    .append(voiceName)
                    .append("'); ");
        }

        if (voiceSpeed != 1.0) {
            // PowerShell Rate: -10 (slow) to 10 (fast)
            int rate = Math.clamp(
                    (int) ((voiceSpeed - 1.0) * 5),
                    -5, 5);
            cmd.append("$s.Rate = ")
                    .append(rate)
                    .append("; ");
        }

        if (outputPath != null) {
            cmd.append("$s.SetOutputToWaveFile('")
                    .append(outputPath)
                    .append("'); ");
        }

        cmd.append("$s.Speak('")
                .append(safe)
                .append("'); ");

        if (outputPath != null) {
            cmd.append(
                    "$s.SetOutputToDefaultAudioDevice()");
        }

        return cmd.toString();
    }

    // ── macOS ─────────────────────────────────────

    /**
     *  Returns List<String> not ProcessBuilder.
     * Callers do: new ProcessBuilder(buildMacArgs(...))
     * Fixes "Incompatible types" compile error.
     *
     * no longer hardcodes "Samantha".
     * Empty voiceName = system default preserved.
     * Speed applied in BOTH speak() + speakAndPlay().
     *
     * @param safe       sanitized text
     * @param outputPath null = play, non-null = AIFF file
     */
    private List<String> buildMacArgs(
            String safe, String outputPath) {

        List<String> args = new ArrayList<>();
        args.add("say");

        // Only add -v when explicitly configured
        // Empty voiceName = system default voice
        if (!voiceName.isBlank()) {
            args.add("-v");
            args.add(voiceName);
        }

        // Apply speed in both paths
        if (voiceSpeed != 1.0) {
            int wpm = (int) (MAC_DEFAULT_WPM
                    * voiceSpeed);
            args.add("-r");
            args.add(String.valueOf(wpm));
        }

        if (outputPath != null) {
            args.add("-o");
            args.add(outputPath);
        }

        args.add(safe);

        return args;
    }

    private void generateMacAudio(
            String text, File outputFile)
            throws Exception {

        File aiffFile = File.createTempFile(
                "jarvis-tts-", ".aiff");
        aiffFile.deleteOnExit();

        try {
            // FIX: buildMacArgs() returns List<String>
            // new ProcessBuilder(List) — correct type
            Process sayProcess =
                    new ProcessBuilder(
                            buildMacArgs(
                                    text,
                                    aiffFile.getAbsolutePath()))
                            .start();

            if (!sayProcess.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                sayProcess.destroyForcibly();
                log.warn("macOS say timed out");
                return;
            }

            if (aiffFile.exists()
                    && aiffFile.length() > 0) {

                Process convert =
                        new ProcessBuilder(
                                "afconvert",
                                "-f", "WAVE",
                                "-d", "LEI16",
                                aiffFile.getAbsolutePath(),
                                outputFile.getAbsolutePath())
                                .start();

                if (!convert.waitFor(
                        TIMEOUT_SECONDS,
                        TimeUnit.SECONDS)) {
                    convert.destroyForcibly();
                    log.warn("afconvert timed out");
                }
            }

        } finally {
            if (aiffFile.exists()) {
                boolean deleted = aiffFile.delete();
                if (!deleted) {
                    log.debug(
                            "AIFF file will be "
                                    + "deleted on JVM exit");
                }
            }
        }
    }

    // ── Linux ─────────────────────────────────────

    /**
     * buildLinuxArgs() extracted method.
     * Removes duplication between generateLinuxAudio()
     * and playLinuxText() (was 12 lines duplicated).
     *
     * speed applied in BOTH paths via -s flag.
     *
     * @param safe       sanitized text
     * @param outputPath null = play, non-null = WAV file
     */
    private List<String> buildEspeakArgs(
            String safe, String outputPath) {

        String voice = voiceName.isBlank()
                ? "en" : voiceName;

        List<String> args = new ArrayList<>();
        args.add("espeak");
        args.add("-v");
        args.add(voice);

        // Apply speed in both paths
        if (voiceSpeed != 1.0) {
            int wpm = (int) (LINUX_DEFAULT_WPM
                    * voiceSpeed);
            args.add("-s");
            args.add(String.valueOf(wpm));
        }

        if (outputPath != null) {
            args.add("-w");
            args.add(outputPath);
        }

        args.add(safe);

        return args;
    }

    private void generateLinuxAudio(
            String text, File outputFile)
            throws Exception {

        String safe = sanitizeForShell(text);

        if (isCommandAvailable(
                "espeak", "--version")) {

            runProcess(new ProcessBuilder(
                    buildEspeakArgs(
                            safe,
                            outputFile.getAbsolutePath())));

        } else if (isCommandAvailable(
                "text2wave", "--version")) {

            runProcess(new ProcessBuilder(
                    "bash", "-c",
                    "echo '"
                            + safe
                            + "' | text2wave -o "
                            + outputFile
                            .getAbsolutePath()));

        } else {
            // Festival Scheme: writes WAV directly
            runProcess(new ProcessBuilder(
                    "bash", "-c",
                    "echo '(voice_kal_diphone) "
                            + "(utt.save.wave "
                            + "(SayText \""
                            + safe
                            + "\") \""
                            + outputFile.getAbsolutePath()
                            + "\")' | festival"));
        }
    }

    private void playLinuxText(String safe)
            throws Exception {

        if (isCommandAvailable(
                "espeak", "--version")) {

            // FIX: shared buildEspeakArgs()
            // no outputPath = play mode
            runProcess(new ProcessBuilder(
                    buildEspeakArgs(safe, null)));

        } else {
            // FIX Issue 3: Festival reads from stdin
            // ProcessBuilder("festival","--tts")
            // then write text to stdin
            Process festival =
                    new ProcessBuilder(
                            "festival", "--tts")
                            .start();

            try (OutputStream stdin =
                         festival.getOutputStream()) {
                stdin.write(safe.getBytes());
                stdin.flush();
            }

            if (!festival.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                festival.destroyForcibly();
                log.warn("Festival timed out");
            }
        }
    }

    // ── Utilities ─────────────────────────────────

    /**
     * Run a ProcessBuilder and wait for completion.
     * Destroys process if timeout exceeded.
     * Shared by all three OS paths.
     */
    private void runProcess(ProcessBuilder pb)
            throws Exception {

        Process process = pb.start();

        if (!process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn("TTS process timed out");
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