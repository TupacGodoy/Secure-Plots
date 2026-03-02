package com.zhilius.secureplots.client;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurePlotsClient implements ClientModInitializer {

    public static final List<BorderDisplay> activeBorders = new ArrayList<>();

    public static class BorderDisplay {
        public PlotData data;
        public long expiresAt;

        public BorderDisplay(PlotData data) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + 10000;
        }
    }

    @Override
    public void onInitializeClient() {

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenPlotScreenPayload.ID,
                (payload, context) -> {
                    BlockPos pos = payload.pos();
                    PlotData data = PlotData.fromNbt(payload.nbt());
                    context.client().execute(() -> context.client().setScreen(new PlotScreen(pos, data)));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.ShowPlotBorderPayload.ID,
                (payload, context) -> {
                    PlotData data = PlotData.fromNbt(payload.nbt());
                    context.client().execute(() -> {
                        activeBorders.removeIf(b -> b.data.getCenter().equals(data.getCenter()));
                        activeBorders.add(new BorderDisplay(data));
                    });
                });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            long now = System.currentTimeMillis();
            Iterator<BorderDisplay> iter = activeBorders.iterator();
            while (iter.hasNext()) {
                BorderDisplay display = iter.next();
                if (display.expiresAt < now) {
                    iter.remove();
                    continue;
                }
                PlotBorderRenderer.render(context, display.data);
            }
        });
    }
}
