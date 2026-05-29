package io.kestra.plugin.docker.model;

import java.net.InetSocketAddress;
import java.util.Map;

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
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class PullTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;
    private int port;

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/models/create", exchange -> {
            var body = "{\"status\":\"Pulling from ai/smollm2\"}\n".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void happyPath() throws Exception {
        var task = Pull.builder()
            .id("pull-test")
            .type(Pull.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output, nullValue());
    }
}
