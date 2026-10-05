package io.kestra.plugin.docker.cli;

import java.util.Objects;
import java.util.regex.Pattern;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PushResponseItem;

import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.runners.RunContext;

import lombok.Getter;

@Getter
public class PushResponseItemCallback extends ResultCallback.Adapter<PushResponseItem> {
    private static final Pattern REGISTRY_HOST = Pattern.compile("https?://([^/\"\\s]+)");

    private final RunContext runContext;
    private Exception error;

    public PushResponseItemCallback(RunContext runContext) {
        super();
        this.runContext = runContext;
    }

    @Override
    public void onNext(PushResponseItem item) {
        super.onNext(item);

        if (item.getErrorDetail() != null) {
            this.error = new Exception(withRegistryHint(item.getErrorDetail().getMessage()));
        }

        //noinspection deprecation
        if (item.getProgress() != null) {
            this.runContext.logger().debug("{} {}", item.getId(), item.getProgress());
        } else if (
            item.getRawValues().containsKey("status") &&
                !item.getRawValues().get("status").toString().trim().isEmpty()
        ) {
            this.runContext.logger().info("{} {}", item.getId(), item.getRawValues().get("status").toString().trim());
        }

        if (
            item.getProgressDetail() != null &&
                item.getProgressDetail().getCurrent() != null &&
                Objects.equals(item.getProgressDetail().getCurrent(), item.getProgressDetail().getTotal())
        ) {
            runContext.metric(Counter.of("bytes", item.getProgressDetail().getTotal()));
        }
    }

    @Override
    public void onError(Throwable throwable) {
        super.onError(throwable);
        this.error = new Exception(throwable);
    }

    // The push runs inside the Docker daemon, so registry TLS can only be fixed in the daemon configuration.
    static String withRegistryHint(String message) {
        if (message == null) {
            return null;
        }

        var matcher = REGISTRY_HOST.matcher(message);
        var host = matcher.find() ? matcher.group(1) : "<registry-host>";

        if (message.contains("x509:")) {
            return ("%s. The Docker daemon does not trust the TLS certificate of registry '%s': add the registry CA certificate to " +
                "'/etc/docker/certs.d/%s/ca.crt' on the Docker host (no restart needed), or list '%s' under 'insecure-registries' in " +
                "'/etc/docker/daemon.json' and restart Docker.").formatted(message, host, host, host);
        }

        if (message.contains("server gave HTTP response to HTTPS client")) {
            return ("%s. Registry '%s' serves plain HTTP: list it under 'insecure-registries' in '/etc/docker/daemon.json' on the Docker host " +
                "and restart Docker.").formatted(message, host);
        }

        return message;
    }
}
