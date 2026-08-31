package ai.jarvis.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentCommands Unit Test")
class DocumentCommandsTest{
    @Mock
    private CliHttpClient http;
    @Mock
    private CliStateManager state;

    private DocumentCommands documentCommands;

    @BeforeEach
    void setUp() {
        documentCommands = new DocumentCommands(state, http);
    }
    @Nested
    @DisplayName("Unauthorized Context Tests")
    class UnauthorizedContext {
        @BeforeEach
        void setUp() {
            when(state.isLoggedIn()).thenReturn(false);
        }
        @Test
        @DisplayName("uploadDocument should return unauthorized message when not logged in")
        void testUploadDocument_Unauthorized() {
            String result = documentCommands.uploadDocument("note.txt");
            assertThat(result).contains("Not logged in");
        }
        @Test
        @DisplayName("listDocuments should return unauthorized message when not logged in")
        void testListDocuments_Unauthorized() {
            String result = documentCommands.listDocuments();
            assertThat(result).contains("Not logged in");
        }
        @Test
        @DisplayName("deleteDocument should return unauthorized message when not logged in")
        void testDeleteDocument_Unauthorized() {
            String result = documentCommands.deleteDocument(1);
            assertThat(result).contains("Not logged in");
        }
    }

    @Nested
    @DisplayName("Authorized Context Tests")
    class AuthorizedContext {
        @BeforeEach
        void setUp() {
            when(state.isLoggedIn()).thenReturn(true);
        }
        //upload
        @Test
        @DisplayName("uploadDocument should return file not found when path does not exist")
        void testUploadDocument_FileNotFound() {
            String result = documentCommands.uploadDocument("missing.txt");
            assertThat(result).contains("File not found");
        }
        @Test
        @DisplayName("uploadDocument should return file is empty when file has blank content")
        void testUploadDocument_EmptyFile(@TempDir Path tempDir) throws Exception {
            Path file = tempDir.resolve("empty.txt");
            Files.writeString(file, "   ");

            String result = documentCommands.uploadDocument(file.toString());
            assertThat(result).contains("File is empty");
        }
        @Test
        @DisplayName("uploadDocument should return server returned no document data when server response is null")
        void testUploadDocument_NullServerResponse(@TempDir Path tempDir) throws Exception {
            when(state.getAccessToken()).thenReturn("test-token");
            
            Path file = tempDir.resolve("note.txt");
            Files.writeString(file, "Hello");
            
            Map<String, Object> response = null;
            when(http.postWithAuth(eq("/api/v1/documents"), eq("test-token"),
                    any(), eq(Map.class))).thenReturn(response);
            String result = documentCommands.uploadDocument(file.toString());
            assertThat(result).contains("server returned no document data");
            assertThat(result).doesNotContain("Document uploaded:");
        }
        @Test
        @DisplayName("uploadDocument should upload text file successfully")
        void testUploadDocument_Success(@TempDir Path tempDir) throws Exception {
            when(state.getAccessToken()).thenReturn("test-token");
            
            Path file = tempDir.resolve("note.txt");
            Files.writeString(file, "Hello");
            
            Map<String, Object> document = Map.of(
                    "id", "doc-1", 
                    "filename", "note.txt",
                    "status", "PENDING"
            );
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", document
            );
            when(http.postWithAuth(eq("/api/v1/documents"), eq("test-token"),
                    any(), eq(Map.class))).thenReturn(response);
            String result = documentCommands.uploadDocument(file.toString());
            assertThat(result).contains("Document uploaded");
            assertThat(result).contains("note.txt");
            assertThat(result).contains("PENDING");

            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(http).postWithAuth(eq("/api/v1/documents"), eq("test-token"), bodyCaptor.capture(), eq(Map.class));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String,Object>) bodyCaptor.getValue();
            assertThat(body.get("filename")).isEqualTo("note.txt");
            assertThat(body.get("content")).isEqualTo("Hello");
        }
        //list
        @Test
        @DisplayName("listDocuments should report when no documents exist")
        void testListDocuments_Empty() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", List.of()
            );
            when(http.get("/api/v1/documents", "test-token", Map.class)).thenReturn(response);
            String result = documentCommands.listDocuments();
            assertThat(result).contains("No documents found");
        }
        @Test
        @DisplayName("listDocuments should show uploaded documents")
        void testListDocuments_Success() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> doc1 = Map.of(
                    "id", "doc-1",
                    "filename", "note1.txt",
                    "status", "READY",
                    "chunkCount", 3
            );
            Map<String, Object> doc2 = Map.of(
                    "id", "doc-2",
                    "filename", "note2.txt",
                    "status", "PENDING",
                    "chunkCount", 5
            );
            List<Map<String, Object>> documents = List.of(doc1, doc2);
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", documents
            );
            when(http.get("/api/v1/documents", "test-token", Map.class)).thenReturn(response);
            String result = documentCommands.listDocuments();
            assertThat(result).contains("note1.txt");
            assertThat(result).contains("note2.txt");
        }
        //delete
        @Test
        @DisplayName("deleteDocument should report when no documents exist")
        void testDeleteDocument_Empty() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", List.of()
            );
            when(http.get("/api/v1/documents", "test-token", Map.class)).thenReturn(response);
            String result = documentCommands.deleteDocument(1);
            assertThat(result).contains("No documents found");
        }
        @Test
        @DisplayName("deleteDocument should fail with number 0")
        void testDeleteDocument_Zero() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> doc = Map.of(
                    "id", "doc-1",
                    "filename", "note1.txt"
            );
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", List.of(doc)
            );

            when(http.get("/api/v1/documents", "test-token", Map.class))
                    .thenReturn(response);

            String result = documentCommands.deleteDocument(0);
            assertThat(result).contains("Invalid number");            
        }
        @Test
        @DisplayName("deleteDocument should fail with out of bounds number")
        void testDeleteDocument_OutOfBounds() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> doc = Map.of(
                    "id", "doc-1",
                    "filename", "note1.txt"
            );
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", List.of(doc)
            );

            when(http.get("/api/v1/documents", "test-token", Map.class))
                    .thenReturn(response);

            String result = documentCommands.deleteDocument(3);
            assertThat(result).contains("Invalid number");
        }

        @Test
        @DisplayName("deleteDocument should not print success when delete returns 404")
        void testDeleteDocument_DeleteReturns404() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> document = Map.of(
                    "id", "doc-1",
                    "filename", "note.txt"
            );
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", List.of(document)
            );

            when(http.get(eq("/api/v1/documents"), eq("test-token"), eq(Map.class)))
                    .thenReturn(response);
            doThrow(new RuntimeException("Delete failed: HTTP 404"))
                    .when(http)
                    .deleteWithAuth("/api/v1/documents/doc-1", "test-token");

            String result = documentCommands.deleteDocument(1);
            assertThat(result).contains("Failed to delete document");
            assertThat(result).contains("Delete failed: HTTP 404");
            assertThat(result).doesNotContain("Document deleted");
            verify(http).deleteWithAuth("/api/v1/documents/doc-1", "test-token");
        }

        @Test
        @DisplayName("deleteDocument should delete document")
        void testDeleteDocument_Success() {
            when(state.getAccessToken()).thenReturn("test-token");

            Map<String, Object> doc1 = Map.of(
                    "id", "doc-1",
                    "filename", "note1.txt",
                    "status", "READY",
                    "chunkCount", 3
            );
            Map<String, Object> doc2 = Map.of(
                    "id", "doc-2",
                    "filename", "note2.txt",
                    "status", "PENDING",
                    "chunkCount", 5
            );
            List<Map<String, Object>> documents = List.of(doc1, doc2);            
            Map<String, Object> response = Map.of(
                    "success", true,
                    "data", documents
            );
            when(http.get("/api/v1/documents", "test-token", Map.class)).thenReturn(response);
            String result = documentCommands.deleteDocument(1);
            assertThat(result).contains("Document deleted");
            assertThat(result).contains("note1.txt");
        }
    }
}