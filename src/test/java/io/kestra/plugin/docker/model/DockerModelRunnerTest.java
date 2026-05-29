package io.kestra.plugin.docker.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips any annotated test when a live Docker Model Runner is not available at localhost:12434.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerModelRunnerTest.Condition.class)
public @interface DockerModelRunnerTest {

    class Condition implements BeforeEachCallback {

        private static final String DMR_URL = "http://localhost:12434/models";
        private static volatile Boolean available = null;

        @Override
        public void beforeEach(ExtensionContext context) {
            if (available == null) {
                available = probe();
            }
            Assumptions.assumeTrue(available, "Docker Model Runner not available at " + DMR_URL);
        }

        private static boolean probe() {
            try (var client = HttpClient.newHttpClient()) {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(DMR_URL))
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
