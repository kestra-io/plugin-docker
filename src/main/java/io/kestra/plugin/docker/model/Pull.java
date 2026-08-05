package io.kestra.plugin.docker.model;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        The model is streamed line by line; each status line is logged at debug level,
        and a summary is logged at info level once the pull completes.
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
        var rModel = runContext.render(this.model).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("The `model` property is required, e.g. `ai/smollm2`."));
        var rHost = resolvedHost(runContext);
        var logger = runContext.logger();

        var request = HttpRequest.builder()
            .uri(URI.create(rHost + "/models/create"))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(Map.of("from", rModel)))
            .build();

        this.executeStreaming(runContext, request, line ->
        {
            if (line.isBlank()) {
                return;
            }

            try {
                var node = MAPPER.readTree(line);
                if (node.has("error")) {
                    throw new IllegalStateException(
                        "Docker Model Runner reported an error while pulling model '" + rModel + "': " + node.get("error").asText()
                    );
                }
                logger.debug("{}", line);
            } catch (JsonProcessingException e) {
                // Non-JSON lines are treated as plain status output.
                logger.debug("{}", line);
            }
        }, "pull model '" + rModel + "'");

        logger.info("Pulled model {}", rModel);
        return null;
    }
}
