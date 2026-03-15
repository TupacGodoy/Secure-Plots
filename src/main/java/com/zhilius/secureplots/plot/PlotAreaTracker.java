/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
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
 * On enter: shows an action-bar notification, optional title message,
 *           and applies ambient effects (particles, weather, time, music).
 * On exit:  shows optional exit message and restores ambient effects.
 * Checks every checkInterval ticks (default 10).
 */
public class PlotAreaTracker {

    private static final Map<UUID, BlockPos> lastPlot     = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean>  flyGranted   = new ConcurrentHashMap<>();
    private static final Map<UUID, Long>     savedTime    = new ConcurrentHashMap<>();
    private static final Map<UUID, String>   savedWeather = new ConcurrentHashMap<>();

    private static int tick        = 0;
    private static int ambientTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlotAreaTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        int checkInt   = (cfg != null && cfg.checkInterval   > 0) ? cfg.checkInterval   : 10;
        int ambientInt = (cfg != null && cfg.ambientInterval > 0) ? cfg.ambientInterval : 20;

        boolean doCheck   = (++tick        >= checkInt);
        boolean doAmbient = (++ambientTick >= ambientInt);
        if (doCheck)   tick        = 0;
        if (doAmbient) ambientTick = 0;

        if (!doCheck && !doAmbient) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld sw)) continue;

            PlotManager manager   = PlotManager.getOrCreate(sw);
            PlotData    current   = manager.getPlotAt(player.getBlockPos());
            UUID        id        = player.getUuid();
            BlockPos    prevCenter = lastPlot.get(id);
            BlockPos    curCenter  = current != null ? current.getCenter() : null;

            boolean same = prevCenter == null ? curCenter == null : prevCenter.equals(curCenter);

            if (doCheck) updateFly(player, current);

            if (same) {
                // Continuous particles: only every ambientInterval ticks
                if (doAmbient && current != null) {
                    String pId = current.getParticleEffect();
                    if (!pId.isBlank() && (cfg == null || cfg.enablePlotParticles))
                        spawnAmbientParticles(sw, player, pId);
                }
                continue;
            }

            if (!doCheck) continue;

            if (curCenter != null) {
                // ── Entered a plot ────────────────────────────────────────────
                if (cfg == null || cfg.enableEnterHud) {
                    player.sendMessage(
                        Text.translatable("sp.hud.entering",
                            Text.literal(current.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD),
                            Text.literal(current.getOwnerName()).formatted(Formatting.WHITE)),
                        true);
                }
                if ((cfg == null || cfg.enableGreetingMessages) && current.hasFlag(PlotData.Flag.GREETINGS)) {
                    String msg = current.getEnterMessage();
                    if (msg != null && !msg.isBlank()) sendTitleMessage(player, msg);
                }
                applyAmbientEnter(player, sw, current, id, cfg);
                lastPlot.put(id, curCenter);

            } else {
                // ── Left a plot ───────────────────────────────────────────────
                if ((cfg == null || cfg.enableGreetingMessages) && prevCenter != null) {
                    PlotData prev = manager.getPlotAt(prevCenter);
                    if (prev != null && prev.hasFlag(PlotData.Flag.GREETINGS)) {
                        String msg = prev.getExitMessage();
                        if (msg != null && !msg.isBlank()) sendTitleMessage(player, msg);
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
                                          PlotData plot, UUID id, SecurePlotsConfig cfg) {
        String particleId = plot.getParticleEffect();
        if (!particleId.isBlank() && (cfg == null || cfg.enablePlotParticles))
            spawnEnterParticles(sw, player, particleId);

        String weather = plot.getWeatherType();
        if (!weather.isBlank() && (cfg == null || cfg.enablePlotWeather)) {
            savedWeather.put(id, getCurrentWeather(sw));
            applyWeather(sw, weather);
        }

        long time = plot.getPlotTime();
        if (time >= 0 && (cfg == null || cfg.enablePlotTime)) {
            savedTime.put(id, sw.getTimeOfDay() % 24000L);
            sw.setTimeOfDay(time);
        }

        String music = plot.getMusicSound();
        if (!music.isBlank() && (cfg == null || cfg.enablePlotMusic))
            playMusic(player, sw, music, cfg);
    }

    // ── Ambient: Exit ─────────────────────────────────────────────────────────

    private static void restoreAmbient(ServerPlayerEntity player, ServerWorld sw, UUID id) {
        String prevWeather = savedWeather.remove(id);
        if (prevWeather != null) applyWeather(sw, prevWeather);

        Long prevTime = savedTime.remove(id);
        if (prevTime != null) sw.setTimeOfDay(prevTime);

        // Stop music (RECORDS channel covers most music events)
        player.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.RECORDS));
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    /** Normalizes a particle ID and resolves it to a ParticleEffect, or returns null. */
    private static ParticleEffect resolveParticle(String particleId) {
        String normalized = particleId.contains(":") ? particleId : "minecraft:" + particleId;
        Identifier rid = Identifier.tryParse(normalized);
        if (rid == null) return null;
        ParticleType<?> type = Registries.PARTICLE_TYPE.get(rid);
        return type instanceof ParticleEffect effect ? effect : null;
    }

    private static void spawnEnterParticles(ServerWorld sw, ServerPlayerEntity player, String particleId) {
        try {
            ParticleEffect effect = resolveParticle(particleId);
            if (effect == null) return;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            int count = cfg != null ? Math.max(1, Math.min(cfg.particleCount, 5)) : 3;
            Vec3d pos = player.getPos();
            // Outer ring (radius 3)
            for (int i = 0; i < 24; i++) {
                double a = (2 * Math.PI / 24) * i;
                sw.spawnParticles(effect, pos.x + Math.cos(a) * 3.0, pos.y + 0.3, pos.z + Math.sin(a) * 3.0,
                    count, 0, 0.2, 0, 0.05);
            }
            // Inner ring (radius 1.2)
            for (int i = 0; i < 12; i++) {
                double a = (2 * Math.PI / 12) * i;
                sw.spawnParticles(effect, pos.x + Math.cos(a) * 1.2, pos.y + 1.2, pos.z + Math.sin(a) * 1.2,
                    count + 1, 0, 0.15, 0, 0.04);
            }
            // Column burst above player
            sw.spawnParticles(effect, pos.x, pos.y + 0.5, pos.z, count * 5, 0.4, 0.8, 0.4, 0.06);
        } catch (Exception ignored) {}
    }

    // Called every ambientInterval ticks while the player is inside the plot
    private static void spawnAmbientParticles(ServerWorld sw, ServerPlayerEntity player, String particleId) {
        try {
            ParticleEffect effect = resolveParticle(particleId);
            if (effect == null) return;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            int count = cfg != null ? Math.max(1, Math.min(cfg.ambientParticleCount, 5)) : 2;
            Vec3d pos = player.getPos();
            for (int i = 0; i < count; i++) {
                double angle = Math.random() * 2 * Math.PI;
                double r     = 0.5 + Math.random() * 1.5;
                sw.spawnParticles(effect,
                    pos.x + Math.cos(angle) * r,
                    pos.y + Math.random() * 2.0,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0, 0, 0.01);
            }
        } catch (Exception ignored) {}
    }

    // ── Weather / Time / Music ────────────────────────────────────────────────

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

    private static void playMusic(ServerPlayerEntity player, ServerWorld sw,
                                  String soundId, SecurePlotsConfig cfg) {
        try {
            Identifier rid = Identifier.tryParse(soundId);
            if (rid == null) return;
            SoundEvent event = Registries.SOUND_EVENT.get(rid);
            if (event == null) return;
            float vol = cfg != null ? Math.max(0.1f, Math.min(cfg.musicVolume, 4.0f)) : 4.0f;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.RECORDS, vol, 1.0f);
        } catch (Exception ignored) {}
    }

    // ── Fly management ────────────────────────────────────────────────────────

    private static void updateFly(ServerPlayerEntity player, PlotData plot) {
        if (player.isCreative() || player.isSpectator()) return;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && !cfg.enableFlyInPlots) return;

        UUID id = player.getUuid();
        boolean shouldHaveFly = plot != null && plot.hasPermission(id, PlotData.Permission.FLY);
        boolean grantedByUs   = flyGranted.getOrDefault(id, false);

        if (shouldHaveFly && !player.getAbilities().allowFlying) {
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
     * Supports legacy '&' color codes.
     */
    private static void sendTitleMessage(ServerPlayerEntity player, String msg) {
        Text text = Text.literal(msg.replace("&", "\u00a7"));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        player.networkHandler.sendPacket(new TitleS2CPacket(text));
    }

    /** Called when a player disconnects — cleans up all tracked state. */
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