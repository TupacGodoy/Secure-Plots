package com.zhilius.secureplots.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

/**
 * Renderiza rayos de partículas entre estacas activas (sesión en curso).
 *
 * - Partículas AMARILLAS: ángulos correctos (o todavía sin verificar).
 * - Partículas ROJAS: cuando el ángulo no es recto (error flash).
 *
 * Se actualiza vía packets del servidor.
 */
public class StakeParticleRenderer {

    public record StakeSession(
            UUID sessionId,
            List<BlockPos> stakes,   // posiciones en orden (0-3)
            boolean hasError,        // true = flash rojo
            long errorUntil          // timestamp ms hasta cuándo mostrar error
    ) {}

    /** Sesiones activas del jugador local */
    public static final Map<UUID, StakeSession> activeSessions = new HashMap<>();

    private static long lastParticleTick = 0;
    private static final long PARTICLE_INTERVAL_MS = 50; // cada 50ms spawn partículas

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Vector3f COLOR_OK    = new Vector3f(1.0f, 0.85f, 0.0f);  // amarillo
    private static final Vector3f COLOR_ERROR = new Vector3f(1.0f, 0.1f,  0.1f);  // rojo
    private static final float PARTICLE_SIZE  = 0.35f;
    private static final double SPACING       = 0.6; // bloques entre partículas

    // ── API ───────────────────────────────────────────────────────────────────

    public static void addStake(UUID sessionId, BlockPos pos, int index, BlockPos plotCenter) {
        StakeSession existing = activeSessions.get(sessionId);
        List<BlockPos> stakes;
        if (existing != null) {
            stakes = new ArrayList<>(existing.stakes());
        } else {
            stakes = new ArrayList<>();
        }
        // Ensure list is big enough
        while (stakes.size() <= index) stakes.add(null);
        stakes.set(index, pos);
        activeSessions.put(sessionId, new StakeSession(sessionId, stakes, false, 0));
    }

    public static void flashError(UUID sessionId, BlockPos errorPos) {
        StakeSession existing = activeSessions.get(sessionId);
        List<BlockPos> stakes = existing != null ? new ArrayList<>(existing.stakes()) : new ArrayList<>();
        if (!stakes.contains(errorPos)) stakes.add(errorPos);
        long until = System.currentTimeMillis() + 1500; // 1.5s de flash rojo
        activeSessions.put(sessionId, new StakeSession(sessionId, stakes, true, until));
    }

    public static void removeSession(UUID sessionId) {
        activeSessions.remove(sessionId);
    }

    public static void clearAll() {
        activeSessions.clear();
    }

    // ── Render tick ───────────────────────────────────────────────────────────

    public static void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastParticleTick < PARTICLE_INTERVAL_MS) return;
        lastParticleTick = now;

        ParticleManager pm = client.particleManager;
        Vec3d playerPos = client.player.getPos().add(0, client.player.getEyeHeight(client.player.getPose()) * 0.5, 0);

        for (StakeSession session : activeSessions.values()) {
            List<BlockPos> stakes = session.stakes();
            boolean isError = session.hasError() && now < session.errorUntil();
            Vector3f color = isError ? COLOR_ERROR : COLOR_OK;

            // Draw beam from each placed stake to the next, and last stake → player
            for (int i = 0; i < stakes.size(); i++) {
                BlockPos from = stakes.get(i);
                if (from == null) continue;

                Vec3d fromVec = Vec3d.ofCenter(from).add(0, 0.5, 0);

                // Beam to next stake
                if (i + 1 < stakes.size() && stakes.get(i + 1) != null) {
                    Vec3d toVec = Vec3d.ofCenter(stakes.get(i + 1)).add(0, 0.5, 0);
                    spawnBeam(world, pm, fromVec, toVec, color);
                }

                // Last stake → player (live preview)
                if (i == stakes.size() - 1 || (i == stakes.size() - 1)) {
                    spawnBeam(world, pm, fromVec, playerPos, color);
                }
            }
        }
    }

    // ── Particle beam ─────────────────────────────────────────────────────────

    private static void spawnBeam(ClientWorld world, ParticleManager pm,
                                   Vec3d from, Vec3d to, Vector3f color) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.1) return;

        double ux = dx / len, uy = dy / len, uz = dz / len;
        DustParticleEffect dust = new DustParticleEffect(color, PARTICLE_SIZE);

        int count = (int) (len / SPACING);
        for (int i = 0; i <= count; i++) {
            double t = i * SPACING;
            double px = from.x + ux * t;
            double py = from.y + uy * t;
            double pz = from.z + uz * t;
            // Small random offset for a glowing effect
            double jitter = 0.04;
            world.addParticle(dust,
                    px + (Math.random() - 0.5) * jitter,
                    py + (Math.random() - 0.5) * jitter,
                    pz + (Math.random() - 0.5) * jitter,
                    0, 0, 0);
        }
    }
}
