package com.zhilius.secureplots.client;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurePlotsClient implements ClientModInitializer {

    public static final List<BorderDisplay> activeBorders = new ArrayList<>();

    public static class BorderDisplay {
        public PlotData data;
        public long expiresAt;

        // Upgrade transition: animate from old size/tier to new
        public int prevRadius   = -1;   // -1 = no transition
        public int prevTier     = -1;
        public long transitionStart = 0;
        public static final long TRANSITION_MS = 1400;

        // Expand-pulse: brief extra radius burst on upgrade arrival
        public long expandPulseStart = -1;
        public static final long EXPAND_PULSE_MS = 600;

        public BorderDisplay(PlotData data) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + 10000;
        }

        public void upgrade(PlotData newData) {
            this.prevRadius      = this.data.getSize().radius;
            this.prevTier        = this.data.getSize().tier;
            this.transitionStart = System.currentTimeMillis();
            this.expandPulseStart = System.currentTimeMillis();
            this.data            = newData;
            this.expiresAt       = System.currentTimeMillis() + 10000;
        }

        /** 0.0 = fully old, 1.0 = fully new. Returns 1.0 if no transition. */
        public float transitionProgress() {
            if (prevRadius < 0) return 1.0f;
            long elapsed = System.currentTimeMillis() - transitionStart;
            float p = Math.min(1.0f, (float) elapsed / TRANSITION_MS);
            return 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p); // ease-out cubic
        }

        /**
         * Extra radius to add beyond the lerped radius — creates the "energy pulse
         * expanding outward" effect on upgrade.  Peaks at ~+8 blocks then decays.
         */
        public float expandPulseRadius() {
            if (expandPulseStart < 0) return 0f;
            long elapsed = System.currentTimeMillis() - expandPulseStart;
            if (elapsed > EXPAND_PULSE_MS) { expandPulseStart = -1; return 0f; }
            float p = (float) elapsed / EXPAND_PULSE_MS;
            // rises fast then falls: sin curve clamped to first half
            return 8f * (float) Math.sin(p * Math.PI);
        }

        /** Lerped radius for this frame, plus any expand pulse. */
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

        /** Effective tier — switches at midpoint. */
        public int effectiveTier() {
            if (prevTier < 0) return data.getSize().tier;
            return transitionProgress() >= 0.5f ? data.getSize().tier : prevTier;
        }
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                HologramTestCommand.register(dispatcher));

        // PlotHologramClient.register(); — disabled, hologram is now a server-side TextDisplayEntity

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
                        MinecraftClient mc = context.client();

                        // PlotHologramClient.show(data, data.getCenter(), 10_000); — disabled

                        for (BorderDisplay bd : activeBorders) {
                            if (bd.data.getCenter().equals(data.getCenter())) {
                                if (bd.data.getSize().tier != data.getSize().tier) {
                                    // Upgrade — play upgrade sound + start transition
                                    bd.upgrade(data);
                                    playUpgradeSound(mc, data.getCenter());
                                } else {
                                    bd.data = data;
                                    bd.expiresAt = System.currentTimeMillis() + 10000;
                                }
                                return;
                            }
                        }
                        // New border shown — play appear sound
                        activeBorders.add(new BorderDisplay(data));
                        playAppearSound(mc, data.getCenter());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.HidePlotBorderPayload.ID,
                (payload, context) -> {
                    BlockPos pos = payload.pos();
                    context.client().execute(() -> {
                        activeBorders.removeIf(b -> b.data.getCenter().equals(pos));
                        PlotHologramClient.hide(pos);
                    });
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> { activeBorders.clear(); PlotHologramClient.clear(); }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeBorders.clear(); PlotHologramClient.clear(); });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            long now = System.currentTimeMillis();
            Iterator<BorderDisplay> iter = activeBorders.iterator();
            while (iter.hasNext()) {
                BorderDisplay display = iter.next();
                if (display.expiresAt < now) { iter.remove(); continue; }
                PlotBorderRenderer.render(context, display);
            }
        });
    }

    /** Soft "whoosh" when border first appears */
    private static void playAppearSound(MinecraftClient mc, BlockPos center) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f));
    }

    /** Epic sound on upgrade */
    private static void playUpgradeSound(MinecraftClient mc, BlockPos center) {
        if (mc.world == null || mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.1f));
        // Second layer — delayed pulse sound to match the expanding ring
        // We can't delay easily without a thread, so just play two at once
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.8f));
    }
}
