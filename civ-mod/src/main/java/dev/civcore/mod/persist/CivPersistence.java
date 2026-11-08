package dev.civcore.mod.persist;

import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.GameState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Saves the strategic game to {@code <world>/civcore/game.json}: written to a temp file, then atomically moved into
 * place, keeping the last 3 saves as game.json.1..3 (docs/D-architecture.md §9).
 */
public final class CivPersistence {
    public static final String FILE = "game.json";
    public static final int BACKUPS = 3;

    private final Path dir;

    public CivPersistence(Path dir) {
        this.dir = dir;
    }

    public Path file() {
        return dir.resolve(FILE);
    }

    public Optional<GameState> load() throws IOException {
        Path f = file();
        if (!Files.exists(f)) return Optional.empty();
        return Optional.of(GameStateCodec.fromJson(Files.readString(f, StandardCharsets.UTF_8)));
    }

    public void save(GameState state) throws IOException {
        Files.createDirectories(dir);
        String json = GameStateCodec.toJson(state);
        Path tmp = dir.resolve(FILE + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        rotateBackups();
        try {
            Files.move(tmp, file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rotateBackups() throws IOException {
        if (!Files.exists(file())) return;
        Files.deleteIfExists(dir.resolve(FILE + "." + BACKUPS));
        for (int i = BACKUPS - 1; i >= 1; i--) {
            Path from = dir.resolve(FILE + "." + i);
            if (Files.exists(from)) Files.move(from, dir.resolve(FILE + "." + (i + 1)), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(file(), dir.resolve(FILE + ".1"), StandardCopyOption.REPLACE_EXISTING);
    }
}
