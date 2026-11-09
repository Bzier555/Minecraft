package dev.civcore.sim.view;

import com.google.gson.Gson;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.rules.Military;
import dev.civcore.sim.rules.TileYields;
import dev.civcore.sim.rules.Victory;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Builds the {@link ClientView} for one viewer, applying fog of war. */
public final class ViewBuilder {
    private static final Gson GSON = new Gson();

    private ViewBuilder() {
    }

    /**
     * @param viewerCivId civ of the viewing player, or -1 (spectator without a civ: sees only the explored nothing)
     * @param playerTiles tiles where the viewer's civ members stand (extra vision)
     * @param seeAll      operators/spectators of a finished game may see everything
     */
    public static ClientView build(GameState s, Ruleset r, int viewerCivId, List<TilePos> playerTiles, boolean seeAll,
                                   long secondsToNextTurn, boolean paused) {
        ClientView v = new ClientView();
        v.turn = s.turn;
        v.secondsToNextTurn = secondsToNextTurn;
        v.paused = paused;
        v.topology = s.settings.topology();
        v.width = s.map.width;
        v.height = s.map.height;
        v.tileSize = s.settings.tileSize();
        v.minBlockX = s.settings.minBlockX();
        v.minBlockZ = s.settings.minBlockZ();
        v.analysed = s.map.analysed;
        v.myCivId = viewerCivId;
        v.winnerCivId = s.winnerCivId;
        v.victoryType = s.victoryType;

        int n = s.map.width * s.map.height;
        boolean[] explored = seeAll ? null : s.explored.get(viewerCivId);
        Set<TilePos> visible = viewerCivId >= 0 ? Visibility.visibleNow(s, viewerCivId, playerTiles) : Set.of();
        v.terrain = new int[n];
        v.owner = new int[n];
        v.resource = new int[n];
        v.visible = new boolean[n];
        for (int i = 0; i < n; i++) {
            TilePos p = new TilePos(i % s.map.width, i / s.map.width);
            boolean seen = seeAll || (explored != null && explored[i]) || visible.contains(p);
            v.visible[i] = seeAll || visible.contains(p);
            v.terrain[i] = seen ? s.map.terrain[i] : ClientView.UNEXPLORED;
            v.owner[i] = seen ? s.map.owner[i] : -1;
            int res = s.map.resource == null ? -1 : s.map.resource[i];
            boolean known = res >= 0 && (seeAll || me0(s, viewerCivId) == null
                || dev.civcore.sim.rules.Resources.knows(me0(s, viewerCivId), dev.civcore.sim.worldplan.ResourceType.values()[res]));
            v.resource[i] = seen && known ? res : -1;
        }

        Civilization me = s.civs.get(viewerCivId);
        for (Civilization c : s.civs.values()) {
            boolean met = seeAll || c.id == viewerCivId || s.cities.values().stream()
                .anyMatch(x -> x.civId == c.id && v.terrain[v.index(x.tileX, x.tileZ)] != ClientView.UNEXPLORED);
            if (!met) continue; // unmet civs stay unknown
            ClientView.CivInfo ci = new ClientView.CivInfo();
            ci.id = c.id;
            ci.name = c.name;
            ci.color = c.color;
            ci.ai = "ai".equals(c.controller);
            ci.eliminated = c.eliminated;
            ci.score = Victory.score(s, c);
            if (me != null && c.id != me.id) {
                ci.atWar = s.atWar(me.id, c.id);
                ci.theyOfferedPeace = s.peaceOffers.contains(c.id + ">" + me.id);
                ci.weOfferedPeace = s.peaceOffers.contains(me.id + ">" + c.id);
                ci.warCooldown = s.warCooldown(me.id, c.id);
            }
            v.civs.add(ci);
        }

        for (City c : s.cities.values()) {
            boolean mine = c.civId == viewerCivId;
            if (!mine && v.terrain[v.index(c.tileX, c.tileZ)] == ClientView.UNEXPLORED) continue;
            ClientView.CityInfo ci = new ClientView.CityInfo();
            ci.id = c.id;
            ci.civId = c.civId;
            ci.name = c.name;
            ci.x = c.tileX;
            ci.z = c.tileZ;
            ci.population = c.population;
            ci.hp = c.hp;
            Civilization owner = s.civs.get(c.civId);
            ci.capital = owner != null && owner.capitalCityId == c.id;
            if (mine) fillOwnCity(s, r, me, c, ci);
            v.cities.add(ci);
        }

        for (Army a : s.armies.values()) {
            boolean mine = a.civId == viewerCivId;
            if (!mine && !v.visible[v.index(a.tileX, a.tileZ)]) continue;
            ClientView.ArmyInfo ai = new ClientView.ArmyInfo();
            ai.id = a.id;
            ai.civId = a.civId;
            ai.x = a.tileX;
            ai.z = a.tileZ;
            ai.units = a.units.size();
            int hp = 0;
            Map<String, Integer> counts = new TreeMap<>();
            for (Army.Unit u : a.units) {
                hp += u.hp;
                counts.merge(u.type.replace("civcore:", ""), 1, Integer::sum);
            }
            ai.hpPercent = a.units.isEmpty() ? 0 : hp / a.units.size();
            StringBuilder sb = new StringBuilder();
            counts.forEach((k, cnt) -> sb.append(sb.isEmpty() ? "" : ", ").append(cnt).append(' ').append(k));
            ai.summary = sb.toString();
            ai.settlers = a.hasUnit(Military.SETTLERS);
            ai.mine = mine;
            if (mine) {
                ai.moves = Military.moves(a, r);
                if (!a.path.isEmpty()) {
                    ai.targetX = a.path.get(a.path.size() - 1)[0];
                    ai.targetZ = a.path.get(a.path.size() - 1)[1];
                }
            }
            v.armies.add(ai);
        }

        if (me != null) {
            v.currentResearch = me.currentResearch;
            v.researchProgress = me.researchProgress;
            v.gold = me.gold;
            v.culture = me.culture;
            v.happiness = dev.civcore.sim.rules.Resources.happiness(s, r, me);
            var owned = dev.civcore.sim.rules.Resources.owned(s, me);
            for (var t : dev.civcore.sim.worldplan.ResourceType.values()) {
                if (t.kind == dev.civcore.sim.worldplan.ResourceType.Kind.STRATEGIC && dev.civcore.sim.rules.Resources.knows(me, t)) {
                    v.strategic.put(t.name().toLowerCase(), new int[]{dev.civcore.sim.rules.Resources.available(s, r, me, t), owned.getOrDefault(t, 0)});
                }
                if (t.kind == dev.civcore.sim.worldplan.ResourceType.Kind.LUXURY && owned.getOrDefault(t, 0) > 0) v.luxuries.add(t.name().toLowerCase());
            }
            for (Ruleset.TechDef t : r.techs().values()) {
                ClientView.TechInfo ti = new ClientView.TechInfo();
                ti.id = t.id();
                ti.era = t.era();
                ti.cost = t.cost();
                ti.requires.addAll(t.requires());
                ti.unlocks.addAll(t.unlocks().buildings());
                ti.unlocks.addAll(t.unlocks().units());
                ti.status = me.knownTechs.contains(t.id()) ? 0 : me.knownTechs.containsAll(t.requires()) ? 1 : 2;
                v.techs.add(ti);
            }
            v.techs.sort((a, b) -> a.cost != b.cost ? Integer.compare(a.cost, b.cost) : a.id.compareTo(b.id));
        }
        return v;
    }

