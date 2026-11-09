package dev.civcore.sim.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A nation. Plain mutable state; only the server thread (via {@code Simulation}) mutates it.
 * Fields are public to keep the persisted shape obvious; rules live in {@code dev.civcore.sim.rules}/{@code engine}.
 */
public final class Civilization {
    public int id;
    public String name;
    /** 0xRRGGBB */
    public int color;
    /** "human" or "ai" */
    public String controller = "human";
    /** Player UUIDs (as strings) who belong to this civ. */
    public List<String> members = new ArrayList<>();
    public long capitalCityId = -1;
    public List<Long> cityIds = new ArrayList<>();
    public Set<String> knownTechs = new LinkedHashSet<>();
    public String currentResearch;
    public int researchProgress;
    public int gold;
    /** Culture earned over the whole game (for a future culture victory). */
    public int culture;
    /** National happiness last strategic tick (see Resources.happiness). */
    public int happiness;
    /** No cities and no armies left. */
    public boolean eliminated;

    public Civilization() {
    }

    public Civilization(int id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
}
