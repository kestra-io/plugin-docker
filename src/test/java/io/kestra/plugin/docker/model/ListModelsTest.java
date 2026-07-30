package io.kestra.plugin.docker.model;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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
class ListModelsTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Real DMR shape: a bare JSON array, "id" is a content digest, tags carry the human-readable name.
    private static final String REAL_MODELS_RESPONSE = """
        [{"id":"sha256:653017dd060f5cd345118ff90382ceb213d383de2887820d2f303893d32ef40d","tags":["docker.io/ai/nomic-embed-text-v1.5:latest"],"created":1778854020,"config":{"format":"gguf","quantization":"MOSTLY_F16","parameters":"136.73M","architecture":"nomic-bert","size":"260.86MiB","gguf":{"general.architecture":"nomic-bert"}}}]
        """;

    private int startStub(HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models", handler);
        server.start();
        return server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.getResponseBody().close();
    }

    private ListModels task(int port) {
        return ListModels.builder()
            .id("list-models-test-" + UUID.randomUUID())
            .type(ListModels.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .build();
    }

    @Test
    void happyPath() throws Exception {
        var port = startStub(exchange -> respond(exchange, 200, REAL_MODELS_RESPONSE));

        var runContext = TestsUtils.mockRunContext(runContextFactory, task(port), Map.of());
        var output = task(port).run(runContext);

        assertThat(output.getModels(), hasSize(1));
        var model = output.getModels().getFirst();
        assertThat(model.id(), is("sha256:653017dd060f5cd345118ff90382ceb213d383de2887820d2f303893d32ef40d"));
        assertThat(model.tags(), contains("docker.io/ai/nomic-embed-text-v1.5:latest"));
        assertThat(model.created(), is(1778854020L));
        assertThat(model.config().quantization(), is("MOSTLY_F16"));
        assertThat(model.config().architecture(), is("nomic-bert"));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage() throws Exception {
        var port = startStub(exchange -> respond(exchange, 500, "internal DMR error"));

        var runContext = TestsUtils.mockRunContext(runContextFactory, task(port), Map.of());
        var ex = assertThrows(Exception.class, () -> task(port).run(runContext));

        assertThat(ex.getMessage(), containsString("internal DMR error"));
    }

    @Test
    void emptyBody_throwsActionableError() throws Exception {
        var port = startStub(exchange -> respond(exchange, 200, null));

        var runContext = TestsUtils.mockRunContext(runContextFactory, task(port), Map.of());
        var ex = assertThrows(IllegalStateException.class, () -> task(port).run(runContext));

        assertThat(ex.getMessage(), containsString("empty response"));
    }

    @Test
    void malformedBody_throwsActionableError() throws Exception {
        var port = startStub(exchange -> respond(exchange, 200, "{\"models\": not-json"));

        var runContext = TestsUtils.mockRunContext(runContextFactory, task(port), Map.of());
        var ex = assertThrows(IllegalStateException.class, () -> task(port).run(runContext));

        assertThat(ex.getMessage(), containsString("Failed to parse"));
    }

    // A peer that accepts the TCP connection then closes it without writing a byte makes Apache HttpClient5
    // raise NoHttpResponseException, which io.kestra.core.http.client.HttpClient (as of 1.3.13) rethrows as a
    // bare `RuntimeException`, not an HttpClientException. This reproduces that transport failure deterministically,
    // without relying on a timeout, to prove AbstractModel#execute still wraps it into an actionable error.
    @Test
    void transportFailure_throwsActionableError() throws Exception {
        try (var serverSocket = new ServerSocket(0)) {
            var acceptThread = new Thread(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        serverSocket.accept().close();
                    }
                } catch (java.io.IOException ignored) {
                    // server socket closed, stop accepting
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            var task = task(serverSocket.getLocalPort());
            var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
            var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));

            assertThat(ex.getMessage(), containsString("Failed to list models on Docker Model Runner"));
            assertThat(ex.getCause(), notNullValue());
        }
    }
}
