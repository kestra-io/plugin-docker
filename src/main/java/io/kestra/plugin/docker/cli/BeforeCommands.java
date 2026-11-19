package io.kestra.plugin.docker.cli;

import java.util.List;
import java.util.stream.Collectors;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;

/**
 * Runs {@code beforeCommands} ahead of a fixed argument list.
 * Without {@code beforeCommands} the arguments are passed to the container untouched. With them, the wrapper can only run
 * a shell script, so the arguments are shell-quoted into a single command line executed by the task interpreter.
 */
final class BeforeCommands {
    private BeforeCommands() {}

    static CommandsWrapper apply(AbstractExecScript task, RunContext runContext, CommandsWrapper commands, List<String> arguments)
        throws IllegalVariableEvaluationException {
        var rBeforeCommands = runContext.render(task.getBeforeCommands()).asList(String.class);
        if (rBeforeCommands.isEmpty()) {
            return commands.withCommands(Property.ofValue(arguments));
        }

        var commandLine = arguments.stream().map(BeforeCommands::quote).collect(Collectors.joining(" "));
        return commands
            .withInterpreter(task.getInterpreter())
            .withBeforeCommands(task.getBeforeCommands())
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(commandLine)));
    }

    private static String quote(String argument) {
        return "'" + argument.replace("'", "'\\''") + "'";
    }
}
