package dev.civcore.sim.ai;

import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;

import java.util.ArrayList;
import java.util.List;

/** Runs every AI civ's decisions for one strategic tick, through the normal command path. */
public final class AiTurn {
    private AiTurn() {
    }

    /** Applies all AI commands; returns the messages of commands the rules rejected (for logging and tests). */
    public static List<String> run(GameState s, Ruleset r) {
        List<String> rejected = new ArrayList<>();
        for (Civilization civ : List.copyOf(s.civs.values())) {
            if (!AiController.isAi(civ) || civ.eliminated) continue;
            for (Command c : AiController.decide(s, r, civ, AiController.Personality.of(s, civ))) {
                CommandResult res = Simulation.apply(s, r, c);
                if (!res.ok()) rejected.add(civ.name + ": " + c + " -> " + res.message());
            }
        }
        return rejected;
    }
}
