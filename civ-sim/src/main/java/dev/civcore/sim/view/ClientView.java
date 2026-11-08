package dev.civcore.sim.view;

import java.util.ArrayList;
import java.util.List;

/**
 * What one player's client is allowed to know, built on the server by {@link ViewBuilder} and sent as JSON.
 * The client renders only this; it never sees the full game state (secure strategic fog of war, D §9).
 */
public final class ClientView {
    public static final int UNEXPLORED = -2;

    public long turn;
    public long secondsToNextTurn;
    public boolean paused;
    public String topology;
    public int width, height, tileSize, minBlockX, minBlockZ;
    public boolean analysed;
    /** Viewer's civ, or -1. */
    public int myCivId = -1;
    /** TerrainType ordinal, -1 not analysed yet, UNEXPLORED (-2) never seen. */
    public int[] terrain;
    /** Owning civ id, -1 none or unexplored. */
    public int[] owner;
    /** Currently in sight (enemy armies are shown only here). */
    public boolean[] visible;
    public List<CivInfo> civs = new ArrayList<>();
    public List<CityInfo> cities = new ArrayList<>();
    public List<ArmyInfo> armies = new ArrayList<>();
    public List<TechInfo> techs = new ArrayList<>();
    public String currentResearch;
    public int researchProgress;
    public int gold;
    public int winnerCivId = -1;
    public String victoryType = "";

    public static final class CivInfo {
        public int id;
        public String name;
        public int color;
        public boolean ai, atWar, eliminated, theyOfferedPeace, weOfferedPeace;
        public long warCooldown;
        public int score;
    }

    public static final class CityInfo {
        public long id;
        public int civId;
        public String name;
        public int x, z;
        public int population, hp;
        public boolean capital;
        // own cities only:
        public boolean mine;
        public int food, foodNeeded, foodYield, foodEaten, production, productionYield, goldYield;
        public List<String> queue = new ArrayList<>();
        public List<String> buildings = new ArrayList<>();
        public List<Item> available = new ArrayList<>();
    }

    public static final class Item {
        public String id;
        public String kind; // "building" | "unit"
        public int cost;
        public int strength;
    }

    public static final class ArmyInfo {
        public long id;
        public int civId;
        public int x, z;
        public int units;
        public int hpPercent;
        public String summary;
        public boolean settlers, mine;
        public int moves;
        public int targetX = -1, targetZ = -1;
    }

    public static final class TechInfo {
        public String id;
        public String era;
        public int cost;
        public List<String> requires = new ArrayList<>();
        public List<String> unlocks = new ArrayList<>();
        /** 0 known, 1 available, 2 locked. */
        public int status;
    }

    public int index(int x, int z) {
        return z * width + x;
    }
}
