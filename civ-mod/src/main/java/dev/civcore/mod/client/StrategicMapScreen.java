package dev.civcore.mod.client;

import dev.civcore.mod.net.CivNetwork;
import dev.civcore.sim.view.ClientView;
import dev.civcore.sim.worldplan.TerrainType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The strategic map (backlog F1.14/F5.1, first version): terrain, borders, cities and armies the player's civ knows
 * about; pan by dragging, zoom with the wheel. Left-click selects; right-click orders the selected army there.
 * Wraps endlessly on cylinder and torus maps.
 */
public class StrategicMapScreen extends Screen {
    private static final int PANEL = 170;
    private static final int[] TERRAIN_COLOR = {
        0xFF1E3F7A, // OCEAN
        0xFF3B6FB0, // COAST
        0xFF6DAA45, // GRASSLAND
        0xFFB5B35C, // PLAINS
        0xFFE0D08A, // DESERT
        0xFF8E9A86, // TUNDRA
        0xFFEDEFF2, // SNOW
        0xFF2F6B2E, // FOREST
        0xFF1F5A20, // JUNGLE
        0xFF5E7A55, // MARSH
        0xFF8C7A55, // HILLS
        0xFF6E6A66, // MOUNTAIN
    };

    private double camX = -1, camZ = -1; // tile at screen centre
    private double zoom = 0;              // pixels per tile
    private boolean dragging;
    private double dragDist;
    private int selX = -1, selZ = -1;
    private long selArmy = -1;
    private int ticks;
    private final List<Button> panelButtons = new ArrayList<>();

    public StrategicMapScreen() {
        super(Component.literal("Strategic Map"));
    }

