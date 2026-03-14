package com.zhilius.secureplots.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class HologramTestCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("hologramtest")
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null) return 0;
                    // Ahora los hologramas son server-side (TextDisplayEntity)
                    // Este comando ya no es necesario — el holograma aparece al colocar o clic derecho
                    client.player.sendMessage(
                        Text.literal("§eThe hologram is now a server entity. Place a block or right-click an existing one."),
                        false
                    );
                    return 1;
                })
        );
    }
}
