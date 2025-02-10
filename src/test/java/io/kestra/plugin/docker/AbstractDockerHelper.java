package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Config;
import com.github.dockerjava.api.model.Image;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import io.kestra.plugin.scripts.runner.docker.DockerService;

import java.io.IOException;
import java.util.List;

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
}
