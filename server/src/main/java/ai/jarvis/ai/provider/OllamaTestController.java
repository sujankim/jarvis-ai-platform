package ai.jarvis.ai.provider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/test")
public class OllamaTestController {

    private final ChatClient chatClient;

    public OllamaTestController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping(
            value = "/ai",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> test(@RequestBody TestRequest request) {
        return chatClient
                .prompt()
                .user(request.message())
                .stream()
                .content();
    }

    public record TestRequest(String message) {}
}
