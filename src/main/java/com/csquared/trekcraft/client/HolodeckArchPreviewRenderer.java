package com.csquared.trekcraft.client;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders a ghost preview of the holodeck arch when the player is holding the controller item.
 */
@EventBusSubscriber(modid = TrekCraftMod.MODID, value = Dist.CLIENT)
public class HolodeckArchPreviewRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) return;

        // Check if player is holding the holodeck controller
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean holdingController = mainHand.is(ModItems.HOLODECK_CONTROLLER.get()) ||
                                    offHand.is(ModItems.HOLODECK_CONTROLLER.get());

        if (!holdingController) return;

        // Get where the player is looking
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos clickPos = blockHit.getBlockPos();
        Direction clickedFace = blockHit.getDirection();

        // If clicking on a solid block, offset to the adjacent position
        BlockState clickedState = level.getBlockState(clickPos);
        if (!clickedState.canBeReplaced()) {
            clickPos = clickPos.relative(clickedFace);
        }

        // Get player's horizontal facing direction
        Direction playerFacing = player.getDirection();
        Direction rightDir = playerFacing.getClockWise();

        // Calculate arch positions
        BlockPos[][] archPositions = calculateArchPositions(clickPos, rightDir);

        // Check if all positions are valid
        boolean allValid = validatePositions(level, archPositions);

        // Render the preview
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // Colors: green for valid, red for invalid
        float r = allValid ? 0.0f : 1.0f;
        float g = allValid ? 1.0f : 0.0f;
        float b = 0.0f;
        float a = 0.8f;

        // Render each block position in the arch
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                BlockPos pos = archPositions[row][col];

                // Skip door positions (row 0-1, col 1-2)
                if ((row == 0 || row == 1) && (col == 1 || col == 2)) {
                    continue;
                }

                // Determine block type for coloring
                boolean isController = (row == 1 && col == 0);
                float blockR = r;
                float blockG = g;
                float blockB = isController ? 0.5f : b; // Purple tint for controller

                renderBlockOutline(poseStack, buffer, pos, blockR, blockG, blockB, a);
            }
        }

        // Also render the door opening outline (in blue)
        for (int row = 0; row < 2; row++) {
            for (int col = 1; col <= 2; col++) {
                BlockPos pos = archPositions[row][col];
                renderBlockOutline(poseStack, buffer, pos, 0.3f, 0.3f, 1.0f, 0.5f);
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static BlockPos[][] calculateArchPositions(BlockPos clickPos, Direction rightDir) {
        BlockPos[][] positions = new BlockPos[3][4];

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                positions[row][col] = clickPos
                    .above(row)
                    .relative(rightDir, col);
            }
        }

        return positions;
    }

    private static boolean validatePositions(Level level, BlockPos[][] positions) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                BlockPos pos = positions[row][col];
                BlockState existing = level.getBlockState(pos);

                if (!existing.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void renderBlockOutline(PoseStack poseStack, VertexConsumer buffer,
                                           BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();

        Matrix4f matrix = poseStack.last().pose();

        // Draw all 12 edges of the cube
        // Bottom face edges
        drawLine(buffer, matrix, x, y, z, x + 1, y, z, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y, z, x + 1, y, z + 1, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y, z + 1, x, y, z + 1, r, g, b, a);
        drawLine(buffer, matrix, x, y, z + 1, x, y, z, r, g, b, a);

        // Top face edges
        drawLine(buffer, matrix, x, y + 1, z, x + 1, y + 1, z, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, a);
        drawLine(buffer, matrix, x, y + 1, z + 1, x, y + 1, z, r, g, b, a);

        // Vertical edges
        drawLine(buffer, matrix, x, y, z, x, y + 1, z, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y, z, x + 1, y + 1, z, r, g, b, a);
        drawLine(buffer, matrix, x + 1, y, z + 1, x + 1, y + 1, z + 1, r, g, b, a);
        drawLine(buffer, matrix, x, y, z + 1, x, y + 1, z + 1, r, g, b, a);
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        // Calculate normal for the line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz);
    }
}
