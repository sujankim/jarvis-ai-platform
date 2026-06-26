package ai.jarvis.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * PHASE 5 UPDATE: Voice selection support.
 *
 * Users can now configure voice name + speed
 * via environment variables or application.yml.
 *
 * JARVIS_VOICE_NAME=Microsoft Zira Desktop  (female Windows)
 * JARVIS_VOICE_NAME=Samantha                (female macOS)
 * JARVIS_VOICE_NAME=en+f3                   (female Linux)
 * JARVIS_VOICE_SPEED=1.2                    (faster)
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
            OS.contains("nux") || OS.contains("nix");

    private final String voiceName;
    private final double voiceSpeed;

    public SystemTextToSpeechService(
            @Value("${jarvis.voice.tts.voice:}")
            String voiceName,
            @Value("${jarvis.voice.tts.speed:1.0}")
            double voiceSpeed) {

        this.voiceName = voiceName;
        this.voiceSpeed = voiceSpeed;

        log.info(
                "SystemTextToSpeechService: "
                        + "os={} voice='{}' speed={}",
                getName(),
                voiceName.isBlank()
                        ? "default" : voiceName,
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
                                "powershell", "-Command",
                                "Write-Host ok");
                    } else if (IS_MAC) {
                        return isCommandAvailable(
                                "say", "--version");
                    } else if (IS_LINUX) {
                        return isCommandAvailable(
                                "espeak", "--version")
                                || isCommandAvailable(
                                "text2wave", "--version")
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
                    buildWindowsPlayCommand(safe))
                    .start();

        } else if (IS_MAC) {
            process = buildMacPlayProcess(safe);

        } else if (IS_LINUX) {
            process = buildLinuxPlayProcess(safe);

        } else {
            log.warn("No TTS for OS: {}", OS);
            return;
        }

        boolean finished = process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("TTS playback timed out");
        }
    }

    /**
     * Build Windows PowerShell TTS command.
     * Includes voice selection if configured.
     */
    private String buildWindowsPlayCommand(
            String safe) {

        StringBuilder cmd = new StringBuilder(
                "Add-Type -AssemblyName System.Speech; "
                        + "$s = New-Object "
                        + "System.Speech.Synthesis"
                        + ".SpeechSynthesizer; ");

        // Voice selection
        if (!voiceName.isBlank()) {
            cmd.append("$s.SelectVoice('")
                    .append(voiceName)
                    .append("'); ");
        }

        // Speed (Rate: -10 slowest, 10 fastest)
        // Convert our multiplier to Rate scale
        if (voiceSpeed != 1.0) {
            int rate = (int) ((voiceSpeed - 1.0) * 5);
            rate = Math.max(-5, Math.min(5, rate));
            cmd.append("$s.Rate = ")
                    .append(rate)
                    .append("; ");
        }

        cmd.append("$s.Speak('")
                .append(safe)
                .append("')");

        return cmd.toString();
    }

    /**
     * Build macOS say command with voice + speed.
     */
    private Process buildMacPlayProcess(String safe)
            throws Exception {

        ProcessBuilder pb;

        if (!voiceName.isBlank()) {
            pb = new ProcessBuilder(
                    "say", "-v", voiceName, safe);
        } else {
            pb = new ProcessBuilder("say", safe);
        }

        // macOS say rate: words per minute
        // Default ~175wpm, 1.5x = 262wpm
        if (voiceSpeed != 1.0) {
            int wpm = (int) (175 * voiceSpeed);
            pb = new ProcessBuilder(
                    "say",
                    "-v", voiceName.isBlank()
                    ? "Samantha" : voiceName,
                    "-r", String.valueOf(wpm),
                    safe);
        }

        return pb.start();
    }

    /**
     * Build Linux espeak command with voice.
     */
    private Process buildLinuxPlayProcess(String safe)
            throws Exception {

        if (isCommandAvailable(
                "espeak", "--version")) {

            ProcessBuilder pb = new ProcessBuilder();

            if (!voiceName.isBlank()) {
                pb.command("espeak",
                        "-v", voiceName, safe);
            } else {
                pb.command("espeak", safe);
            }

            // espeak speed in words per minute
            if (voiceSpeed != 1.0) {
                int wpm = (int) (175 * voiceSpeed);
                pb.command("espeak",
                        "-v", voiceName.isBlank()
                                ? "en" : voiceName,
                        "-s", String.valueOf(wpm),
                        safe);
            }

            return pb.start();

        } else {
            // festival fallback
            return new ProcessBuilder(
                    "festival", "--tts")
                    .start();
        }
    }

    private void generateWindowsAudio(
            String text, File outputFile)
            throws Exception {

        String safe = sanitizeForShell(text);
        String path = outputFile
                .getAbsolutePath()
                .replace("\\", "\\\\");

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
            int rate = (int) ((voiceSpeed - 1.0) * 5);
            rate = Math.max(-5, Math.min(5, rate));
            cmd.append("$s.Rate = ")
                    .append(rate)
                    .append("; ");
        }

        cmd.append("$s.SetOutputToWaveFile('")
                .append(path)
                .append("'); ")
                .append("$s.Speak('")
                .append(safe)
                .append("'); ")
                .append("$s.SetOutputToDefaultAudioDevice()");

        Process process = new ProcessBuilder(
                "powershell", "-Command",
                cmd.toString())
                .start();

        if (!process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn("Windows TTS timed out");
        }
    }

    private void generateMacAudio(
            String text, File outputFile)
            throws Exception {

        File aiffFile = File.createTempFile(
                "jarvis-tts-", ".aiff");
        aiffFile.deleteOnExit();

        try {
            ProcessBuilder pb;

            if (!voiceName.isBlank()) {
                pb = new ProcessBuilder(
                        "say",
                        "-v", voiceName,
                        "-o", aiffFile.getAbsolutePath(),
                        text);
            } else {
                pb = new ProcessBuilder(
                        "say",
                        "-o", aiffFile.getAbsolutePath(),
                        text);
            }

            Process sayProcess = pb.start();
            if (!sayProcess.waitFor(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                sayProcess.destroyForcibly();
                return;
            }

            if (aiffFile.exists()) {
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
                }
            }

        } finally {
            aiffFile.delete();
        }
    }

    private void generateLinuxAudio(
            String text, File outputFile)
            throws Exception {

        Process process;

        if (isCommandAvailable(
                "espeak", "--version")) {

            String voice = voiceName.isBlank()
                    ? "en" : voiceName;

            process = new ProcessBuilder(
                    "espeak",
                    "-v", voice,
                    "-w", outputFile.getAbsolutePath(),
                    text)
                    .start();

        } else if (isCommandAvailable(
                "text2wave", "--version")) {
            process = new ProcessBuilder(
                    "bash", "-c",
                    "echo '"
                            + sanitizeForShell(text)
                            + "' | text2wave -o "
                            + outputFile.getAbsolutePath())
                    .start();
        } else {
            process = new ProcessBuilder(
                    "bash", "-c",
                    "echo '(voice_kal_diphone) "
                            + "(utt.save.wave "
                            + "(SayText \""
                            + sanitizeForShell(text)
                            + "\") \""
                            + outputFile.getAbsolutePath()
                            + "\")' | festival")
                    .start();
        }

        if (!process.waitFor(
                TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.warn("Linux TTS timed out");
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