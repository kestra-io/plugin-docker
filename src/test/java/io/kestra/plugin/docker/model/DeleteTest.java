package io.kestra.plugin.docker.model;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    private int startStub(HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/", handler);
        server.start();
        return server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.getResponseBody().close();
    }

    private Delete task(int port, String model) {
        return Delete.builder()
            .id("delete-test-" + UUID.randomUUID())
            .type(Delete.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue(model))
            .build();
    }

    @Test
    void happyPath_buildsCorrectUrl() throws Exception {
        var capturedUri = new AtomicReference<String>();
        var port = startStub(exchange -> {
            capturedUri.set(exchange.getRequestURI().getPath());
            // Real DMR shape: array of untag/delete actions.
            respond(exchange, 200, "[{\"Untagged\":\"docker.io/ai/smollm2:latest\"},{\"Deleted\":\"sha256:abc\"}]");
        });

        var deleteTask = task(port, "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, deleteTask, Map.of());
        deleteTask.run(runContext);

        assertThat(capturedUri.get(), is("/models/ai/smollm2"));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage() throws Exception {
        var port = startStub(exchange -> respond(exchange, 404, "error while deleting model: model not found"));

        var deleteTask = task(port, "ai/does-not-exist");
        var runContext = TestsUtils.mockRunContext(runContextFactory, deleteTask, Map.of());
        var ex = assertThrows(Exception.class, () -> deleteTask.run(runContext));

        assertThat(ex.getMessage(), containsString("error while deleting model: model not found"));
    }
}
