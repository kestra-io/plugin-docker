package io.kestra.plugin.docker.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractModel extends Task {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    // Idle timeout between reads, not a total duration cap — safe for long Pull streams as long as data keeps flowing.
    private static final Duration READ_IDLE_TIMEOUT = Duration.ofSeconds(60);

    @Schema(
        title = "Docker Model Runner host",
        description = "Base URL of the Docker Model Runner REST API. Override when DMR is exposed on a non-default address."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> host = Property.ofValue("http://localhost:12434");

    protected String resolvedHost(RunContext runContext) throws IllegalVariableEvaluationException {
        var rHost = runContext.render(this.host).as(String.class).orElse("http://localhost:12434");
        return rHost.endsWith("/") ? rHost.substring(0, rHost.length() - 1) : rHost;
    }

    private HttpClient httpClient(RunContext runContext) throws IllegalVariableEvaluationException {
        return HttpClient.builder()
            .runContext(runContext)
            .configuration(HttpConfiguration.builder()
                .timeout(TimeoutConfiguration.builder()
                    .connectTimeout(Property.ofValue(CONNECT_TIMEOUT))
                    .readIdleTimeout(Property.ofValue(READ_IDLE_TIMEOUT))
                    .build())
                .build())
            .build();
    }

    /**
     * Executes a request and returns the response body. On a transport failure or a non-2xx
     * response, wraps Kestra's exception (which already carries DMR's status code and body) into
     * an actionable message naming the attempted action.
     */
    protected <T> T execute(RunContext runContext, HttpRequest request, Class<T> responseType, String action) throws IllegalVariableEvaluationException, IOException {
        try (var client = this.httpClient(runContext)) {
            return client.request(request, responseType).getBody();
        } catch (HttpClientException e) {
            throw failure(action, e);
        }
    }

    /**
     * Executes a request and streams the response body line by line, closing the underlying
     * stream once fully consumed. Suited to newline-delimited progress payloads (e.g. Pull).
     */
    protected void executeStreaming(RunContext runContext, HttpRequest request, Consumer<String> lineConsumer, String action) throws IllegalVariableEvaluationException, IOException {
        try (var client = this.httpClient(runContext)) {
            client.request(request, response -> readLines(response.getBody(), lineConsumer));
        } catch (HttpClientException e) {
            throw failure(action, e);
        } catch (UncheckedIOException e) {
            throw failure(action, e.getCause());
        }
    }

    private static void readLines(InputStream inputStream, Consumer<String> lineConsumer) {
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static IllegalStateException failure(String action, Exception cause) {
        return new IllegalStateException("Failed to " + action + " on Docker Model Runner: " + cause.getMessage(), cause);
    }
}
