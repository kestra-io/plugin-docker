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
import static org.hamcrest.Matchers.*;

@KestraTest
class ListTest {

    @Inject
    RunContextFactory runContextFactory;

    private HttpServer server;
    private int port;

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/models", exchange -> {
            var body = "{\"models\":[{\"id\":\"ai/smollm2\",\"created\":1234567890,\"owned_by\":\"docker\"}]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
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
        var task = List.builder()
            .id("list-test")
            .type(List.class.getName())
            .host(Property.ofValue("http://localhost:" + port))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), hasSize(1));
        assertThat(output.getModels().getFirst().id(), is("ai/smollm2"));
        assertThat(output.getModels().getFirst().created(), is(1234567890L));
        assertThat(output.getModels().getFirst().ownedBy(), is("docker"));
    }
}
