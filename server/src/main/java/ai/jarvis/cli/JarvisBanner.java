package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JarvisBanner {

    @EventListener(ApplicationReadyEvent.class)
    public void displayBanner() {
        System.out.println("""

                ╔══════════════════════════════════════════════╗
                ║                                              ║
                ║     ░░░  JARVIS AI PLATFORM  ░░░            ║
                ║          v0.1.0-SNAPSHOT                    ║
                ║                                              ║
                ║   Local AI • Spring Boot 4 • Java 21        ║
                ║                                              ║
                ╠══════════════════════════════════════════════╣
                ║  Type 'help'    → all commands               ║
                ║  Type 'login'   → authenticate               ║
                ║  Type 'chat'    → talk to Jarvis             ║
                ║  Type 'status'  → system health              ║
                ╚══════════════════════════════════════════════╝
                """);
    }
}