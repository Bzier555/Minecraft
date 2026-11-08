package dev.civcore.sim.command;

public record CommandResult(boolean ok, String message) {
    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult fail(String message) {
        return new CommandResult(false, message);
    }
}
