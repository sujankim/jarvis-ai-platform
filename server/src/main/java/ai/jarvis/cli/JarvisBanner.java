package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JarvisBanner {

    private boolean displayed = false;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // run first
    public void displayBanner() {
        if (displayed) return; // only show once
        displayed = true;

        System.out.println("""

                ╔══════════════════════════════════════════════╗
                ║                                              ║
                ║       JARVIS AI PLATFORM v0.1.0             ║
                ║                                              ║
                ║   Local AI  Spring Boot 4  Java 21          ║
                ║                                              ║
                ╠══════════════════════════════════════════════╣
                ║  help     all commands                       ║
                ║  login    authenticate                       ║
                ║  chat     talk to Jarvis                     ║
                ║  status   system health                      ║
                ╚══════════════════════════════════════════════╝
                """);
    }
}