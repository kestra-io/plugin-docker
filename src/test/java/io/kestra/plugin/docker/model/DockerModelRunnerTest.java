package io.kestra.plugin.docker.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips any annotated test unless a developer has explicitly opted in via the {@value Condition#OPT_IN_ENV_VAR}
 * environment variable AND a live Docker Model Runner answers at localhost:12434.
 * <p>
 * These tests pull and delete the real {@code ai/smollm2} tag against whatever DMR instance is reachable.
 * DMR tags are not test-namespaced: if the developer running the suite already has {@code ai/smollm2} pulled
 * for their own use, opting in will delete it. Reachability alone is not a safe enough gate for that reason —
 * an explicit, informed opt-in is required in addition.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerModelRunnerTest.Condition.class)
public @interface DockerModelRunnerTest {

    class Condition implements BeforeEachCallback {

        private static final String OPT_IN_ENV_VAR = "DMR_IT_TESTS";
        private static final String DMR_URL = "http://localhost:12434/models";
        private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
        private static volatile Boolean available = null;

        @Override
        public void beforeEach(ExtensionContext context) {
            Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv(OPT_IN_ENV_VAR)),
                "Skipped: set " + OPT_IN_ENV_VAR + "=true to run Docker Model Runner integration tests. "
                    + "Warning: they pull and delete the real 'ai/smollm2' tag on the DMR instance at " + DMR_URL
                    + " — only enable this against a DMR you don't mind losing that model on."
            );

            if (available == null) {
                available = probe();
            }
            Assumptions.assumeTrue(available, "Skipped: Docker Model Runner not available at " + DMR_URL);
        }

        private static boolean probe() {
            try (var client = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build()) {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(DMR_URL))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() / 100 == 2;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
