package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Config;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.kestra.plugin.scripts.runner.docker.DockerService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AbstractDockerHelper {
    public String getPassword() {
        return "testpassword";
    }

    public String getUsername() {
        return "testuser";
    }

    public String getRegistry() {
        return "localhost:5000";
    }

    public String getPrivateImage() {
        return "ubuntu:unit-test";
    }

    boolean containerExists(String containerId, RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        try(DockerClient client = getDockerClient(runContext, null, null, null)) {
            for(Container container : client.listContainersCmd().withShowAll(true).exec()) {
                if (container.getId().equals(containerId)) return true;
            }
        }
        return false;
    }

    boolean imageExists(RunContext runContext, String image) throws IllegalVariableEvaluationException, IOException {
        return imageExists(runContext, image, null);
    }

    boolean imageExists(RunContext runContext, String image, Credentials credentials) throws IllegalVariableEvaluationException, IOException {
        return getImageId(runContext, image, credentials) != null;
    }

    String getImageId(RunContext runContext, String image, Credentials credentials) throws IllegalVariableEvaluationException, IOException {
        try(DockerClient client = getDockerClient(runContext, image, credentials, null)) {
            List<Image> images = client.listImagesCmd().exec();
            return images.stream().filter(i -> {
                for (String repoTag : i.getRepoTags()) {
                    if (repoTag.contains(image)) {
                        return true;
                    }
                }
                return false;
            }).findFirst().map(Image::getId).orElse(null);
        }
    }

    void rmImageIfExists(RunContext runContext, String image, Credentials credentials) throws IllegalVariableEvaluationException, IOException {
        String id = getImageId(runContext, image, credentials);
        if (id != null) {
            try(DockerClient client = getDockerClient(runContext, image, credentials, null)) {
                client.removeImageCmd(id)
                    .withForce(true)
                    .withNoPrune(false)
                    .exec();
            }
        }
    }

    void rmImageIfExists(RunContext runContext, String image) throws IllegalVariableEvaluationException, IOException {
        rmImageIfExists(runContext, image, null);
    }

    DockerClient getDockerClient(RunContext runContext, String image, Credentials credentials, Config config) throws IllegalVariableEvaluationException, IOException {
        return DockerService.client(runContext, null, config, credentials, image);
    }

    String runContainer(RunContextFactory runContextFactory, String image) throws Exception {
        Run run = Run.builder()
            .id(Run.class.getSimpleName())
            .type(Run.class.getName())
            .wait(Property.of(false))
            .containerImage(Property.of(image))
            .build();
        RunContext runRunContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());
        ScriptOutput runOutput = run.run(runRunContext);

        Docker.DockerTaskRunnerDetailResult detailResult = (Docker.DockerTaskRunnerDetailResult) runOutput.getTaskRunner();
        return detailResult.getContainerId();
    }

    void buildImage(RunContextFactory runContextFactory, String image, String label) throws Exception {
        Build task = Build.builder()
            .id(Build.class.getSimpleName())
            .type(Build.class.getName())
            .platforms(Property.of(List.of("linux/amd64")))
            .buildArgs(Property.of(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.of(Map.of(label, "true")))
            .tags(Property.of(List.of(image)))
            .dockerfile(Property.of("""
                FROM ubuntu
                ARG APT_PACKAGES=""

                RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
            """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        task.run(runContext);
    }
}
