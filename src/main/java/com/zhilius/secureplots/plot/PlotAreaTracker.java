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

    private static int tick        = 0;
    private static int ambientTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlotAreaTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        com.zhilius.secureplots.config.SecurePlotsConfig cfgTick = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
        int checkInt   = (cfgTick != null && cfgTick.checkInterval   > 0) ? cfgTick.checkInterval   : 10;
        int ambientInt = (cfgTick != null && cfgTick.ambientInterval > 0) ? cfgTick.ambientInterval : 20;

        boolean doCheck   = (++tick >= checkInt);
        boolean doAmbient = (++ambientTick >= ambientInt);
        if (doCheck)   tick = 0;
        if (doAmbient) ambientTick = 0;

        if (!doCheck && !doAmbient) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld sw)) continue;

            PlotManager manager    = PlotManager.getOrCreate(sw);
            PlotData    current    = manager.getPlotAt(player.getBlockPos());
            UUID        id         = player.getUuid();
            BlockPos    prevCenter = lastPlot.get(id);
            BlockPos    curCenter  = current != null ? current.getCenter() : null;

            boolean same = (prevCenter == null && curCenter == null)
                    || (prevCenter != null && prevCenter.equals(curCenter));

            if (doCheck) updateFly(player, current);
            if (same) {
                // Partículas continuas: solo cada AMBIENT_INTERVAL ticks (cada segundo)
                if (doAmbient && current != null) {
                    com.zhilius.secureplots.config.SecurePlotsConfig cfgP = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
                    String pId = current.getParticleEffect();
                    if (!pId.isBlank() && (cfgP == null || cfgP.enablePlotParticles))
                        spawnAmbientParticles(sw, player, pId);
                }
                continue;
            }
            // Si no es un cambio de plot, no procesar entrada/salida
            if (!doCheck) continue;

            if (curCenter != null) {
                // ── Entered a plot ──────────────────────────────────────────

                // Action bar: plot name + owner
                com.zhilius.secureplots.config.SecurePlotsConfig cfg0 = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
                if (cfg0 == null || cfg0.enableEnterHud) {
                    player.sendMessage(
                        Text.translatable("sp.hud.entering",
                            Text.literal(current.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD),
                            Text.literal(current.getOwnerName()).formatted(Formatting.WHITE)),
                        true);
                }

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
        com.zhilius.secureplots.config.SecurePlotsConfig cfg = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;

        // Particles
        String particleId = plot.getParticleEffect();
        if (!particleId.isBlank() && (cfg == null || cfg.enablePlotParticles))
            spawnEnterParticles(sw, player, particleId);

        // Weather
        String weather = plot.getWeatherType();
        if (!weather.isBlank() && (cfg == null || cfg.enablePlotWeather)) {
            savedWeather.put(id, getCurrentWeather(sw));
            applyWeather(sw, weather);
        }

        // Time
        long time = plot.getPlotTime();
        if (time >= 0 && (cfg == null || cfg.enablePlotTime)) {
            savedTime.put(id, sw.getTimeOfDay() % 24000L);
            sw.setTimeOfDay(time);
        }

        // Music
        String music = plot.getMusicSound();
        if (!music.isBlank() && (cfg == null || cfg.enablePlotMusic))
            playMusic(player, sw, music);
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
            // Accept both "minecraft:happy_villager" and "happy_villager"
            String normalized = particleId.contains(":") ? particleId : "minecraft:" + particleId;
            Identifier rid = Identifier.tryParse(normalized);
            if (rid == null) return;
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(rid);
            if (!(type instanceof ParticleEffect effect)) return;
            com.zhilius.secureplots.config.SecurePlotsConfig cfgC = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
            // Burst de entrada: usa particleCount del config, máximo 5 para no saturar
            int count = (cfgC != null) ? Math.max(1, Math.min(cfgC.particleCount, 5)) : 3;
            Vec3d pos = player.getPos();
            // Outer ring (radius 3)
            for (int i = 0; i < 24; i++) {
                double angle = (2 * Math.PI / 24) * i;
                double x = pos.x + Math.cos(angle) * 3.0;
                double z = pos.z + Math.sin(angle) * 3.0;
                sw.spawnParticles(effect, x, pos.y + 0.3, z, count, 0, 0.2, 0, 0.05);
            }
            // Inner ring (radius 1.2)
            for (int i = 0; i < 12; i++) {
                double angle = (2 * Math.PI / 12) * i;
                double x = pos.x + Math.cos(angle) * 1.2;
                double z = pos.z + Math.sin(angle) * 1.2;
                sw.spawnParticles(effect, x, pos.y + 1.2, z, count + 1, 0, 0.15, 0, 0.04);
            }
            // Column burst above player
            sw.spawnParticles(effect, pos.x, pos.y + 0.5, pos.z, count * 5, 0.4, 0.8, 0.4, 0.06);
        } catch (Exception ignored) {}
    }

    // Llamado cada ambientInterval ticks mientras el jugador está dentro de la plot
    private static void spawnAmbientParticles(ServerWorld sw, ServerPlayerEntity player, String particleId) {
        try {
            String normalized = particleId.contains(":") ? particleId : "minecraft:" + particleId;
            Identifier rid = Identifier.tryParse(normalized);
            if (rid == null) return;
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(rid);
            if (!(type instanceof ParticleEffect effect)) return;
            com.zhilius.secureplots.config.SecurePlotsConfig cfgC = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
            int count = (cfgC != null) ? Math.max(1, Math.min(cfgC.ambientParticleCount, 5)) : 2;
            Vec3d pos = player.getPos();
            for (int i = 0; i < count; i++) {
                double angle = Math.random() * 2 * Math.PI;
                double r = 0.5 + Math.random() * 1.5;
                sw.spawnParticles(effect,
                    pos.x + Math.cos(angle) * r,
                    pos.y + Math.random() * 2.0,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0, 0, 0.01);
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
            com.zhilius.secureplots.config.SecurePlotsConfig cfgM = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
            float vol = (cfgM != null) ? Math.max(0.1f, Math.min(cfgM.musicVolume, 4.0f)) : 4.0f;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.RECORDS, vol, 1.0f);
        } catch (Exception ignored) {}
    }

    // ── Fly management ────────────────────────────────────────────────────────

    private static void updateFly(ServerPlayerEntity player, PlotData plot) {
        UUID id = player.getUuid();
        if (player.isCreative() || player.isSpectator()) return;
        com.zhilius.secureplots.config.SecurePlotsConfig cfgFly = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
        if (cfgFly != null && !cfgFly.enableFlyInPlots) return;

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
