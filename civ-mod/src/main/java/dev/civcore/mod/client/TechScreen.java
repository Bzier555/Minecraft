package dev.civcore.mod.client;

import dev.civcore.mod.net.CivNetwork;
import dev.civcore.sim.view.ClientView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Technologies as a grid: known (green), available (click to research), locked (grey). */
public class TechScreen extends Screen {
    private final Screen parent;
    private final List<Button> techButtons = new ArrayList<>();
    private final java.util.Map<Integer, String> eraLabels = new java.util.TreeMap<>();

    private static int eraOrder(String era) {
        return switch (era == null ? "" : era) {
            case "ancient" -> 0;
            case "medieval" -> 1;
            case "industrial" -> 2;
            default -> 3;
        };
    }
    private int ticks;
    private long lastView;

    public TechScreen(Screen parent) {
        super(Component.literal("Technologies"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Map"), b -> minecraft.setScreen(parent != null ? parent : new StrategicMapScreen()))
            .bounds(width - 70, height - 26, 60, 20).build());
        rebuild();
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) ClientCivState.requestView();
        if (ClientCivState.receivedAt() != lastView) {
            lastView = ClientCivState.receivedAt();
            rebuild();
        }
    }

    private void rebuild() {
        techButtons.forEach(this::removeWidget);
        techButtons.clear();
        ClientView v = ClientCivState.view();
        if (v == null) return;
        int cols = Math.max(1, (width - 20) / 140);
        int row = 0;
        String era = null;
        int col = 0;
        eraLabels.clear();
        for (ClientView.TechInfo t : v.techs.stream().sorted(java.util.Comparator.comparingInt((ClientView.TechInfo x) -> eraOrder(x.era)).thenComparingInt(x -> x.cost)).toList()) {
            if (!t.era.equals(era)) {
                if (era != null) row += col > 0 ? 1 : 0;
                era = t.era;
                col = 0;
                eraLabels.put(40 + row * 20, ClientCivState.shortId(era));
                row++;
            }
            if (col >= cols) {
                col = 0;
                row++;
            }
            int x = 10 + col * 140, y = 40 + row * 20 - 8;
            col++;
            String mark = t.status == 0 ? "[x] " : t.id.equals(v.currentResearch) ? "[>] " : "";
            Button b = Button.builder(Component.literal(mark + ClientCivState.shortId(t.id) + " (" + t.cost + ")"), btn -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "research";
                a.item = t.id;
                ClientCivState.send(a);
            }).bounds(x, y, 134, 18).build();
            b.active = t.status == 1 && !t.id.equals(v.currentResearch);
            StringBuilder tip = new StringBuilder();
            if (!t.requires.isEmpty()) tip.append("Requires: ").append(String.join(", ", t.requires.stream().map(ClientCivState::shortId).toList()));
            if (!t.unlocks.isEmpty()) tip.append(tip.isEmpty() ? "" : "\n").append("Unlocks: ").append(String.join(", ", t.unlocks.stream().map(ClientCivState::shortId).toList()));
            if (!tip.isEmpty()) b.setTooltip(Tooltip.create(Component.literal(tip.toString())));
            techButtons.add(addRenderableWidget(b));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ClientView v = ClientCivState.view();
        String head = v == null ? "Loading..." : v.currentResearch == null ? "Choose a technology to research."
            : "Researching " + ClientCivState.shortId(v.currentResearch) + ": " + v.researchProgress + " science so far";
        g.drawString(font, head, 10, 14, 0xFFD700);
        eraLabels.forEach((y, label) -> g.drawString(font, label + " era", 10, y - 10, 0xC0C0FF));
    }
}
