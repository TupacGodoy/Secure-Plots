package com.zhilius.secureplots.plot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players entering/leaving plots.
 * On enter: always shows an action-bar (HUD) notification with the plot name and owner,
 *           shows a custom enter message in the HUD (if set),
 *           and applies ambient effects: particles, weather, time, and music.
 * On exit:  shows the custom exit message in the HUD (if set)
 *           and restores ambient effects changed on enter.
 * Checks every 10 ticks.
 */
public class PlotAreaTracker {

    private static final Map<UUID, BlockPos> lastPlot     = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean>  flyGranted   = new ConcurrentHashMap<>();
    private static final Map<UUID, Long>     savedTime    = new ConcurrentHashMap<>();
    private static final Map<UUID, String>   savedWeather = new ConcurrentHashMap<>();

    private static int tick = 0;
    private static final int CHECK_INTERVAL = 10;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlotAreaTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (++tick < CHECK_INTERVAL) return;
        tick = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld sw)) continue;

            PlotManager manager    = PlotManager.getOrCreate(sw);
            PlotData    current    = manager.getPlotAt(player.getBlockPos());
            UUID        id         = player.getUuid();
            BlockPos    prevCenter = lastPlot.get(id);
            BlockPos    curCenter  = current != null ? current.getCenter() : null;

            boolean same = (prevCenter == null && curCenter == null)
                    || (prevCenter != null && prevCenter.equals(curCenter));

            updateFly(player, current);
            if (same) continue;

            if (curCenter != null) {
                // ── Entered a plot ──────────────────────────────────────────

                // Action bar: plot name + owner (always shown)
                player.sendMessage(
                    Text.translatable("sp.hud.entering",
                        Text.literal(current.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD),
                        Text.literal(current.getOwnerName()).formatted(Formatting.WHITE)),
                    true);

                // Custom enter message as title overlay (center screen, distinct from action bar)
                if (current.hasFlag(PlotData.Flag.GREETINGS)) {
                    String msg = current.getEnterMessage();
                    if (msg != null && !msg.isBlank()) {
                        sendTitleMessage(player, msg);
                    }
                }

                applyAmbientEnter(player, sw, current, id);
                lastPlot.put(id, curCenter);

            } else {
                // ── Left a plot ─────────────────────────────────────────────
                PlotData prev = prevCenter != null ? manager.getPlotAt(prevCenter) : null;
                if (prev != null && prev.hasFlag(PlotData.Flag.GREETINGS)) {
                    String msg = prev.getExitMessage();
                    if (msg != null && !msg.isBlank()) {
                        sendTitleMessage(player, msg);
                    }
                }

                restoreAmbient(player, sw, id);
                player.sendMessage(Text.literal(""), true);
                lastPlot.remove(id);
            }
        }
    }

    // ── Ambient: Enter ────────────────────────────────────────────────────────

    private static void applyAmbientEnter(ServerPlayerEntity player, ServerWorld sw,
                                          PlotData plot, UUID id) {
        // Particles
        String particleId = plot.getParticleEffect();
        if (!particleId.isBlank()) spawnEnterParticles(sw, player, particleId);

        // Weather
        String weather = plot.getWeatherType();
        if (!weather.isBlank()) {
            savedWeather.put(id, getCurrentWeather(sw));
            applyWeather(sw, weather);
        }

        // Time
        long time = plot.getPlotTime();
        if (time >= 0) {
            savedTime.put(id, sw.getTimeOfDay() % 24000L);
            sw.setTimeOfDay(time);
        }

        // Music
        String music = plot.getMusicSound();
        if (!music.isBlank()) playMusic(player, sw, music);
    }

    // ── Ambient: Exit ─────────────────────────────────────────────────────────

    private static void restoreAmbient(ServerPlayerEntity player, ServerWorld sw, UUID id) {
        String prevWeather = savedWeather.remove(id);
        if (prevWeather != null) applyWeather(sw, prevWeather);

        Long prevTime = savedTime.remove(id);
        if (prevTime != null) sw.setTimeOfDay(prevTime);

        // Stop music (RECORDS channel covers most music events)
        player.networkHandler.sendPacket(
            new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(
                null, SoundCategory.RECORDS));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void spawnEnterParticles(ServerWorld sw, ServerPlayerEntity player, String particleId) {
        try {
            Identifier rid = Identifier.tryParse(particleId);
            if (rid == null) return;
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(rid);
            if (!(type instanceof ParticleEffect effect)) return;
            Vec3d pos = player.getPos();
            for (int i = 0; i < 12; i++) {
                double angle = (2 * Math.PI / 12) * i;
                double x = pos.x + Math.cos(angle) * 1.5;
                double z = pos.z + Math.sin(angle) * 1.5;
                sw.spawnParticles(effect, x, pos.y + 1.0, z, 1, 0, 0.1, 0, 0.05);
            }
        } catch (Exception ignored) {}
    }

    private static String getCurrentWeather(ServerWorld sw) {
        if (sw.isThundering()) return "THUNDER";
        if (sw.isRaining())    return "RAIN";
        return "CLEAR";
    }

    private static void applyWeather(ServerWorld sw, String weather) {
        switch (weather.toUpperCase()) {
            case "CLEAR"   -> sw.setWeather(0, 6000, false, false);
            case "RAIN"    -> sw.setWeather(0, 6000, true,  false);
            case "THUNDER" -> sw.setWeather(0, 6000, true,  true);
        }
    }

    private static void playMusic(ServerPlayerEntity player, ServerWorld sw, String soundId) {
        try {
            Identifier rid = Identifier.tryParse(soundId);
            if (rid == null) return;
            net.minecraft.sound.SoundEvent event = net.minecraft.registry.Registries.SOUND_EVENT.get(rid);
            if (event == null) return;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.RECORDS, 4.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    // ── Fly management ────────────────────────────────────────────────────────

    private static void updateFly(ServerPlayerEntity player, PlotData plot) {
        UUID id = player.getUuid();
        if (player.isCreative() || player.isSpectator()) return;

        boolean shouldHaveFly     = plot != null && plot.hasPermission(id, PlotData.Permission.FLY);
        boolean grantedByUs       = flyGranted.getOrDefault(id, false);
        boolean currentlyAllowed  = player.getAbilities().allowFlying;

        if (shouldHaveFly && !currentlyAllowed) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
            flyGranted.put(id, true);
        } else if (!shouldHaveFly && grantedByUs) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying      = false;
            player.sendAbilitiesUpdate();
            flyGranted.remove(id);
        }
    }

    /**
     * Sends a custom message as a title overlay (center screen).
     * Supports legacy '&' color codes. Appears above the action bar,
     * so both the plot name (action bar) and custom message (title) are visible simultaneously.
     */
    private static void sendTitleMessage(ServerPlayerEntity player, String msg) {
        Text text = Text.literal(msg.replace("&", "\u00a7"));
        // fade-in 10, stay 60, fade-out 20 ticks
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        player.networkHandler.sendPacket(new TitleS2CPacket(text));
    }

    /** Called when a player disconnects — clean up all state. */
    public static void onPlayerLeave(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (flyGranted.remove(id) != null) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying      = false;
            player.sendAbilitiesUpdate();
        }
        lastPlot.remove(id);
        savedTime.remove(id);
        savedWeather.remove(id);
    }
}
