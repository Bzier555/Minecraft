package dev.civcore.sim.rules;

import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;

/**
 * Governments and policies (backlog F5.3, docs/F-game-design.md §7), and the combined modifiers they give a civ.
 * Kept in code (not JSON) while the set is small; the effects are applied by {@link #modifiers}.
 */
public final class Civics {
    public static final int ANARCHY_TURNS = 2;

    public enum Government {
        DESPOTISM(null, 1, 0, 0, 0, 0, 0, 0),
        MONARCHY("civcore:currency", 2, 2, 0, 0, 10, 0, 0),
        REPUBLIC("civcore:philosophy", 3, 0, 15, 10, 0, 0, 0),
        THEOCRACY("civcore:theology", 2, 2, 0, 0, 0, 25, 0),
        MERCHANT_REPUBLIC("civcore:banking", 3, 0, 5, 25, 0, 0, 0),
        CONSTITUTIONAL_MONARCHY("civcore:economics", 4, 3, 10, 10, 10, 0, 0);

        public final String requiresTech;
        public final int policySlots, happiness, sciencePct, goldPct, productionPct, culturePct, strengthPct;

        Government(String requiresTech, int policySlots, int happiness, int sciencePct, int goldPct, int productionPct, int culturePct, int strengthPct) {
            this.requiresTech = requiresTech;
            this.policySlots = policySlots;
            this.happiness = happiness;
            this.sciencePct = sciencePct;
            this.goldPct = goldPct;
            this.productionPct = productionPct;
            this.culturePct = culturePct;
            this.strengthPct = strengthPct;
        }
    }

    public enum Policy {
        AGRARIAN_REFORM("+1 food in every city"),
        PROFESSIONAL_ARMY("unit upkeep halved"),
        SCHOLASTICISM("+10% science"),
        TRADE_GUILDS("+15% gold"),
        CIVIC_PRIDE("+3 happiness"),
        MILITARY_TRADITION("+10% unit strength"),
        MONUMENTAL_ARCHITECTURE("+20% culture"),
        WORK_ETHIC("+10% production");

        public final String description;

        Policy(String description) {
            this.description = description;
        }
    }

    /** Everything governments and policies change, as one bundle. */
    public record Modifiers(int foodPerCity, int sciencePct, int goldPct, int productionPct, int culturePct, int happiness,
                            int upkeepPct, int strengthPct, boolean anarchy) {
    }

    private Civics() {
    }

    public static Government government(Civilization c) {
        try {
            return c.government == null ? Government.DESPOTISM : Government.valueOf(c.government);
        } catch (IllegalArgumentException e) {
            return Government.DESPOTISM;
        }
    }

    public static boolean available(Civilization c, Government g) {
        return g.requiresTech == null || c.knownTechs.contains(g.requiresTech);
    }

    /** Culture needed for the next policy. */
    public static int policyCost(Civilization c) {
        return 60 + 60 * c.policies.size();
    }

    public static int freeCulture(Civilization c) {
        return c.culture - c.cultureSpent;
    }

    public static Modifiers modifiers(GameState s, Civilization c) {
        Government g = government(c);
        boolean anarchy = c.anarchyUntilTurn > s.turn;
        int food = 0, sci = g.sciencePct, gold = g.goldPct, prod = g.productionPct, cult = g.culturePct, happy = g.happiness, upkeep = 100, str = g.strengthPct;
        for (String id : c.policies) {
            switch (Policy.valueOf(id)) {
                case AGRARIAN_REFORM -> food += 1;
                case PROFESSIONAL_ARMY -> upkeep = 50;
                case SCHOLASTICISM -> sci += 10;
                case TRADE_GUILDS -> gold += 15;
                case CIVIC_PRIDE -> happy += 3;
                case MILITARY_TRADITION -> str += 10;
                case MONUMENTAL_ARCHITECTURE -> cult += 20;
                case WORK_ETHIC -> prod += 10;
            }
        }
        if (anarchy) {
            sci = -100;
            gold = -100;
        }
        return new Modifiers(food, sci, gold, prod, cult, happy, upkeep, str, anarchy);
    }

    public static int applyPct(int value, int pct) {
        return Math.max(0, value * (100 + pct) / 100);
    }
}
