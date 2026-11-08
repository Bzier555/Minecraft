package dev.civcore.mod.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.civcore.mod.CivCoreMod;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.RulesetLoader;
import dev.civcore.sim.ruleset.RulesetValidator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads {@code data/<namespace>/civ/{techs,buildings,units}/*.json} from all datapacks into a {@link Ruleset}.
 * Reloads with /reload. An invalid ruleset is logged and the previous one is kept.
 */
public final class RulesetReloadListener extends SimpleJsonResourceReloadListener {
    private static volatile Ruleset current = Ruleset.empty();
    private static volatile List<String> lastErrors = List.of();

    public RulesetReloadListener() {
        super(new Gson(), "civ");
    }

    public static Ruleset current() {
        return current;
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        RulesetLoader loader = new RulesetLoader();
        for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(entries).entrySet()) {
            String path = e.getKey().getPath(); // e.g. "techs/agriculture"
            int slash = path.indexOf('/');
            if (slash < 0) continue;
            loader.add(path.substring(0, slash), e.getKey().toString(), e.getValue());
        }
        Ruleset r = loader.build();
        List<String> errors = new ArrayList<>(loader.errors());
        errors.addAll(RulesetValidator.validate(r));
        lastErrors = List.copyOf(errors);
        if (!errors.isEmpty()) {
            errors.forEach(err -> CivCoreMod.LOG.error("Civ ruleset: {}", err));
            CivCoreMod.LOG.error("Civ ruleset has {} error(s); keeping the previous ruleset ({} techs)", errors.size(), current.techs().size());
            return;
        }
        current = r;
        CivCoreMod.LOG.info("Civ ruleset loaded: {} techs, {} buildings, {} units (hash {})",
            r.techs().size(), r.buildings().size(), r.units().size(), r.hash());
    }
}
