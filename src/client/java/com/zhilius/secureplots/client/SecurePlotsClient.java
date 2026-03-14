/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zhilius.secureplots.client;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SecurePlotsClient implements ClientModInitializer {

    public static final List<BorderDisplay> activeBorders = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Inner class: BorderDisplay
    // -------------------------------------------------------------------------
    public static class BorderDisplay {

        private static final long  DISPLAY_DURATION_MS     = 10_000;
        private static final long  TRANSITION_MS           = 5_600;
        private static final long  EXPAND_PULSE_MS         = 2_400;
        private static final float EXPAND_PULSE_MAX_RADIUS = 8f;

        public PlotData data;
        public long     expiresAt;

        // Upgrade transition fields — package-private for PlotBorderRenderer access
        int  prevRadius       = -1;
        int  prevTier         = -1;
        long transitionStart  = 0;
        long expandPulseStart = -1;

        public BorderDisplay(PlotData data) {
            this.data      = data;
            this.expiresAt = currentTimeWithOffset();
        }

        /** Starts an upgrade transition from the current size/tier to the new one. */
        public void upgrade(PlotData newData) {
            long now              = System.currentTimeMillis();
            this.prevRadius       = this.data.getSize().getRadius();
            this.prevTier         = this.data.getSize().tier;
            this.transitionStart  = now;
            this.expandPulseStart = now;
            this.data             = newData;
            this.expiresAt        = now + DISPLAY_DURATION_MS;
        }

        /** Refreshes data and resets the expiry timer without starting a transition. */
        public void refresh(PlotData newData) {
            this.data      = newData;
            this.expiresAt = currentTimeWithOffset();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public boolean isTransitioning() {
            return prevRadius >= 0;
        }

        /** Returns 0.0 (fully old) → 1.0 (fully new). Returns 1.0 if no transition is active. */
        public float transitionProgress() {
            if (!isTransitioning()) return 1.0f;
            float p   = Math.min(1.0f, (float)(System.currentTimeMillis() - transitionStart) / TRANSITION_MS);
            float inv = 1.0f - p;
            return 1.0f - inv * inv * inv; // ease-out cubic
        }

        /** Extra radius added on upgrade — creates an outward energy-pulse effect. */
        public float expandPulseRadius() {
            if (expandPulseStart < 0) return 0f;
            long elapsed = System.currentTimeMillis() - expandPulseStart;
            if (elapsed > EXPAND_PULSE_MS) { expandPulseStart = -1; return 0f; }
            float p = (float) elapsed / EXPAND_PULSE_MS;
            return EXPAND_PULSE_MAX_RADIUS * (float) Math.sin(p * Math.PI);
        }

        /** Interpolated radius for this frame, including any expand pulse. */
        public float effectiveRadiusF() {
            float base = isTransitioning()
                ? prevRadius + transitionProgress() * (data.getSize().getRadius() - prevRadius)
                : data.getSize().getRadius();
            return base + expandPulseRadius();
        }

        /** Integer version of {@link #effectiveRadiusF()} for callers that need an int. */
        public int effectiveRadius() {
            return (int) effectiveRadiusF();
        }

        /** Tier switches at the midpoint of the transition. */
        public int effectiveTier() {
            if (!isTransitioning()) return data.getSize().tier;
            return transitionProgress() >= 0.5f ? data.getSize().tier : prevTier;
        }

        private static long currentTimeWithOffset() {
            return System.currentTimeMillis() + DISPLAY_DURATION_MS;
        }
    }

    // -------------------------------------------------------------------------
    // ClientModInitializer
    // -------------------------------------------------------------------------
    @Override
    public void onInitializeClient() {
        PlotHologramClient.register();
        registerNetworkReceivers();
        registerConnectionEvents();
        registerWorldRenderer();
    }

    // -------------------------------------------------------------------------
    // Network receivers
    // -------------------------------------------------------------------------
    private void registerNetworkReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenPlotScreenPayload.ID,
            (payload, ctx) -> {
                BlockPos pos  = payload.pos();
                PlotData data = PlotData.fromNbt(payload.nbt());
                ctx.client().execute(() ->
                    ctx.client().setScreen(new PlotScreen(pos, data)));
            });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.ShowPlotBorderPayload.ID,
            (payload, ctx) -> {
                PlotData data = PlotData.fromNbt(payload.nbt());
                ctx.client().execute(() -> handleShowBorder(ctx.client(), data));
            });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.HidePlotBorderPayload.ID,
            (payload, ctx) -> {
                BlockPos pos = payload.pos();
                ctx.client().execute(() -> {
                    activeBorders.removeIf(b -> b.data.getCenter().equals(pos));
                    PlotHologramClient.hide(pos);
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenChatPayload.ID,
            (payload, ctx) -> {
                String prefill = payload.prefill();
                ctx.client().execute(() ->
                    ctx.client().setScreen(new ChatScreen(prefill)));
            });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SyncBorderConfigPayload.ID,
            (payload, ctx) -> ctx.client().execute(() ->
                PlotBorderRendererConfig.apply(payload.toBorderConfig())));
    }

    private void handleShowBorder(MinecraftClient mc, PlotData data) {
        // enableHologram is controlled server-side via BorderConfig.hologramEnabled (synced on join)
        if (PlotBorderRendererConfig.current.hologramEnabled) {
            PlotHologramClient.show(data, data.getCenter(), 10_000);
        }
        BorderDisplay existing = findBorderByCenter(data.getCenter());
        if (existing != null) {
            if (existing.data.getSize().tier != data.getSize().tier) {
                existing.upgrade(data);
                playUpgradeSound(mc);
            } else {
                existing.refresh(data);
            }
        } else {
            activeBorders.add(new BorderDisplay(data));
            playAppearSound(mc);
        }
    }

    /** Returns the first BorderDisplay whose center matches, or null. */
    private static BorderDisplay findBorderByCenter(BlockPos center) {
        for (BorderDisplay bd : activeBorders) {
            if (bd.data.getCenter().equals(center)) return bd;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Connection events
    // -------------------------------------------------------------------------
    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            client.execute(() -> { activeBorders.clear(); PlotHologramClient.clear(); }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeBorders.clear();
            PlotHologramClient.clear();
        });
    }

    // -------------------------------------------------------------------------
    // World renderer
    // -------------------------------------------------------------------------
    private void registerWorldRenderer() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            activeBorders.removeIf(BorderDisplay::isExpired);
            activeBorders.forEach(bd -> PlotBorderRenderer.render(context, bd));
        });
    }

    // -------------------------------------------------------------------------
    // Sound helpers
    // -------------------------------------------------------------------------

    /** Soft whoosh played when a border first appears. */
    private static void playAppearSound(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f));
    }

    /** Epic layered sound played on plot upgrade. */
    private static void playUpgradeSound(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.1f));
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.8f));
    }
}
