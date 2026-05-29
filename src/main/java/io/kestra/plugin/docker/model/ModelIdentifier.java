package io.kestra.plugin.docker.model;

/**
 * Parses a DMR model identifier into namespace and name path components.
 * Examples: "ai/smollm2" → ["ai", "smollm2"], "hf.co/org/repo" → ["hf.co", "org/repo"],
 * "smollm2" (no slash) → ["ai", "smollm2"].
 */
record ModelIdentifier(String namespace, String name) {

    static ModelIdentifier parse(String model) {
        var slash = model.indexOf('/');
        if (slash < 0) {
            return new ModelIdentifier("ai", model);
        }
        return new ModelIdentifier(model.substring(0, slash), model.substring(slash + 1));
    }
}
