package com.zhilius.secureplots.client;

import com.mojang.brigadier.CommandDispatcher;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class HologramTestCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("hologramtest")
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null) return 0;

                    BlockPos pos = client.player.getBlockPos();
                    PlotData fakeData = new PlotData(
                        UUID.randomUUID(),
                        client.player.getName().getString(),
                        pos,
                        PlotSize.SMALL,
                        0L
                    );
                    fakeData.setPlotName("TEST Parcela");

                    // Inyectar en HUD directamente
                    PlotHologramRenderer.hudTargetPos = pos;
                    PlotHologramRenderer.hudTargetData = fakeData;
                    PlotHologramRenderer.hudExpiresAt = System.currentTimeMillis() + 8000;

                    // También en el mapa 3D
                    SecurePlotsClient.activeHolograms.put(pos, new SecurePlotsClient.HologramDisplay(pos, fakeData));

                    client.player.sendMessage(Text.literal("§aTest activado — deberías ver texto en el centro de la pantalla"), false);
                    return 1;
                })
        );
    }
}
