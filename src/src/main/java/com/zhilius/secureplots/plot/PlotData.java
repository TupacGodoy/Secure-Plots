package com.zhilius.secureplots.plot;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PlotData {

    public enum Role {
        OWNER, ADMIN, MEMBER, VISITOR
    }

    private UUID ownerId;
    private String ownerName;
    private BlockPos center;
    private PlotSize size;
    private boolean hasRank;

    // Days since placed (stored in ticks)
    private long placedAtTick;

    // Members: UUID -> Role
    private Map<UUID, Role> members = new HashMap<>();
    private Map<UUID, String> memberNames = new HashMap<>();

    // Name of the plot
    private String plotName;

    public PlotData(UUID ownerId, String ownerName, BlockPos center, PlotSize size, long currentTick) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.center = center;
        this.size = size;
        this.placedAtTick = currentTick;
        this.plotName = ownerName + "'s Plot";
        this.hasRank = false;
    }

    public PlotData() {}

    // --- Getters / Setters ---

    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public BlockPos getCenter() { return center; }
    public PlotSize getSize() { return size; }
    public void setSize(PlotSize size) { this.size = size; }
    public boolean hasRank() { return hasRank; }
    public void setHasRank(boolean hasRank) { this.hasRank = hasRank; }
    public String getPlotName() { return plotName; }
    public void setPlotName(String plotName) { this.plotName = plotName; }
    public long getPlacedAtTick() { return placedAtTick; }
    public Map<UUID, Role> getMembers() { return members; }

    public void addMember(UUID uuid, String name, Role role) {
        members.put(uuid, role);
        memberNames.put(uuid, name);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberNames.remove(uuid);
    }

    public Role getRoleOf(UUID uuid) {
        if (uuid.equals(ownerId)) return Role.OWNER;
        return members.getOrDefault(uuid, Role.VISITOR);
    }

    public boolean canBuild(UUID uuid) {
        Role role = getRoleOf(uuid);
        return role == Role.OWNER || role == Role.ADMIN || role == Role.MEMBER;
    }

    public boolean isExpired(long currentTick) {
        if (hasRank) return false;
        long ticksAlive = currentTick - placedAtTick;
        long daysAlive = ticksAlive / 24000L;
        long maxDays = 25L + (5L * size.tier);
        return daysAlive > maxDays;
    }

    public long getDaysRemaining(long currentTick) {
        if (hasRank) return Long.MAX_VALUE;
        long ticksAlive = currentTick - placedAtTick;
        long daysAlive = ticksAlive / 24000L;
        long maxDays = 25L + (5L * size.tier);
        return Math.max(0, maxDays - daysAlive);
    }

    public String getMemberName(UUID uuid) {
        return memberNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    // --- NBT Serialization ---

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ownerId", ownerId.toString());
        nbt.putString("ownerName", ownerName);
        nbt.putInt("cx", center.getX());
        nbt.putInt("cy", center.getY());
        nbt.putInt("cz", center.getZ());
        nbt.putInt("sizeTier", size.tier);
        nbt.putBoolean("hasRank", hasRank);
        nbt.putLong("placedAt", placedAtTick);
        nbt.putString("plotName", plotName);

        NbtList membersList = new NbtList();
        for (Map.Entry<UUID, Role> entry : members.entrySet()) {
            NbtCompound m = new NbtCompound();
            m.putString("uuid", entry.getKey().toString());
            m.putString("role", entry.getValue().name());
            m.putString("name", memberNames.getOrDefault(entry.getKey(), "Unknown"));
            membersList.add(m);
        }
        nbt.put("members", membersList);

        return nbt;
    }

    public static PlotData fromNbt(NbtCompound nbt) {
        PlotData data = new PlotData();
        data.ownerId = UUID.fromString(nbt.getString("ownerId"));
        data.ownerName = nbt.getString("ownerName");
        data.center = new BlockPos(nbt.getInt("cx"), nbt.getInt("cy"), nbt.getInt("cz"));
        data.size = PlotSize.fromTier(nbt.getInt("sizeTier"));
        data.hasRank = nbt.getBoolean("hasRank");
        data.placedAtTick = nbt.getLong("placedAt");
        data.plotName = nbt.getString("plotName");

        NbtList membersList = nbt.getList("members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            NbtCompound m = membersList.getCompound(i);
            UUID uuid = UUID.fromString(m.getString("uuid"));
            Role role = Role.valueOf(m.getString("role"));
            String name = m.getString("name");
            data.members.put(uuid, role);
            data.memberNames.put(uuid, name);
        }

        return data;
    }
}
