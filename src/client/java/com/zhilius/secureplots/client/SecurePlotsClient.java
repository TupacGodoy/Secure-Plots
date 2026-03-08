package com.zhilius.secureplots.client;

import com.zhilius.secureplots.item.PlotStakeItem;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SecurePlotsClient implements ClientModInitializer {

    public static final List<BorderDisplay> activeBorders = new ArrayList<>();

    /** Subdivisiones activas para renderizar: clave = centro de la plot */
    public static final Map<BlockPos, PlotData> activeSubdivisions = new HashMap<>();

    public static class BorderDisplay {
        public PlotData data;
        public long expiresAt;

        public int prevRadius   = -1;
        public int prevTier     = -1;
        public long transitionStart = 0;
        public static final long TRANSITION_MS = 1400;

        public long expandPulseStart = -1;
        public static final long EXPAND_PULSE_MS = 600;

        public BorderDisplay(PlotData data) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + 10000;
        }

        public void upgrade(PlotData newData) {
            this.prevRadius       = this.data.getSize().radius;
            this.prevTier         = this.data.getSize().tier;
            this.transitionStart  = System.currentTimeMillis();
            this.expandPulseStart = System.currentTimeMillis();
            this.data             = newData;
            this.expiresAt        = System.currentTimeMillis() + 10000;
        }

        public float transitionProgress() {
            if (prevRadius < 0) return 1.0f;
            long elapsed = System.currentTimeMillis() - transitionStart;
            float p = Math.min(1.0f, (float) elapsed / TRANSITION_MS);
            return 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
        }

        public float expandPulseRadius() {
            if (expandPulseStart < 0) return 0f;
            long elapsed = System.currentTimeMillis() - expandPulseStart;
            if (elapsed > EXPAND_PULSE_MS) { expandPulseStart = -1; return 0f; }
            float p = (float) elapsed / EXPAND_PULSE_MS;
            return 8f * (float) Math.sin(p * Math.PI);
        }

        public int effectiveRadius() { return (int) effectiveRadiusF(); }

        public float effectiveRadiusF() {
            float base;
            if (prevRadius < 0) {
                base = data.getSize().radius;
            } else {
                float p = transitionProgress();
                base = prevRadius + p * (data.getSize().radius - prevRadius);
            }
            return base + expandPulseRadius();
        }

        public int effectiveTier() {
            if (prevTier < 0) return data.getSize().tier;
            return transitionProgress() >= 0.5f ? data.getSize().tier : prevTier;
        }
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                HologramTestCommand.register(dispatcher));

        PlotHologramClient.register();

        // ── Packet: open plot screen ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenPlotScreenPayload.ID,
                (payload, context) -> {
                    BlockPos pos = payload.pos();
                    PlotData data = PlotData.fromNbt(payload.nbt());
                    context.client().execute(() -> context.client().setScreen(new PlotScreen(pos, data)));
                });

        // ── Packet: show plot border ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.ShowPlotBorderPayload.ID,
                (payload, context) -> {
                    PlotData data = PlotData.fromNbt(payload.nbt());
                    context.client().execute(() -> {
                        MinecraftClient mc = context.client();
                        PlotHologramClient.show(data, data.getCenter(), 10_000);

                        for (BorderDisplay bd : activeBorders) {
                            if (bd.data.getCenter().equals(data.getCenter())) {
                                if (bd.data.getSize().tier != data.getSize().tier) {
                                    bd.upgrade(data);
                                    playUpgradeSound(mc, data.getCenter());
                                } else {
                                    bd.data = data;
                                    bd.expiresAt = System.currentTimeMillis() + 10000;
                                }
                                return;
                            }
                        }
                        activeBorders.add(new BorderDisplay(data));
                        playAppearSound(mc, data.getCenter());
                    });
                });

        // ── Packet: hide plot border ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.HidePlotBorderPayload.ID,
                (payload, context) -> {
                    BlockPos pos = payload.pos();
                    context.client().execute(() -> {
                        activeBorders.removeIf(b -> b.data.getCenter().equals(pos));
                        PlotHologramClient.hide(pos);
                    });
                });

        // ── Packet: show subdivisions ─────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.ShowSubdivisionsPayload.ID,
                (payload, context) -> {
                    PlotData data = PlotData.fromNbt(payload.nbt());
                    context.client().execute(() ->
                        activeSubdivisions.put(data.getCenter(), data)
                    );
                });

        // ── Packet: hide subdivisions ─────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.HideSubdivisionsPayload.ID,
                (payload, context) -> {
                    BlockPos pos = payload.plotCenter();
                    context.client().execute(() -> activeSubdivisions.remove(pos));
                });

        // ── Packet: stake placed (start/update particle session) ──────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.StakeUpdatePayload.ID,
                (payload, context) -> {
                    BlockPos stakePos = payload.stakePos();
                    net.minecraft.nbt.NbtCompound nbt = payload.data();
                    context.client().execute(() -> {
                        try {
                            java.util.UUID sessionId = java.util.UUID.fromString(nbt.getString("sessionId"));
                            int index = nbt.getInt("index");
                            StakeParticleRenderer.addStake(sessionId, stakePos, index,
                                    new BlockPos(nbt.getInt("plotCX"), nbt.getInt("plotCY"), nbt.getInt("plotCZ")));
                            // If 4th stake (index 3), session is complete — clear after 3s
                            if (index >= 3) {
                                java.util.UUID sid = sessionId;
                                new Thread(() -> {
                                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                                    context.client().execute(() -> StakeParticleRenderer.removeSession(sid));
                                }).start();
                            }
                        } catch (Exception ignored) {}
                    });
                });

        // ── Packet: stake angle error (flash red) ─────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.StakeAngleErrorPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            java.util.UUID sessionId = java.util.UUID.fromString(payload.sessionId());
                            StakeParticleRenderer.flashError(sessionId, payload.errorPos());
                        } catch (Exception ignored) {}
                    });
                });

        // ── Packet: stake session cancelled ──────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.StakeCancelledPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            java.util.UUID sessionId = java.util.UUID.fromString(payload.sessionId());
                            StakeParticleRenderer.removeSession(sessionId);
                        } catch (Exception ignored) {}
                    });
                });

        // ── Packet: open stake Y menu ─────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenStakeYMenuPayload.ID,
                (payload, context) -> context.client().execute(() ->
                    context.client().setScreen(new StakeYMenuScreen(
                            payload.stakePos(), payload.plotCenter(), payload.subName(),
                            payload.useY(), payload.yMin(), payload.yMax()))
                ));

        // ── Connection lifecycle ──────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> {
                    activeBorders.clear();
                    activeSubdivisions.clear();
                    StakeParticleRenderer.clearAll();
                    PlotHologramClient.clear();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeBorders.clear();
            activeSubdivisions.clear();
            StakeParticleRenderer.clearAll();
            PlotHologramClient.clear();
        });

        // ── World render: borders + subdivisions + stake particles ────────────
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            long now = System.currentTimeMillis();

            // Render active borders
            Iterator<BorderDisplay> iter = activeBorders.iterator();
            while (iter.hasNext()) {
                BorderDisplay display = iter.next();
                if (display.expiresAt < now) { iter.remove(); continue; }
                PlotBorderRenderer.render(context, display);
            }

            // Render subdivisions
            for (Map.Entry<BlockPos, PlotData> entry : activeSubdivisions.entrySet()) {
                SubdivisionRenderer.render(context, entry.getValue(), false, "");
            }

            // Stake particle beams
            StakeParticleRenderer.onWorldRender(context);
        });
    }

    private static void playAppearSound(MinecraftClient mc, BlockPos center) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f));
    }

    private static void playUpgradeSound(MinecraftClient mc, BlockPos center) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.1f));
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.8f));
    }
}
