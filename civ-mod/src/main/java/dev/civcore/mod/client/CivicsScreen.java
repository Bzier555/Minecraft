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

/** Government (left) and policies bought with culture (right). */
public class CivicsScreen extends Screen {
    private static final String[][] GOV_INFO = {
        {"DESPOTISM", "1 policy slot"},
        {"MONARCHY", "2 slots, +2 happiness, +10% production"},
        {"REPUBLIC", "3 slots, +15% science, +10% gold"},
        {"THEOCRACY", "2 slots, +2 happiness, +25% culture"},
        {"MERCHANT_REPUBLIC", "3 slots, +5% science, +25% gold"},
        {"CONSTITUTIONAL_MONARCHY", "4 slots, +3 happiness, +10% science, gold, production"},
    };

    private final Screen parent;
    private final List<Button> buttons = new ArrayList<>();
    private int ticks;
    private long lastView;

    public CivicsScreen(Screen parent) {
        super(Component.literal("Civics"));
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
        buttons.forEach(this::removeWidget);
        buttons.clear();
        ClientView v = ClientCivState.view();
        if (v == null || v.government == null) return;
        int y = 44, w = Math.min(220, width / 2 - 20);
        for (String[] gi : GOV_INFO) {
            String id = gi[0];
            boolean current = id.equals(v.government);
            Button b = Button.builder(Component.literal((current ? "[current] " : "") + ClientCivState.shortId(id.toLowerCase())), btn -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "government";
                a.item = id;
                ClientCivState.send(a);
            }).bounds(10, y, w, 20).build();
            b.active = !current && v.governments.contains(id);
            b.setTooltip(Tooltip.create(Component.literal(gi[1] + (v.governments.contains(id) ? "\nSwitching costs 2 turns of anarchy." : "\nNot yet discovered."))));
            buttons.add(addRenderableWidget(b));
            y += 24;
        }
        y = 44;
        int x = width / 2 + 10;
        for (String id : v.allPolicies) {
            boolean adopted = v.policies.contains(id);
            Button b = Button.builder(Component.literal((adopted ? "[x] " : "") + ClientCivState.shortId(id.toLowerCase())), btn -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "policy";
                a.item = id;
                ClientCivState.send(a);
            }).bounds(x, y, w, 20).build();
            b.active = !adopted && v.policies.size() < v.policySlots && v.freeCulture >= v.policyCost;
            b.setTooltip(Tooltip.create(Component.literal(v.policyText.getOrDefault(id, ""))));
            buttons.add(addRenderableWidget(b));
            y += 24;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ClientView v = ClientCivState.view();
        if (v == null || v.government == null) {
            g.drawCenteredString(font, "Loading...", width / 2, height / 2, 0xFFFFFF);
            return;
        }
        g.drawString(font, "Government" + (v.anarchyTurns > 0 ? " (anarchy: " + v.anarchyTurns + " turns)" : ""), 10, 28, 0xFFD700);
        g.drawString(font, "Policies " + v.policies.size() + "/" + v.policySlots + " | next costs " + v.policyCost + " culture (you have " + v.freeCulture + ")",
            width / 2 + 10, 28, 0xFFD700);
    }
}