    @Override
    protected void init() {
        rebuildPanel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) ClientCivState.requestView(); // refresh every 2 s while open
        if (ticks % 10 == 0) rebuildPanel();
    }

    private int mapWidth() {
        return width - PANEL;
    }

    private void ensureCamera(ClientView v) {
        if (zoom <= 0) zoom = Math.max(4, Math.min(mapWidth() / (double) v.width, height / (double) v.height));
        if (camX < 0) {
            ClientView.CityInfo home = v.cities.stream().filter(c -> c.mine && c.capital).findFirst()
                .orElse(v.cities.stream().filter(c -> c.mine).findFirst().orElse(null));
            camX = home != null ? home.x + 0.5 : v.width / 2.0;
            camZ = home != null ? home.z + 0.5 : v.height / 2.0;
        }
    }

    // ------------------------------------------------------------------ coordinates (wrap-aware)

    private boolean wrapX(ClientView v) {
        return !"bounded".equals(v.topology);
    }

    private boolean wrapZ(ClientView v) {
        return "torus".equals(v.topology);
    }

    /** Screen x of tile column x (the copy nearest the camera). */
    private double screenX(ClientView v, double tileX) {
        double d = tileX - camX;
        if (wrapX(v)) d = d - Math.floor(d / v.width + 0.5) * v.width;
        return mapWidth() / 2.0 + d * zoom;
    }

    private double screenZ(ClientView v, double tileZ) {
        double d = tileZ - camZ;
        if (wrapZ(v)) d = d - Math.floor(d / v.height + 0.5) * v.height;
        return height / 2.0 + d * zoom;
    }

    private int[] tileAt(ClientView v, double mx, double my) {
        int tx = (int) Math.floor(camX + (mx - mapWidth() / 2.0) / zoom);
        int tz = (int) Math.floor(camZ + (my - height / 2.0) / zoom);
        if (wrapX(v)) tx = Math.floorMod(tx, v.width);
        if (wrapZ(v)) tz = Math.floorMod(tz, v.height);
        if (tx < 0 || tz < 0 || tx >= v.width || tz >= v.height) return null;
        return new int[]{tx, tz};
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0B0B10);
        ClientView v = ClientCivState.view();
        if (v == null || v.terrain == null) {
            g.drawCenteredString(font, "Waiting for the server...", width / 2, height / 2, 0xFFFFFF);
            return;
        }
        ensureCamera(v);
        g.enableScissor(0, 0, mapWidth(), height);
        int span = (int) Math.ceil(Math.max(mapWidth(), height) / zoom) + 2;
        for (int dz = -span / 2 - 1; dz <= span / 2 + 1; dz++) {
            for (int dx = -span / 2 - 1; dx <= span / 2 + 1; dx++) {
                int rx = (int) Math.floor(camX) + dx, rz = (int) Math.floor(camZ) + dz;
                int tx = wrapX(v) ? Math.floorMod(rx, v.width) : rx;
                int tz = wrapZ(v) ? Math.floorMod(rz, v.height) : rz;
                if (tx < 0 || tz < 0 || tx >= v.width || tz >= v.height) continue;
                int x0 = (int) Math.floor(mapWidth() / 2.0 + (rx - camX) * zoom);
                int z0 = (int) Math.floor(height / 2.0 + (rz - camZ) * zoom);
                int x1 = (int) Math.floor(mapWidth() / 2.0 + (rx + 1 - camX) * zoom);
                int z1 = (int) Math.floor(height / 2.0 + (rz + 1 - camZ) * zoom);
                drawTile(g, v, tx, tz, x0, z0, x1, z1);
            }
        }
        // cities and armies
        for (ClientView.CityInfo c : v.cities) drawCity(g, v, c);
        int[] stackIndex = new int[v.width * v.height];
        for (ClientView.ArmyInfo a : v.armies) drawArmy(g, v, a, stackIndex[v.index(a.x, a.z)]++);
        // selection
        if (selX >= 0) {
            int sx = (int) screenX(v, selX), sz = (int) screenZ(v, selZ);
            g.renderOutline(sx, sz, (int) Math.ceil(zoom), (int) Math.ceil(zoom), 0xFFFFFF00);
        }
        g.disableScissor();
        g.fill(mapWidth(), 0, width, height, 0xE0181820);
    }

    private void drawTile(GuiGraphics g, ClientView v, int tx, int tz, int x0, int z0, int x1, int z1) {
        int i = v.index(tx, tz);
        int t = v.terrain[i];
        int color = t == ClientView.UNEXPLORED ? 0xFF000000 : t < 0 ? 0xFF404040 : TERRAIN_COLOR[t];
        if (t != ClientView.UNEXPLORED && !v.visible[i]) color = darken(color);
        g.fill(x0, z0, x1, z1, color);
        int res = v.resource == null ? -1 : v.resource[i];
        if (res >= 0 && zoom >= 10) {
            var rt = dev.civcore.sim.worldplan.ResourceType.values()[res];
            int rc = switch (rt.kind) {
                case BONUS -> 0xFFE8E8C0;
                case STRATEGIC -> 0xFFFF6040;
                case LUXURY -> 0xFFE070FF;
            };
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, rr = Math.max(2, (int) (zoom / 7));
            g.fill(cx - rr - 1, cz - rr - 1, cx + rr + 1, cz + rr + 1, 0xFF000000);
            g.fill(cx - rr, cz - rr, cx + rr, cz + rr, rc);
            if (zoom >= 22) g.drawString(font, rt.name().substring(0, 1), cx - 2, cz - 4, 0xFF000000, false);
        }
        int owner = v.owner[i];
        if (owner >= 0) {
            ClientView.CivInfo civ = ClientCivState.civ(owner);
            int cc = civ == null ? 0xFFFFFF : civ.color;
            g.fill(x0, z0, x1, z1, 0x40000000 | cc);
            int w = Math.max(1, (int) (zoom / 8));
            // border edges where the neighbour has a different owner
            if (ownerAt(v, tx - 1, tz) != owner) g.fill(x0, z0, x0 + w, z1, 0xFF000000 | cc);
            if (ownerAt(v, tx + 1, tz) != owner) g.fill(x1 - w, z0, x1, z1, 0xFF000000 | cc);
            if (ownerAt(v, tx, tz - 1) != owner) g.fill(x0, z0, x1, z0 + w, 0xFF000000 | cc);
            if (ownerAt(v, tx, tz + 1) != owner) g.fill(x0, z1 - w, x1, z1, 0xFF000000 | cc);
        }
    }

    private int ownerAt(ClientView v, int x, int z) {
        if (wrapX(v)) x = Math.floorMod(x, v.width);
        if (wrapZ(v)) z = Math.floorMod(z, v.height);
        if (x < 0 || z < 0 || x >= v.width || z >= v.height) return -1;
        return v.owner[v.index(x, z)];
    }

    private static int darken(int argb) {
        int r = ((argb >> 16) & 0xFF) * 6 / 10, gg = ((argb >> 8) & 0xFF) * 6 / 10, b = (argb & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void drawCity(GuiGraphics g, ClientView v, ClientView.CityInfo c) {
        ClientView.CivInfo civ = ClientCivState.civ(c.civId);
        int cc = 0xFF000000 | (civ == null ? 0xFFFFFF : civ.color);
        int sx = (int) screenX(v, c.x + 0.5), sz = (int) screenZ(v, c.z + 0.5);
        int r = Math.max(3, (int) (zoom * 0.3));
        g.fill(sx - r - 1, sz - r - 1, sx + r + 1, sz + r + 1, 0xFFFFFFFF);
        g.fill(sx - r, sz - r, sx + r, sz + r, cc);
        if (c.capital) g.fill(sx - 1, sz - 1, sx + 1, sz + 1, 0xFFFFD700);
        if (zoom >= 8) {
            String label = c.name + " " + c.population;
            g.drawString(font, label, sx - font.width(label) / 2, sz + r + 2, 0xFFFFFF, true);
        }
    }

    private void drawArmy(GuiGraphics g, ClientView v, ClientView.ArmyInfo a, int stack) {
        ClientView.CivInfo civ = ClientCivState.civ(a.civId);
        int cc = 0xFF000000 | (civ == null ? 0xFFFFFF : civ.color);
        double base = zoom * 0.18;
        int sx = (int) (screenX(v, a.x) + zoom * 0.72 - stack * base);
        int sz = (int) (screenZ(v, a.z) + zoom * 0.2);
        int r = Math.max(2, (int) base);
        g.fill(sx - r - 1, sz - r - 1, sx + r + 1, sz + r + 1, a.id == selArmy ? 0xFFFFFF00 : 0xFF000000);
        g.fill(sx - r, sz - r, sx + r, sz + r, a.settlers ? 0xFFFFFFFF : cc);
        if (zoom >= 12) g.drawString(font, String.valueOf(a.units), sx + r + 1, sz - 4, 0xFFFFFF, true);
        if (a.mine && a.targetX >= 0 && a.id == selArmy) {
            int tx = (int) screenX(v, a.targetX + 0.5), tz = (int) screenZ(v, a.targetZ + 0.5);
            g.renderOutline(tx - 3, tz - 3, 6, 6, 0xFFFFFF00);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ClientView v = ClientCivState.view();
        if (v == null) return;
        int x = mapWidth() + 6, y = 6;
        g.drawString(font, "Turn " + v.turn + (v.paused ? " (paused)" : " | next " + v.secondsToNextTurn + "s"), x, y, 0xFFFFFF);
        y += 11;
        g.drawString(font, "Gold " + v.gold + " | " + (v.currentResearch == null ? "No research" : ClientCivState.shortId(v.currentResearch)
            + " " + v.researchProgress + "/" + techCost(v, v.currentResearch)), x, y, 0xC0C0C0);
        y += 11;
        StringBuilder res = new StringBuilder("Happy " + (v.happiness >= 0 ? "+" : "") + v.happiness);
        if (v.strategic != null) v.strategic.forEach((k, a) -> res.append(" | ").append(ClientCivState.shortId(k)).append(' ').append(a[0]).append('/').append(a[1]));
        g.drawString(font, res.toString(), x, y, v.happiness < 0 ? 0xFF8080 : 0x80FF80);
        y += 11;
        if (v.luxuries != null && !v.luxuries.isEmpty()) {
            g.drawString(font, "Luxuries: " + String.join(", ", v.luxuries), x, y, 0xE070FF);
            y += 11;
        }
        if (v.winnerCivId >= 0) {
            ClientView.CivInfo w = ClientCivState.civ(v.winnerCivId);
            g.drawString(font, (w == null ? "?" : w.name) + " won (" + v.victoryType + ")", x, y, 0xFFD700);
            y += 11;
        }
        if (!v.analysed) {
            g.drawString(font, "Analysing the world...", x, y, 0xFF8080);
            y += 11;
        }
        y += 4;
        for (String line : selectionLines(v)) {
            g.drawString(font, line, x, y, 0xFFFFFF);
            y += 10;
        }
        // hover tooltip
        if (mouseX < mapWidth()) {
            int[] t = tileAt(v, mouseX, mouseY);
            if (t != null) {
                int ter = v.terrain[v.index(t[0], t[1])];
                String name = ter == ClientView.UNEXPLORED ? "Unexplored" : ter < 0 ? "Unknown" : ClientCivState.shortId(TerrainType.values()[ter].name().toLowerCase());
                int rr = v.resource == null ? -1 : v.resource[v.index(t[0], t[1])];
                if (rr >= 0) name += " + " + ClientCivState.shortId(dev.civcore.sim.worldplan.ResourceType.values()[rr].name().toLowerCase());
                g.drawString(font, "(" + t[0] + "," + t[1] + ") " + name, 4, height - 12, 0xFFFFFF, true);
            }
        }
        g.drawString(font, "Drag: pan  Wheel: zoom  Left: select  Right: move army", 4, 4, 0xA0A0A0, true);
    }

    private static int techCost(ClientView v, String id) {
        for (ClientView.TechInfo t : v.techs) if (t.id.equals(id)) return t.cost;
        return 0;
    }

    private List<String> selectionLines(ClientView v) {
        List<String> lines = new ArrayList<>();
        ClientView.ArmyInfo army = selectedArmy(v);
        if (army != null) {
            ClientView.CivInfo civ = ClientCivState.civ(army.civId);
            lines.add("Army #" + army.id + " (" + (civ == null ? "?" : civ.name) + ")");
            lines.add(army.summary);
            lines.add("Health " + army.hpPercent + "%" + (army.mine ? ", " + army.moves + " tiles/turn" : ""));
            if (army.mine) lines.add(army.targetX >= 0 ? "Marching to (" + army.targetX + "," + army.targetZ + ")" : "Right-click a tile to move");
            return lines;
        }
        if (selX < 0) {
            lines.add("Click a tile, city or army.");
            return lines;
        }
        lines.add("Tile (" + selX + "," + selZ + ")");
        int ter = v.terrain[v.index(selX, selZ)];
        lines.add(ter == ClientView.UNEXPLORED ? "Unexplored" : ter < 0 ? "Not analysed" : ClientCivState.shortId(TerrainType.values()[ter].name().toLowerCase()));
        int owner = v.owner[v.index(selX, selZ)];
        ClientView.CivInfo oc = ClientCivState.civ(owner);
        if (oc != null) lines.add("Owner: " + oc.name);
        ClientView.CityInfo city = cityAt(v, selX, selZ);
        if (city != null) {
            lines.add("City: " + city.name + " (pop " + city.population + ", hp " + city.hp + ")");
        }
        return lines;
    }

    private ClientView.CityInfo cityAt(ClientView v, int x, int z) {
        for (ClientView.CityInfo c : v.cities) if (c.x == x && c.z == z) return c;
        return null;
    }

    private ClientView.ArmyInfo selectedArmy(ClientView v) {
        for (ClientView.ArmyInfo a : v.armies) if (a.id == selArmy) return a;
        return null;
    }

    // ------------------------------------------------------------------ side panel buttons

    private void rebuildPanel() {
        panelButtons.forEach(this::removeWidget);
        panelButtons.clear();
        ClientView v = ClientCivState.view();
        int x = mapWidth() + 6, w = PANEL - 12;
        int y = height - 24;
        add(Button.builder(Component.literal("Technologies"), b -> minecraft.setScreen(new TechScreen(this))).bounds(x, y, w, 20).build());
        y -= 24;
        if (v == null) return;
        ClientView.ArmyInfo army = selectedArmy(v);
        if (army != null && army.mine && army.settlers) {
            add(Button.builder(Component.literal("Found city here"), b -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "settle";
                a.id = army.id;
                ClientCivState.send(a);
                selArmy = -1;
            }).bounds(x, y, w, 20).build());
            y -= 24;
        }
        ClientView.CityInfo city = selX >= 0 ? cityAt(v, selX, selZ) : null;
        if (city != null && city.mine) {
            add(Button.builder(Component.literal("Manage " + city.name), b -> minecraft.setScreen(new CityScreen(city.id, this))).bounds(x, y, w, 20).build());
            y -= 24;
        }
        // diplomacy: one row per known civ
        for (ClientView.CivInfo c : v.civs) {
            if (c.id == v.myCivId || c.eliminated || v.myCivId < 0 || y < 120) continue;
            String label;
            String type;
            if (c.atWar) {
                label = (c.theyOfferedPeace ? "Accept peace: " : c.weOfferedPeace ? "Peace offered: " : "Offer peace: ") + c.name;
                type = "peace";
            } else {
                label = c.warCooldown > 0 ? "Treaty " + c.warCooldown + "t: " + c.name : "Declare war: " + c.name;
                type = "war";
            }
            Button b = Button.builder(Component.literal(label), btn -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = type;
                a.civ = c.id;
                ClientCivState.send(a);
            }).bounds(x, y, w, 20).build();
            b.active = !(type.equals("war") && c.warCooldown > 0) && !(type.equals("peace") && c.weOfferedPeace);
            add(b);
            y -= 24;
        }
    }

    private void add(Button b) {
        panelButtons.add(addRenderableWidget(b));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= mapWidth()) return false;
        dragging = button == 0;
        dragDist = 0;
        if (button == 1) {
            ClientView v = ClientCivState.view();
            ClientView.ArmyInfo army = v == null ? null : selectedArmy(v);
            int[] t = v == null ? null : tileAt(v, mx, my);
            if (army != null && army.mine && t != null) {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "move";
                a.id = army.id;
                a.x = t[0];
                a.z = t[1];
                ClientCivState.send(a);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean wasDrag = dragging && dragDist > 4;
        dragging = false;
        if (super.mouseReleased(mx, my, button)) return true;
        if (button == 0 && !wasDrag && mx < mapWidth()) select(mx, my);
        return true;
    }

    private void select(double mx, double my) {
        ClientView v = ClientCivState.view();
        if (v == null) return;
        int[] t = tileAt(v, mx, my);
        if (t == null) return;
        // clicking the same tile again cycles through its armies, own first
        List<ClientView.ArmyInfo> here = new ArrayList<>();
        for (ClientView.ArmyInfo a : v.armies) if (a.x == t[0] && a.z == t[1]) here.add(a);
        here.sort((a, b) -> Boolean.compare(b.mine, a.mine));
        long next = -1;
        if (!here.isEmpty()) {
            int idx = -1;
            for (int i = 0; i < here.size(); i++) if (here.get(i).id == selArmy) idx = i;
            next = (t[0] == selX && t[1] == selZ) ? (idx + 1 < here.size() ? here.get(idx + 1).id : -1) : here.get(0).id;
        }
        selX = t[0];
        selZ = t[1];
        selArmy = next;
        rebuildPanel();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragDist += Math.abs(dx) + Math.abs(dy);
            camX -= dx / zoom;
            camZ -= dy / zoom;
            ClientView v = ClientCivState.view();
            if (v != null) {
                if (wrapX(v)) camX = ((camX % v.width) + v.width) % v.width;
                if (wrapZ(v)) camZ = ((camZ % v.height) + v.height) % v.height;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        zoom = Math.max(3, Math.min(64, zoom * (scrollY > 0 ? 1.2 : 1 / 1.2)));
        return true;
    }
}
