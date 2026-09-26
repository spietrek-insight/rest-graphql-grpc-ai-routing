package com.insight.router;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RouterPropertiesTest {
    @Test
    void modelsAreIndependentAndLegacyModelOnlyAppliesToTheDefaultProvider() {
        var old = new RouterProperties(0, "jev", "", "custom-jev", "", "");
        assertThat(old.modelFor("jev")).isEqualTo("custom-jev");
        assertThat(old.modelFor("ollama")).isEqualTo("qwen3:4b-instruct-2507-q4_K_M");
        var defaults = new RouterProperties(0, "ollama", "", "", "", "", null, null);
        assertThat(defaults.modelFor("clm")).isEqualTo("clm-latest");
        assertThat(defaults.clmUrl()).isEmpty();
        assertThat(defaults.clmKey()).isEmpty();
        assertThat(defaults.modelFor("jev")).isEqualTo("jev-latest");
        assertThat(defaults.modelFor("ollama")).isEqualTo("qwen3:4b-instruct-2507-q4_K_M");
        var explicit = new RouterProperties(0, "jev", "", "legacy", "", "", "local", "hosted");
        assertThat(explicit.modelFor("jev")).isEqualTo("hosted");
        assertThat(explicit.modelFor("ollama")).isEqualTo("local");
        var clm =
                new RouterProperties(
                        0, "clm", "", "legacy-clm", "", "", "local", "hosted", null, null, null);
        assertThat(clm.modelFor("clm")).isEqualTo("legacy-clm");
        assertThat(clm.modelFor("jev")).isEqualTo("hosted");
        assertThat(clm.clmUrl()).isEmpty();
        assertThat(clm.clmKey()).isEmpty();
        assertThatThrownBy(() -> clm.modelFor("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
