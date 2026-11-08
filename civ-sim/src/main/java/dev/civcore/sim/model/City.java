package dev.civcore.sim.model;

import dev.civcore.sim.topology.TilePos;

import java.util.ArrayList;
import java.util.List;

/** A city: strategic record (authoritative). Physical representations are projections (docs/D-architecture.md §7). */
public final class City {
    public long id;
    public int civId;
    /** Civ that founded it; used by domination victory for original capitals. */
    public int originalCivId;
    public String name;
    public int tileX;
    public int tileZ;
    /** Exact block of the City Center block. */
    public int blockX;
    public int blockY;
    public int blockZ;
    public int population = 1;
    public static final int MAX_HP = 100;
    /** City defence hit points; 0 means the city can be captured. */
    public int hp = MAX_HP;
    /** Founded as its civ's first city: counts for domination victory. */
    public boolean originalCapital;
    public int foodStored;
    public int productionStored;
    public List<String> buildings = new ArrayList<>();
    public List<String> productionQueue = new ArrayList<>();
    public long foundedTurn;

    public City() {
    }

    public TilePos tile() {
        return new TilePos(tileX, tileZ);
    }
}
