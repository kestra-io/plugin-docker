package io.kestra.plugin.docker.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
class PullTest {

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
        server.createContext("/models/create", handler);
        server.start();
        return server.getAddress().getPort();
    }

    private static String requestBody(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
        return new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.getResponseBody().close();
    }

    private Pull task(int port, String model) {
        return Pull.builder()
            .id("pull-test-" + UUID.randomUUID())
            .type(Pull.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue(model))
            .build();
    }

    @Test
    void happyPath_sendsFromFieldAndStreamsProgress() throws Exception {
        var capturedBody = new AtomicReference<String>();
        var port = startStub(exchange -> {
            capturedBody.set(requestBody(exchange));
            // Real DMR streaming shape captured against a live instance.
            respond(exchange, 200, """
                {"type":"progress","message":"Downloaded: 0.01 MB","total":274303184,"layer":{"id":"sha256:abc","size":12624,"current":12624},"mode":"pull"}
                {"type":"success","message":"Model pulled successfully"}
                """);
        });

        var pullTask = task(port, "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, pullTask, Map.of());
        var output = pullTask.run(runContext);

        assertThat(output, nullValue());
        // Verified against a real DMR: the correct request field is "from", not "fromImage".
        assertThat(capturedBody.get(), is("{\"from\":\"ai/smollm2\"}"));
    }

    @Test
    void errorLine_throwsWithDmrMessage() throws Exception {
        var port = startStub(exchange -> respond(exchange, 200, """
            {"type":"error","error":"Invalid model reference"}
            """));

        var pullTask = task(port, "not-a-model");
        var runContext = TestsUtils.mockRunContext(runContextFactory, pullTask, Map.of());
        var ex = assertThrows(IllegalStateException.class, () -> pullTask.run(runContext));

        assertThat(ex.getMessage(), containsString("Invalid model reference"));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage() throws Exception {
        var port = startStub(exchange -> respond(exchange, 400, "Invalid model reference"));

        var pullTask = task(port, "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, pullTask, Map.of());
        var ex = assertThrows(Exception.class, () -> pullTask.run(runContext));

        assertThat(ex.getMessage(), containsString("Invalid model reference"));
    }

    // Same transport failure as ListModelsTest#transportFailure_throwsActionableError, exercised through
    // AbstractModel#executeStreaming this time: a peer closing the connection before any response bytes
    // makes Apache HttpClient5 raise NoHttpResponseException, which core's HttpClient rethrows as a bare
    // RuntimeException rather than an HttpClientException.
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

            var pullTask = task(serverSocket.getLocalPort(), "ai/smollm2");
            var runContext = TestsUtils.mockRunContext(runContextFactory, pullTask, Map.of());
            var ex = assertThrows(IllegalStateException.class, () -> pullTask.run(runContext));

            assertThat(ex.getMessage(), containsString("Failed to pull model 'ai/smollm2' on Docker Model Runner"));
            assertThat(ex.getCause(), notNullValue());
        }
    }
}
