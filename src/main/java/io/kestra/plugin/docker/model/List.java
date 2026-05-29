package io.kestra.plugin.docker.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.Output;
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

        var request = HttpRequest.newBuilder()
            .uri(URI.create(rHost + "/models"))
            .GET()
            .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("DMR /models returned HTTP " + response.statusCode());
            }
            var modelList = MAPPER.readValue(response.body(), ModelsResponse.class);
            runContext.logger().info("Found {} model(s)", modelList.models().size());
            return Output.builder().models(modelList.models()).build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelsResponse(java.util.List<ModelInfo> models) {}

    @Schema(title = "Information about a single model")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
        @Schema(title = "Model identifier") String id,
        @Schema(title = "Creation timestamp (Unix seconds)") Long created,
        @Schema(title = "Model owner") @JsonProperty("owned_by") String ownedBy
    ) {}

    @Builder
    @Getter
    @Schema(title = "Output of the List task")
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Models available in Docker Model Runner")
        private final java.util.List<ModelInfo> models;
    }
}
