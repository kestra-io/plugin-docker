package io.kestra.plugin.docker.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pull a model via Docker Model Runner",
    description = """
        Pulls a model from a registry using the Docker Model Runner (DMR) REST API.
        The model is streamed line by line; each status line is logged as info.
        Throws if any line contains an error field or if the server returns a non-2xx response.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Pull a model with Docker Model Runner",
            full = true,
            code = """
                id: docker_model_pull
                namespace: company.team

                tasks:
                  - id: pull
                    type: io.kestra.plugin.docker.model.Pull
                    model: ai/smollm2
                """
        )
    }
)
public class Pull extends AbstractModel implements RunnableTask<VoidOutput> {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "Model identifier",
        description = "The model to pull, e.g. `ai/smollm2` or `hf.co/org/repo`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> model;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rModel = runContext.render(this.model).as(String.class).orElseThrow();
        var rHost = resolvedHost(runContext);
        var logger = runContext.logger();

        var request = HttpRequest.builder()
            .uri(URI.create(rHost + "/models/create"))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.builder().content(Map.of("fromImage", rModel)).build())
            .build();

        try (var client = httpClient(runContext)) {
            client.request(request, response -> {
                try (var reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("{}", line);
                        try {
                            var node = MAPPER.readTree(line);
                            if (node.has("error")) {
                                throw new RuntimeException("Error pulling model " + rModel + ": " + node.get("error").asText());
                            }
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            // Non-JSON lines are treated as plain status output
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return null;
    }
}
