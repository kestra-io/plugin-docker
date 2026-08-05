package io.kestra.plugin.docker.model;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List models available in Docker Model Runner",
    description = "Fetches the list of locally available models from the Docker Model Runner (DMR) REST API."
)
@Plugin(
    examples = {
        @Example(
            title = "List available Docker Model Runner models",
            full = true,
            code = """
                id: docker_model_list
                namespace: company.team

                tasks:
                  - id: list_models
                    type: io.kestra.plugin.docker.model.List
                """
        )
    }
)
public class List extends AbstractModel implements RunnableTask<List.Output> {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rHost = resolvedHost(runContext);

        var request = HttpRequest.builder()
            .uri(URI.create(rHost + "/models"))
            .method("GET")
            .build();

        var rBody = this.execute(runContext, request, String.class, "list models");
        if (rBody == null || rBody.isBlank()) {
            throw new IllegalStateException(
                "Docker Model Runner returned an empty response for GET " + rHost + "/models. Verify the DMR instance is running and reachable."
            );
        }

        java.util.List<ModelInfo> models;
        try {
            models = java.util.List.of(MAPPER.readValue(rBody, ModelInfo[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Failed to parse Docker Model Runner's response for GET " + rHost + "/models: " + e.getOriginalMessage(), e
            );
        }

        runContext.logger().info("Found {} model(s)", models.size());
        return Output.builder().models(models).build();
    }

    @Schema(title = "Information about a single model")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
        @Schema(
            title = "Content digest",
            description = "Content-addressable identifier of the model, e.g. `sha256:...`. This is not a human-readable name, see `tags`."
        ) String id,
        @Schema(
            title = "Tags",
            description = "Human-readable references pointing at this model, e.g. `docker.io/ai/nomic-embed-text-v1.5:latest`."
        ) java.util.List<String> tags,
        @Schema(title = "Creation timestamp", description = "Unix epoch seconds at which the model was created.") Long created,
        @Schema(title = "Model configuration") Config config
    ) {}

    @Schema(title = "Model configuration details")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(
        @Schema(title = "File format", description = "e.g. `gguf`.") String format,
        @Schema(title = "Quantization method", description = "e.g. `MOSTLY_F16`.") String quantization,
        @Schema(title = "Parameter count", description = "e.g. `136.73M`.") String parameters,
        @Schema(title = "Model architecture", description = "e.g. `nomic-bert`.") String architecture,
        @Schema(title = "On-disk size", description = "e.g. `260.86MiB`.") String size
    ) {}

    @Builder
    @Getter
    @Schema(title = "Output of the List task")
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Models available in Docker Model Runner")
        private final java.util.List<ModelInfo> models;
    }
}
