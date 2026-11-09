package dev.civcore.sim.ruleset;

import dev.civcore.sim.ruleset.Ruleset.BuildingDef;
import dev.civcore.sim.ruleset.Ruleset.TechDef;
import dev.civcore.sim.ruleset.Ruleset.UnitDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checks references and the tech graph. An empty list means the ruleset is usable. */
public final class RulesetValidator {
    private RulesetValidator() {
    }

    public static List<String> validate(Ruleset r) {
        List<String> errors = new ArrayList<>();
        for (TechDef t : r.techs().values()) {
            if (t.cost() <= 0) errors.add("tech " + t.id() + ": cost must be positive");
            for (String req : t.requires()) {
                if (!r.techs().containsKey(req)) errors.add("tech " + t.id() + ": requires unknown tech " + req);
            }
            for (String b : t.unlocks().buildings()) {
                if (!r.buildings().containsKey(b)) errors.add("tech " + t.id() + ": unlocks unknown building " + b);
            }
            for (String u : t.unlocks().units()) {
                if (!r.units().containsKey(u)) errors.add("tech " + t.id() + ": unlocks unknown unit " + u);
            }
        }
        for (BuildingDef b : r.buildings().values()) {
            if (b.cost() <= 0) errors.add("building " + b.id() + ": cost must be positive");
            if (b.requiresTech() != null && !r.techs().containsKey(b.requiresTech())) {
                errors.add("building " + b.id() + ": requires unknown tech " + b.requiresTech());
            }
        }
        for (UnitDef u : r.units().values()) {
            if (u.cost() <= 0) errors.add("unit " + u.id() + ": cost must be positive");
            if (u.requiresTech() != null && !r.techs().containsKey(u.requiresTech())) {
                errors.add("unit " + u.id() + ": requires unknown tech " + u.requiresTech());
            }
            if (u.requiresResource() != null && dev.civcore.sim.worldplan.ResourceType.byId(u.requiresResource()) == null) {
                errors.add("unit " + u.id() + ": requires unknown resource " + u.requiresResource());
            }
        }
        findCycle(r).ifPresent(cycle -> errors.add("tech prerequisites contain a cycle: " + String.join(" -> ", cycle)));
        return errors;
    }

    private static java.util.Optional<List<String>> findCycle(Ruleset r) {
        Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done
        for (String id : r.techs().keySet()) {
            List<String> path = new ArrayList<>();
            List<String> cycle = dfs(r, id, state, path);
            if (cycle != null) return java.util.Optional.of(cycle);
        }
        return java.util.Optional.empty();
    }

    private static List<String> dfs(Ruleset r, String id, Map<String, Integer> state, List<String> path) {
        Integer s = state.get(id);
        if (s != null && s == 2) return null;
        path.add(id);
        if (s != null && s == 1) return new ArrayList<>(path.subList(path.indexOf(id), path.size()));
        state.put(id, 1);
        TechDef t = r.techs().get(id);
        if (t != null) {
            for (String req : t.requires()) {
                List<String> c = dfs(r, req, state, path);
                if (c != null) return c;
            }
        }
        state.put(id, 2);
        path.remove(path.size() - 1);
        return null;
    }
}
