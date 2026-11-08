package dev.civcore.mod.client;

import dev.civcore.mod.net.CivNetwork;
import dev.civcore.sim.view.ClientView;
import dev.civcore.sim.view.ViewBuilder;
import net.minecraft.client.Minecraft;

/** The latest view the server sent, plus helpers the screens share. Client thread only. */
public final class ClientCivState {
    private static ClientView view;
    private static long receivedAt;

    private ClientCivState() {
    }

    public static ClientView view() {
        return view;
    }

    public static long receivedAt() {
        return receivedAt;
    }

    public static void acceptView(byte[] gz) {
        view = ViewBuilder.fromJson(CivNetwork.gunzip(gz));
        receivedAt = System.currentTimeMillis();
    }

    public static void requestView() {
        CivNetwork.Action a = new CivNetwork.Action();
        a.type = "view";
        CivNetwork.sendAction(a);
    }

    public static void send(CivNetwork.Action a) {
        CivNetwork.sendAction(a);
    }

    public static void open(String screen, long id) {
        Minecraft mc = Minecraft.getInstance();
        requestView();
        switch (screen) {
            case "city" -> mc.setScreen(new CityScreen(id, null));
            case "tech" -> mc.setScreen(new TechScreen(null));
            default -> mc.setScreen(new StrategicMapScreen());
        }
    }

    public static ClientView.CivInfo civ(int id) {
        if (view == null) return null;
        for (ClientView.CivInfo c : view.civs) if (c.id == id) return c;
        return null;
    }

    public static String shortId(String id) {
        if (id == null) return "-";
        String s = id.replace("civcore:", "").replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
