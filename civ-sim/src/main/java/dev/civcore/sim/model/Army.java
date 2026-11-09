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
        /** Stable id (links a unit to its soldier entity while the army is physical). 0 = not yet assigned. */
        public long uid;
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
    /** Bumped whenever the army's marker must be redrawn (moves, merges). */
    public int generation;
    /**
     * True while the army exists as soldier entities near a player (set by the mod's projection). Physical-vs-physical
     * battles are fought in the world, not by the abstract combat rules (docs/D-architecture.md §7).
     */
    public boolean physical;
    /** Bumped when the rules change units' health abstractly, so physical soldiers are rebuilt to match. */
    public int revision;

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
