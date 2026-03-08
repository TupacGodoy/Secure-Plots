package com.zhilius.secureplots.block;

import com.mojang.serialization.MapCodec;
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.blockentity.PlotStakeBlockEntity;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSubdivision;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estaca de Parcela — se coloca en las 4 esquinas de una subdivisión.
 *
 * Reglas:
 *  - Cada estaca se asocia a un "grupo de 4" identificado por UUID (sessionId).
 *  - Las estacas cercanas (misma plot, mismo jugador) pertenecen al mismo grupo.
 *  - Al colocar la 1ra estaca: inicia sesión, partículas amarillas hasta el jugador.
 *  - Al colocar la 2da/3ra: verifica ángulo recto con las anteriores.
 *    Si el ángulo NO es recto → sonido de cancelación, NO coloca el bloque.
 *  - Al colocar la 4ta: verifica que las 4 formen un cuadrilátero con 90° → crea subdivisión.
 *  - Click derecho en cualquier estaca del grupo → abre menú de Y.
 */
public class PlotStakeBlock extends BlockWithEntity {

    public PlotStakeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(PlotStakeBlock::new);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlotStakeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (world.isClient) return null;
        return BlockWithEntity.validateTicker(type,
                ModBlockEntities.PLOT_STAKE_BLOCK_ENTITY,
                (w, pos, s, be) -> PlotStakeBlockEntity.tick(w, pos, s, (PlotStakeBlockEntity) be));
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack stack) {
        if (world.isClient || !(placer instanceof ServerPlayerEntity player)) return;
        ServerWorld sw = (ServerWorld) world;

        // Verify inside a plot
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData plot = manager.getPlotAt(pos);
        if (plot == null) {
            world.breakBlock(pos, true, player);
            player.sendMessage(Text.literal("§c✗ Debes colocar la estaca dentro de una parcela."), true);
            return;
        }

        // Check permission
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_SUBDIVISIONS)
                && !player.getUuid().equals(plot.getOwnerId())
                && !player.getCommandTags().contains("plot_admin")) {
            world.breakBlock(pos, true, player);
            player.sendMessage(Text.literal("§c✗ No tenés permiso para crear subdivisiones aquí."), true);
            return;
        }

        PlotStakeBlockEntity be = getBlockEntity(world, pos);
        if (be == null) return;

        be.setOwnerId(player.getUuid());
        be.setPlotCenter(plot.getCenter());

        // Find existing session for this player in this plot
        List<BlockPos> siblings = findSessionStakes(sw, plot, player.getUuid());

        if (siblings.isEmpty()) {
            // First stake: start new session
            UUID sessionId = UUID.randomUUID();
            be.setSessionId(sessionId);
            be.setStakeIndex(0);
            be.markDirty();

            player.sendMessage(Text.literal("§e✦ Estaca #1 colocada. Coloca 3 más para definir la subdivisión."), true);
            // Notify client to start particle beam
            ModPackets.sendStakeUpdate(player, pos, sessionId, 0, true, plot.getCenter());

        } else {
            int count = siblings.size(); // how many already placed (0-indexed, so this is index)

            // Validate right angle with previous stakes
            if (count >= 2) {
                List<BlockPos> allPositions = new ArrayList<>(siblings);
                allPositions.add(pos);

                if (!hasRightAngles(allPositions)) {
                    // Cancel placement
                    world.breakBlock(pos, true, player);
                    sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                            SoundCategory.PLAYERS, 1.0f, 0.5f);
                    player.sendMessage(Text.literal("§c✗ Las estacas no forman ángulos rectos. ¡Reubicá la estaca!"), true);
                    // Notify client: flash red particles
                    ModPackets.sendStakeAngleError(player, pos, getSessionId(sw, siblings.get(0)));
                    return;
                }
            }

            // Get session from first sibling
            PlotStakeBlockEntity firstBe = getBlockEntity(sw, siblings.get(0));
            if (firstBe == null) return;
            UUID sessionId = firstBe.getSessionId();

            be.setSessionId(sessionId);
            be.setStakeIndex(count);
            be.setPlotCenter(plot.getCenter());
            be.markDirty();

            if (count < 3) {
                player.sendMessage(Text.literal(
                        "§e✦ Estaca #" + (count + 1) + " colocada. Faltan " + (3 - count) + " más."), true);
                ModPackets.sendStakeUpdate(player, pos, sessionId, count, true, plot.getCenter());
            } else {
                // 4th stake: validate full quadrilateral and create subdivision
                List<BlockPos> all = new ArrayList<>(siblings);
                all.add(pos);

                if (!isValidQuadrilateral(all)) {
                    world.breakBlock(pos, true, player);
                    sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                            SoundCategory.PLAYERS, 1.0f, 0.5f);
                    player.sendMessage(Text.literal("§c✗ Las 4 estacas no forman un cuadrilátero válido con 90°."), true);
                    ModPackets.sendStakeAngleError(player, pos, sessionId);
                    return;
                }

                // Create subdivision
                String subName = nextSubName(plot);
                PlotSubdivision sub = plot.getOrCreateSubdivision(subName);
                // Add points in order (sorted by stake index)
                for (BlockPos sp : sortByIndex(sw, all)) {
                    sub.addPoint(sp.getX(), sp.getZ());
                }
                manager.markDirty();

                // Store subdivision name in all stake block entities
                for (BlockPos sp : all) {
                    PlotStakeBlockEntity sbe = getBlockEntity(sw, sp);
                    if (sbe != null) {
                        sbe.setSubdivisionName(subName);
                        sbe.markDirty();
                    }
                }

                ModPackets.sendStakeUpdate(player, pos, sessionId, 3, true, plot.getCenter());
                ModPackets.sendShowSubdivisions(player, plot);
                player.sendMessage(Text.literal(
                        "§a✔ Subdivisión §e\"" + subName + "\"§a creada. Click derecho en una estaca para configurar altura."), false);
            }
        }
    }

    // ── Interaction (right-click) ──────────────────────────────────────────────

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ServerWorld sw = (ServerWorld) world;

        PlotStakeBlockEntity be = getBlockEntity(world, pos);
        if (be == null) return ActionResult.PASS;

        String subName = be.getSubdivisionName();
        BlockPos plotCenter = be.getPlotCenter();
        if (subName == null || subName.isEmpty() || plotCenter == null) {
            sp.sendMessage(Text.literal("§7Esta estaca aún no tiene subdivisión asignada."), true);
            return ActionResult.SUCCESS;
        }

        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData plot = manager.getPlot(plotCenter);
        if (plot == null) return ActionResult.PASS;

        PlotSubdivision sub = plot.getSubdivision(subName);
        if (sub == null) return ActionResult.PASS;

        // Open Y config menu
        ModPackets.sendOpenStakeYMenu(sp, pos, plotCenter, subName, sub.useY, sub.yMin, sub.yMax);
        return ActionResult.SUCCESS;
    }

    // ── Break ─────────────────────────────────────────────────────────────────

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            ServerWorld sw = (ServerWorld) world;
            PlotStakeBlockEntity be = getBlockEntity(world, pos);
            if (be != null) {
                String subName = be.getSubdivisionName();
                BlockPos plotCenter = be.getPlotCenter();
                if (subName != null && !subName.isEmpty() && plotCenter != null) {
                    PlotManager manager = PlotManager.getOrCreate(sw);
                    PlotData plot = manager.getPlot(plotCenter);
                    if (plot != null) {
                        plot.removeSubdivision(subName);
                        manager.markDirty();
                        ModPackets.sendHideSubdivisions(sp, plotCenter);
                        // Remove sibling stakes
                        removeSiblingStakes(sw, plot, be.getSessionId(), pos, sp);
                        sp.sendMessage(Text.literal("§c✗ Subdivisión §e\"" + subName + "\"§c eliminada."), false);
                    }
                } else {
                    // Stake without subdivision yet — just remove from session
                    UUID sessionId = be.getSessionId();
                    if (sessionId != null) {
                        ModPackets.sendStakeCancelled(sp, sessionId);
                    }
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Checks if the list of 3 or 4 positions forms right angles at each vertex.
     * Only checks angles at the vertices that connect 3 points.
     */
    private boolean hasRightAngles(List<BlockPos> positions) {
        int n = positions.size();
        if (n < 3) return true;
        // Check the last added point creates a right angle with the two previous
        BlockPos a = positions.get(n - 3);
        BlockPos b = positions.get(n - 2);
        BlockPos c = positions.get(n - 1);
        return isRightAngle(a, b, c);
    }

    /**
     * Returns true if the angle at B (A→B→C) is 90°.
     * Uses dot product: (BA · BC) == 0
     */
    private boolean isRightAngle(BlockPos a, BlockPos b, BlockPos c) {
        int bax = a.getX() - b.getX(), baz = a.getZ() - b.getZ();
        int bcx = c.getX() - b.getX(), bcz = c.getZ() - b.getZ();
        long dot = (long) bax * bcx + (long) baz * bcz;
        return dot == 0;
    }

    /**
     * Validates that 4 positions form a valid quadrilateral where all 4 angles are 90°.
     */
    private boolean isValidQuadrilateral(List<BlockPos> p) {
        if (p.size() != 4) return false;
        return isRightAngle(p.get(3), p.get(0), p.get(1))
                && isRightAngle(p.get(0), p.get(1), p.get(2))
                && isRightAngle(p.get(1), p.get(2), p.get(3))
                && isRightAngle(p.get(2), p.get(3), p.get(0));
    }

    // ── Session helpers ───────────────────────────────────────────────────────

    private List<BlockPos> findSessionStakes(ServerWorld world, PlotData plot, UUID ownerId) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos center = plot.getCenter();
        int r = plot.getSize().radius + 10;

        // Search in an area around the plot center for PlotStakeBlockEntity owned by this player
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -10; dy <= 10; dy++) {
                    BlockPos candidate = center.add(dx, dy, dz);
                    if (world.getBlockState(candidate).getBlock() instanceof PlotStakeBlock) {
                        PlotStakeBlockEntity be = getBlockEntity(world, candidate);
                        if (be != null && ownerId.equals(be.getOwnerId())
                                && (be.getSubdivisionName() == null || be.getSubdivisionName().isEmpty())
                                && plot.getCenter().equals(be.getPlotCenter())) {
                            result.add(candidate);
                        }
                    }
                }
            }
        }
        // Sort by stake index
        result.sort((a, b) -> {
            PlotStakeBlockEntity ba = getBlockEntity(world, a);
            PlotStakeBlockEntity bb = getBlockEntity(world, b);
            int ia = ba != null ? ba.getStakeIndex() : 0;
            int ib = bb != null ? bb.getStakeIndex() : 0;
            return Integer.compare(ia, ib);
        });
        return result;
    }

    private List<BlockPos> sortByIndex(ServerWorld world, List<BlockPos> positions) {
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort((a, b) -> {
            PlotStakeBlockEntity ba = getBlockEntity(world, a);
            PlotStakeBlockEntity bb = getBlockEntity(world, b);
            int ia = ba != null ? ba.getStakeIndex() : 0;
            int ib = bb != null ? bb.getStakeIndex() : 0;
            return Integer.compare(ia, ib);
        });
        return sorted;
    }

    private UUID getSessionId(ServerWorld world, BlockPos pos) {
        PlotStakeBlockEntity be = getBlockEntity(world, pos);
        return be != null ? be.getSessionId() : null;
    }

    private void removeSiblingStakes(ServerWorld world, PlotData plot, UUID sessionId,
                                      BlockPos except, ServerPlayerEntity player) {
        if (sessionId == null) return;
        BlockPos center = plot.getCenter();
        int r = plot.getSize().radius + 10;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -10; dy <= 10; dy++) {
                    BlockPos candidate = center.add(dx, dy, dz);
                    if (candidate.equals(except)) continue;
                    if (world.getBlockState(candidate).getBlock() instanceof PlotStakeBlock) {
                        PlotStakeBlockEntity be = getBlockEntity(world, candidate);
                        if (be != null && sessionId.equals(be.getSessionId())) {
                            world.breakBlock(candidate, false);
                        }
                    }
                }
            }
        }
    }

    private String nextSubName(PlotData plot) {
        int i = 1;
        while (plot.getSubdivision("Zona " + i) != null) i++;
        return "Zona " + i;
    }

    private PlotStakeBlockEntity getBlockEntity(World world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof PlotStakeBlockEntity be) return be;
        return null;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
