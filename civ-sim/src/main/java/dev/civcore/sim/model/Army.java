package dev.civcore.sim.model;

import dev.civcore.sim.topology.TilePos;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract army: a stack of units on one tile. Authoritative state; any entities in the world are projections
 * (docs/D-architecture.md §7).
 */
public final class Army {
    public static final int FULL_HP = 100;

    public static final class Unit {
        public String type;
        public int hp = FULL_HP;

        public Unit() {
        }

        public Unit(String type) {
            this.type = type;
        }
    }

    public long id;
    public int civId;
    public List<Unit> units = new ArrayList<>();
    public int tileX;
    public int tileZ;
    /** Remaining path (tile coordinates as [x,z] pairs), first element is the next step. Empty = holding. */
    public List<int[]> path = new ArrayList<>();
    /** Bumped whenever the army's physical projection must be rebuilt (generation rule, D §7.2). */
    public int generation;

    public Army() {
    }

    public TilePos tile() {
        return new TilePos(tileX, tileZ);
    }

    public void moveTo(TilePos p) {
        tileX = p.x();
        tileZ = p.z();
        generation++;
    }

    public boolean hasUnit(String type) {
        return units.stream().anyMatch(u -> u.type.equals(type));
    }
}
