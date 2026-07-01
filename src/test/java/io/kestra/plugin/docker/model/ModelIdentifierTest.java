package io.kestra.plugin.docker.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ModelIdentifierTest {

    @Test
    void parse_simpleNamespace() {
        var id = ModelIdentifier.parse("ai/smollm2");
        assertThat(id.namespace(), is("ai"));
        assertThat(id.name(), is("smollm2"));
    }

    @Test
    void parse_deepPath() {
        // hf.co/org/repo → namespace=hf.co, name=org/repo
        var id = ModelIdentifier.parse("hf.co/org/repo");
        assertThat(id.namespace(), is("hf.co"));
        assertThat(id.name(), is("org/repo"));
    }

    @Test
    void parse_bareNameDefaultsToAi() {
        var id = ModelIdentifier.parse("smollm2");
        assertThat(id.namespace(), is("ai"));
        assertThat(id.name(), is("smollm2"));
    }
}