    private static Civilization me0(GameState s, int civId) {
        return s.civs.get(civId);
    }

    private static void fillOwnCity(GameState s, Ruleset r, Civilization me, City c, ClientView.CityInfo ci) {
        ci.mine = true;
        TileYields.Yield y = TileYields.city(s, c);
        Map<String, Integer> b = buildingYields(c, r);
        ci.food = c.foodStored;
        ci.foodNeeded = Simulation.growthThreshold(c.population);
        ci.foodYield = y.food() + b.getOrDefault("food", 0);
        ci.foodEaten = Simulation.FOOD_PER_CITIZEN * c.population;
        ci.production = c.productionStored;
        ci.productionYield = y.production() + b.getOrDefault("production", 0);
        ci.goldYield = y.gold() + b.getOrDefault("gold", 0);
        ci.culture = c.cultureStored;
        ci.cultureNeeded = dev.civcore.sim.rules.Culture.threshold(c.cultureTiles);
        ci.cultureYield = dev.civcore.sim.rules.Culture.cityCulture(c, r);
        ci.queue.addAll(c.productionQueue);
        ci.buildings.addAll(c.buildings);
        for (Ruleset.BuildingDef bd : r.buildings().values()) {
            if (bd.id().equals("civcore:palace") || c.buildings.contains(bd.id()) || c.productionQueue.contains(bd.id())) continue;
            if (bd.requiresTech() != null && !me.knownTechs.contains(bd.requiresTech())) continue;
            ClientView.Item it = new ClientView.Item();
            it.id = bd.id();
            it.kind = "building";
            it.cost = bd.cost();
            ci.available.add(it);
        }
        for (Ruleset.UnitDef ud : r.units().values()) {
            if (ud.requiresTech() != null && !me.knownTechs.contains(ud.requiresTech())) continue;
            ClientView.Item it = new ClientView.Item();
            it.id = ud.id();
            it.kind = "unit";
            it.cost = ud.cost();
            it.strength = ud.strength();
            ci.available.add(it);
        }
        ci.available.sort((a, x) -> a.kind.equals(x.kind) ? Integer.compare(a.cost, x.cost) : a.kind.compareTo(x.kind));
    }

    private static Map<String, Integer> buildingYields(City c, Ruleset r) {
        Map<String, Integer> total = new java.util.HashMap<>();
        for (String id : c.buildings) {
            Optional.ofNullable(r.buildings().get(id)).ifPresent(d -> d.yields().forEach((k, val) -> total.merge(k, val, Integer::sum)));
        }
        return total;
    }

    public static String toJson(ClientView v) {
        return GSON.toJson(v);
    }

    public static ClientView fromJson(String json) {
        return GSON.fromJson(json, ClientView.class);
    }
}
