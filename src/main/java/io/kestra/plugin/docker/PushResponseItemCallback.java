package io.kestra.plugin.docker;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PushResponseItem;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.runners.RunContext;
import lombok.Getter;

import java.util.Objects;

@Getter
public class PushResponseItemCallback extends ResultCallback.Adapter<PushResponseItem> {
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
            this.error = new Exception(item.getErrorDetail().getMessage());
        }

        //noinspection deprecation
        if (item.getProgress() != null) {
            this.runContext.logger().debug("{} {}", item.getId(), item.getProgress());
        } else if (item.getRawValues().containsKey("status") &&
            !item.getRawValues().get("status").toString().trim().isEmpty()
        ) {
            this.runContext.logger().info("{} {}", item.getId(), item.getRawValues().get("status").toString().trim());
        }

        if (item.getProgressDetail() != null &&
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
}
