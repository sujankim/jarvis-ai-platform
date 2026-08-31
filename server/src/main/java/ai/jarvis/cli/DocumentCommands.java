package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLI commands for managing uploaded documents.
 * Supports upload, list, and delete operations.
 */
@Slf4j
@Component
public class DocumentCommands {
    private final CliStateManager state;
    private final CliHttpClient http;

    public DocumentCommands(CliStateManager state, CliHttpClient http) {
        this.state = state;
        this.http = http;
    }
    @Command(
            name = "doc upload",
            description = "Upload a text document. Usage: doc upload --file path/to/file.txt"
    )
    public String uploadDocument(
            @Option(longName = "file", shortName = 'f', description = "Path to the text file to upload", required = true
                    ) String file) {
        if (!state.isLoggedIn()) {
            return "Not logged in.";
        }

        Path path = Path.of(file);
        if (!Files.exists(path)) {
            return "File not found: " + file;
        }
        if (!Files.isRegularFile(path)) {
            return "Path is not a file: " + file;
        }
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read file: " + e.getMessage();
        }
        if (content.isBlank()) {
            return "File is empty: " + file;
        }
        String filename = path.getFileName().toString();

        Map<String, Object> body = Map.of(
                "filename", filename,
                "content", content
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.postWithAuth(
                    "/api/v1/documents",
                    state.getAccessToken(),
                    body,
                    Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> document = response != null
                    ? (Map<String, Object>) response.get("data") : null;

            if (document == null) {
                return "Document uploaded, but server returned no document data.";
            }
            return "Document uploaded: " + document.get("filename")
                    + " (status: " + document.get("status") + ")";
        } catch (Exception e) {
            return "Failed to upload document: " + e.getMessage();
        }
    }
    @Command(
            name = "doc list",
            description = "List uploaded documents"
    )
    public String listDocuments() {
        if (!state.isLoggedIn()) {
            return "Not logged in.";
        }
        try {
            List<Map<String, Object>> documents = fetchDocuments();
            if (documents.isEmpty()) {
                return "No documents found.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append("+----+-------------------------------+------------+--------+\n");
            sb.append("| #  | Filename                      | Status     | Chunks |\n");
            sb.append("+----+-------------------------------+------------+--------+\n");
            for (int i = 0; i < documents.size(); i++) {
                Map<String, Object> doc = documents.get(i);
                String filename = doc.get("filename") != null 
                        ? doc.get("filename").toString() : "Untitled";
                if (filename.length() > 29) {
                    filename = filename.substring(0, 26) + "...";
                }
                String status = doc.get("status") != null 
                        ? doc.get("status").toString() : "UNKNOWN";
                int chunkCount = doc.get("chunkCount") instanceof Number number 
                        ? number.intValue() : 0;

                sb.append(String.format("| %-2d | %-29s | %-10s | %-6d |\n",
                        i + 1, filename, status, chunkCount));
            }
            sb.append("+----+-------------------------------+------------+--------+\n");

            return sb.toString();
        } catch (Exception e) {
            return "Failed to list documents: " + e.getMessage();
        }
    }
    @Command(
            name = "doc delete",
            description = "Delete a document by list number. Usage: doc delete --number 1"
    )
    public String deleteDocument(
            @Option(longName = "number", shortName = 'n', description = "Document number from doc list", required = true
                    ) int number) {
        if (!state.isLoggedIn()) {
            return "Not logged in.";
        }
        try {
            List<Map<String, Object>> documents = fetchDocuments();
            if (documents.isEmpty()) {
                return "No documents found to delete.";
            }
            if (number < 1 || number > documents.size()) {
                return "Invalid number. Use 1-" + documents.size();
            }
            Map<String, Object> target = documents.get(number - 1);
            Object id = target.get("id");
            if (id == null || id.toString().isBlank()) {
                return "Selected document has no id.";
            }

            http.deleteWithAuth(
                    "/api/v1/documents/" + id,
                    state.getAccessToken());

            String filename = target.get("filename") != null 
                    ? target.get("filename").toString() : id.toString();
            return "Document deleted: " + filename;
        } catch (Exception e) {
            return "Failed to delete document: " + e.getMessage();
        }
    }
    
    private List<Map<String, Object>> fetchDocuments() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = http.get(
                "/api/v1/documents",
                state.getAccessToken(),
                Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = response != null
                    ? (List<Map<String, Object>>) response.get("data") : List.of();

        return documents != null ? documents : List.of();
    }
}