package dev.civcore.mod.client;

import dev.civcore.mod.net.CivNetwork;
import dev.civcore.sim.view.ClientView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** One of the player's cities: growth, yields, buildings and the production queue (click an item to queue it). */
public class CityScreen extends Screen {
    private final long cityId;
    private final Screen parent;
    private final List<Button> itemButtons = new ArrayList<>();
    private int ticks;
    private long lastView;

    public CityScreen(long cityId, Screen parent) {
        super(Component.literal("City"));
        this.cityId = cityId;
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ClientView.CityInfo city() {
        ClientView v = ClientCivState.view();
        if (v == null) return null;
        for (ClientView.CityInfo c : v.cities) if (c.id == cityId && c.mine) return c;
        return null;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Map"), b -> minecraft.setScreen(parent != null ? parent : new StrategicMapScreen()))
            .bounds(width - 70, height - 26, 60, 20).build());
        rebuildItems();
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) ClientCivState.requestView();
        if (ClientCivState.receivedAt() != lastView) {
            lastView = ClientCivState.receivedAt();
            rebuildItems();
        }
    }

    private void rebuildItems() {
        itemButtons.forEach(this::removeWidget);
        itemButtons.clear();
        ClientView.CityInfo c = city();
        if (c == null) return;
        int x = width / 2 + 10, y = 40, w = Math.min(200, width / 2 - 20);
        for (ClientView.Item it : c.available) {
            if (y > height - 40) break;
            int turns = c.productionYield <= 0 ? 0 : (int) Math.ceil(it.cost / (double) c.productionYield);
            String label = ClientCivState.shortId(it.id) + " (" + it.cost + (it.kind.equals("unit") ? ", str " + it.strength : "")
                + (turns > 0 ? ", ~" + turns + " turns" : "") + ")";
            Button b = Button.builder(Component.literal(label), btn -> {
                CivNetwork.Action a = new CivNetwork.Action();
                a.type = "queue";
                a.id = cityId;
                a.item = it.id;
                ClientCivState.send(a);
            }).bounds(x, y, w, 18).build();
            itemButtons.add(addRenderableWidget(b));
            y += 20;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ClientView.CityInfo c = city();
        if (c == null) {
            g.drawCenteredString(font, "Loading city...", width / 2, height / 2, 0xFFFFFF);
            return;
        }
        int x = 12, y = 12;
        g.drawString(font, c.name + (c.capital ? " (capital)" : ""), x, y, 0xFFD700);
        y += 16;
        int surplus = c.foodYield - c.foodEaten;
        String growth = surplus > 0 ? "grows in " + (int) Math.ceil((c.foodNeeded - c.food) / (double) surplus) + " turns"
            : surplus == 0 ? "not growing" : "STARVING";
        line(g, x, y, "Population " + c.population + "  (" + growth + ")");
        y += 12;
        line(g, x, y, "Food " + c.food + "/" + c.foodNeeded + "   +" + c.foodYield + " -" + c.foodEaten);
        y += 12;
        line(g, x, y, "Production +" + c.productionYield + "   stored " + c.production);
        y += 12;
        line(g, x, y, "Gold +" + c.goldYield + "   Defence " + c.hp + "/100");
        y += 18;
        g.drawString(font, "Building now:", x, y, 0xFFD700);
        y += 12;
        if (c.queue.isEmpty()) {
            line(g, x, y, "(nothing - pick something on the right)");
            y += 12;
        }
        for (String q : c.queue) {
            line(g, x, y, "- " + ClientCivState.shortId(q));
            y += 12;
        }
        y += 6;
        g.drawString(font, "Buildings:", x, y, 0xFFD700);
        y += 12;
        for (String b : c.buildings) {
            line(g, x, y, "- " + ClientCivState.shortId(b));
            y += 12;
        }
        g.drawString(font, "Add to queue:", width / 2 + 10, 26, 0xFFD700);
    }

    private void line(GuiGraphics g, int x, int y, String s) {
        g.drawString(font, s, x, y, 0xFFFFFF);
    }
}
