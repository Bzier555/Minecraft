package dev.civcore.sim;

import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.RulesetLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Loads the ruleset that actually ships in the mod's datapack resources. */
public final class TestRulesets {
    public static final Path SHIPPED = Path.of("..", "civ-mod", "src", "main", "resources", "data", "civcore", "civ");

    private TestRulesets() {
    }

    public static RulesetLoader shippedLoader() {
        try {
            return RulesetLoader.fromDirectory(SHIPPED);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Ruleset shipped() {
        return shippedLoader().build();
    }
}
