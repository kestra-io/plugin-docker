package io.kestra.plugin.docker.model;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
class DeleteTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;
    private int port;
    private final AtomicReference<String> capturedUri = new AtomicReference<>();

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/models/", exchange -> {
            capturedUri.set(exchange.getRequestURI().getPath());
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
    void happyPath_buildsCorrectUrl() throws Exception {
        var task = Delete.builder()
            .id("delete-test")
            .type(Delete.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        assertThat(capturedUri.get(), is("/models/ai/smollm2"));
    }
}
