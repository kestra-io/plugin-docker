package io.kestra.plugin.docker.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ConfigureTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;
    private int port;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/models/", exchange -> {
            capturedBody.set(
                new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining())
            );
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void happyPath_sendsContextSizeAndFlags() throws Exception {
        var task = Configure.builder()
            .id("configure-test")
            .type(Configure.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .runtimeFlags(Property.ofValue(List.of("--temp 0.7")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        assertThat(capturedBody.get(), containsString("\"contextSize\":4096"));
        assertThat(capturedBody.get(), containsString("\"--temp 0.7\""));
    }

    @Test
    void skipsCallWhenNothingSet() throws Exception {
        var task = Configure.builder()
            .id("configure-noop-test")
            .type(Configure.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        // No HTTP call made — capturedBody stays null
        assertThat(capturedBody.get(), nullValue());
    }
}
