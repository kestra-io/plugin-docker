package io.kestra.plugin.docker;

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
}
