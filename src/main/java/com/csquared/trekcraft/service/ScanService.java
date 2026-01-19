package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.network.ScanResultPayload;
import com.csquared.trekcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class ScanService {
    public static final TagKey<Block> SCAN_ORES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "scan_ores"));

    private static final int SCAN_RANGE = 10;
    private static final long SCAN_COOLDOWN_TICKS = 100; // 5 seconds

    // Per-player cooldown tracking
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    public enum ScanResult {
        SUCCESS,
        ON_COOLDOWN,
        NO_SLIP,
        NOT_OVERWORLD
    }

    public static ScanResult performScan(ServerPlayer player) {
        // Check overworld
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            return ScanResult.NOT_OVERWORLD;
        }

        // Check cooldown
        long currentTime = player.level().getGameTime();
        Long lastScan = cooldowns.get(player.getUUID());
        if (lastScan != null && currentTime - lastScan < SCAN_COOLDOWN_TICKS) {
            return ScanResult.ON_COOLDOWN;
        }

        // Check for slip payment (unless creative)
        if (!player.isCreative()) {
            if (!consumeSlip(player)) {
                return ScanResult.NO_SLIP;
            }
        }

        // Set cooldown
        cooldowns.put(player.getUUID(), currentTime);

        // Perform the scan
        executeScan(player);
        return ScanResult.SUCCESS;
    }

    private static boolean consumeSlip(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.LATINUM_SLIP.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void executeScan(ServerPlayer player) {
        Level level = player.level();
        Direction facing = player.getDirection(); // Horizontal facing

        // Calculate scan volume based on facing
        BlockPos playerPos = player.blockPosition();
        BlockPos[] scanBounds = calculateScanBounds(playerPos, facing);
        BlockPos minPos = scanBounds[0];
        BlockPos maxPos = scanBounds[1];

        // Collect interesting blocks with relative coordinates
        List<ScanResultPayload.ScannedBlock> interestingBlocks = new ArrayList<>();

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(checkPos);

                    // Check if block is interesting (ore, spawner, or container)
                    boolean isInteresting = false;
                    String blockId = null;

                    if (state.is(SCAN_ORES) || isCommonOre(state)) {
                        isInteresting = true;
                        blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    } else if (state.is(Blocks.SPAWNER)) {
                        isInteresting = true;
                        blockId = "minecraft:spawner";
                    } else {
                        BlockEntity be = level.getBlockEntity(checkPos);
                        if (be instanceof Container) {
                            isInteresting = true;
                            blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        }
                    }

                    if (isInteresting && blockId != null) {
                        // Calculate relative position (0-9 in each dimension)
                        int relX = x - minPos.getX();
                        int relY = y - minPos.getY();
                        int relZ = z - minPos.getZ();

                        interestingBlocks.add(new ScanResultPayload.ScannedBlock(relX, relY, relZ, blockId));
                    }
                }
            }
        }

        // Send scan results to client
        ScanResultPayload payload = new ScanResultPayload(facing.getName().toUpperCase(), interestingBlocks);
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static BlockPos[] calculateScanBounds(BlockPos playerPos, Direction facing) {
        int halfWidth = SCAN_RANGE / 2;

        // Calculate offsets based on facing
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();

        // Right direction (perpendicular)
        int rightX = -forwardZ;
        int rightZ = forwardX;

        // Start position (1 block in front, centered left-right, centered vertically)
        int startX = playerPos.getX() + forwardX - rightX * halfWidth;
        int startZ = playerPos.getZ() + forwardZ - rightZ * halfWidth;
        int startY = playerPos.getY() - halfWidth;

        // End position
        int endX = startX + forwardX * (SCAN_RANGE - 1) + rightX * (SCAN_RANGE - 1);
        int endZ = startZ + forwardZ * (SCAN_RANGE - 1) + rightZ * (SCAN_RANGE - 1);
        int endY = startY + SCAN_RANGE - 1;

        BlockPos minPos = new BlockPos(
                Math.min(startX, endX),
                startY,
                Math.min(startZ, endZ)
        );
        BlockPos maxPos = new BlockPos(
                Math.max(startX, endX),
                endY,
                Math.max(startZ, endZ)
        );

        return new BlockPos[]{minPos, maxPos};
    }

    private static String getQuadrant(BlockPos playerPos, BlockPos targetPos, Direction facing) {
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();

        // Calculate forward and right distances
        int dx = targetPos.getX() - playerPos.getX();
        int dz = targetPos.getZ() - playerPos.getZ();

        // Forward distance
        int forwardDist = dx * forwardX + dz * forwardZ;

        // Right distance (perpendicular)
        int rightDist = dx * (-forwardZ) + dz * forwardX;

        boolean isNear = forwardDist <= SCAN_RANGE / 2;
        boolean isLeft = rightDist < 0;

        if (!isNear && isLeft) return "Gamma (Far-Left)";
        if (!isNear && !isLeft) return "Delta (Far-Right)";
        if (isNear && isLeft) return "Alpha (Near-Left)";
        return "Beta (Near-Right)";
    }

    private static boolean isCommonOre(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE ||
               block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE ||
               block == Blocks.ANCIENT_DEBRIS;
    }

    private static String getBlockDisplayName(BlockState state) {
        // Simplify ore names
        String name = state.getBlock().getName().getString();
        return name.replace(" Ore", "").replace("Deepslate ", "");
    }

    public static long getCooldownRemaining(ServerPlayer player) {
        Long lastScan = cooldowns.get(player.getUUID());
        if (lastScan == null) return 0;

        long elapsed = player.level().getGameTime() - lastScan;
        long remaining = SCAN_COOLDOWN_TICKS - elapsed;
        return Math.max(0, remaining);
    }

    public static String getResultMessage(ScanResult result) {
        return switch (result) {
            case SUCCESS -> "Scan complete.";
            case ON_COOLDOWN -> "Tricorder recalibrating. Please wait.";
            case NO_SLIP -> "Insufficient power. Scan requires 1 Latinum Slip.";
            case NOT_OVERWORLD -> "Scan restricted to Overworld coordinates only.";
        };
    }
}
