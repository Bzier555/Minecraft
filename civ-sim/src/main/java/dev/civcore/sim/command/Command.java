package dev.civcore.sim.command;

/**
 * Everything that changes the game goes through a Command, from players and AI alike (docs/D-architecture.md §1.4).
 * {@code issuer} is a player UUID string, or "ai:&lt;civId&gt;" for AI controllers.
 */
public sealed interface Command permits Command.FoundCity, Command.SetResearch, Command.QueueProduction,
    Command.MoveArmy, Command.DeclareWar, Command.ProposePeace, Command.SettleWithArmy {

    String issuer();

    /**
     * Found a city at a block position. If the issuer has no civilization yet, one is created with {@code civName}.
     */
    record FoundCity(String issuer, String civName, String cityName, int blockX, int blockY, int blockZ) implements Command {
    }

    record SetResearch(String issuer, String techId) implements Command {
    }

    /** Append a building or unit id to a city's production queue. */
    record QueueProduction(String issuer, long cityId, String itemId) implements Command {
    }

    /** Order an army to a tile (it paths there over the following turns; entering an enemy tile attacks it). */
    record MoveArmy(String issuer, long armyId, int tileX, int tileZ) implements Command {
    }

    record DeclareWar(String issuer, int targetCivId) implements Command {
    }

    /** Offer peace; if the other side has already offered, the war ends. */
    record ProposePeace(String issuer, int targetCivId) implements Command {
    }

    /** Use a settlers unit to found a city on the army's tile. */
    record SettleWithArmy(String issuer, long armyId, String cityName) implements Command {
    }
}
