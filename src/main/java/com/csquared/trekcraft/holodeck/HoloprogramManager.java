package com.csquared.trekcraft.holodeck;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for saving and loading holoprograms using Minecraft's StructureTemplate.
 * Uses Create-compatible NBT format for bidirectional compatibility.
 *
 * File format:
 * - Extension: .nbt
 * - Compression: GZIP
 * - Storage: .minecraft/schematics/ (game directory, not world-specific)
 *
 * This allows holoprograms to appear in Create's schematic list and vice versa.
 */
public class HoloprogramManager {

    private static final String SCHEMATICS_DIR = "schematics";
    private static final String HOLOPROGRAM_PREFIX = "holoprogram_";

    /**
     * Result codes for holoprogram load operations.
     */
    public enum LoadResult {
        SUCCESS,
        NOT_FOUND,
        TOO_LARGE,
        READ_ERROR
    }

    /**
     * Detailed result of a holoprogram load operation.
     */
    public record LoadResultDetails(
            LoadResult result,
            @Nullable Vec3i schematicSize,
            @Nullable Vec3i interiorSize
    ) {
        public static LoadResultDetails success() {
            return new LoadResultDetails(LoadResult.SUCCESS, null, null);
        }

        public static LoadResultDetails notFound() {
            return new LoadResultDetails(LoadResult.NOT_FOUND, null, null);
        }

        public static LoadResultDetails tooLarge(Vec3i schematicSize, Vec3i interiorSize) {
            return new LoadResultDetails(LoadResult.TOO_LARGE, schematicSize, interiorSize);
        }

        public static LoadResultDetails readError() {
            return new LoadResultDetails(LoadResult.READ_ERROR, null, null);
        }
    }

    /**
     * Get the schematics directory path.
     */
    private static Path getSchematicsDir() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path dir = gameDir.resolve(SCHEMATICS_DIR);
        TrekCraftMod.LOGGER.info("GAMEDIR: {}", gameDir.toAbsolutePath());
        TrekCraftMod.LOGGER.info("Schematics dir: {}", dir.toAbsolutePath());
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                TrekCraftMod.LOGGER.info("Created schematics directory: {}", dir.toAbsolutePath());
            } catch (IOException e) {
                TrekCraftMod.LOGGER.error("Failed to create schematics directory", e);
            }
        }
        return dir;
    }

    /**
     * Result of a save operation, containing success status and NBT data for syncing.
     */
    public record SaveResult(boolean success, @Nullable CompoundTag nbtData) {
        public static SaveResult failure() {
            return new SaveResult(false, null);
        }

        public static SaveResult success(CompoundTag nbt) {
            return new SaveResult(true, nbt);
        }
    }

    /**
     * Save the contents of a holodeck interior as a holoprogram.
     *
     * @param level The server level
     * @param name The holoprogram name
     * @param min The minimum corner of the interior
     * @param max The maximum corner of the interior
     * @return SaveResult with success status and NBT data for client sync
     */
    public static SaveResult save(ServerLevel level, String name, BlockPos min, BlockPos max) {
        try {
            // Calculate size
            Vec3i size = new Vec3i(
                max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1
            );

            // Create structure template
            StructureTemplate template = new StructureTemplate();

            // Fill template from world
            // Use AIR instead of STRUCTURE_VOID for Create compatibility
            template.fillFromWorld(level, min, size, false, Blocks.AIR);

            // Save to NBT
            CompoundTag nbt = template.save(new CompoundTag());

            // Post-process for Create compatibility
            // Replace any structure_void in palette with air
            replaceStructureVoidWithAir(nbt);

            // Write to file
            String fileName = sanitizeFileName(name) + ".nbt";
            Path filePath = getSchematicsDir().resolve(fileName);

            NbtIo.writeCompressed(nbt, filePath);

            TrekCraftMod.LOGGER.info("Saved holoprogram '{}' to {}", name, filePath);
            return SaveResult.success(nbt);

        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to save holoprogram '{}'", name, e);
            return SaveResult.failure();
        }
    }

    /**
     * Save schematic data locally (client-side).
     * Used to sync holoprograms from server to client's local schematics folder.
     *
     * @param name The holoprogram name
     * @param nbt The schematic NBT data
     * @return true if save was successful
     */
    public static boolean saveLocal(String name, CompoundTag nbt) {
        try {
            String fileName = sanitizeFileName(name) + ".nbt";
            Path filePath = getSchematicsDir().resolve(fileName);

            NbtIo.writeCompressed(nbt, filePath);

            TrekCraftMod.LOGGER.info("Saved holoprogram locally '{}' to {}", name, filePath);
            return true;

        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to save holoprogram locally '{}'", name, e);
            return false;
        }
    }

    /**
     * Load a holoprogram into a holodeck interior.
     *
     * @param level The server level
     * @param name The holoprogram name
     * @param origin The position to place the structure (interior min corner)
     * @param interiorSize The size of the holodeck interior for validation
     * @return LoadResultDetails with status and size information
     */
    public static LoadResultDetails load(ServerLevel level, String name, BlockPos origin, Vec3i interiorSize) {
        try {
            String fileName = sanitizeFileName(name) + ".nbt";
            Path filePath = getSchematicsDir().resolve(fileName);

            if (!Files.exists(filePath)) {
                TrekCraftMod.LOGGER.warn("Holoprogram '{}' not found at {}", name, filePath);
                return LoadResultDetails.notFound();
            }

            // Read NBT
            CompoundTag nbt = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());

            // Create structure template and load
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK.asLookup(), nbt);

            // Get schematic size and validate against interior
            Vec3i schematicSize = template.getSize();
            if (schematicSize.getX() > interiorSize.getX() ||
                schematicSize.getY() > interiorSize.getY() ||
                schematicSize.getZ() > interiorSize.getZ()) {
                TrekCraftMod.LOGGER.warn("Holoprogram '{}' too large ({}x{}x{}) for holodeck ({}x{}x{})",
                        name,
                        schematicSize.getX(), schematicSize.getY(), schematicSize.getZ(),
                        interiorSize.getX(), interiorSize.getY(), interiorSize.getZ());
                return LoadResultDetails.tooLarge(schematicSize, interiorSize);
            }

            // Create placement settings
            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

            // Place in world
            // The 2 flag means UPDATE_CLIENTS
            template.placeInWorld(level, origin, origin, settings, RandomSource.create(), 2);

            TrekCraftMod.LOGGER.info("Loaded holoprogram '{}' at {}", name, origin);
            return LoadResultDetails.success();

        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to load holoprogram '{}'", name, e);
            return LoadResultDetails.readError();
        }
    }

    /**
     * List all available holoprograms.
     *
     * @return List of holoprogram names (without .nbt extension)
     */
    public static List<String> listHoloprograms() {
        List<String> holoprograms = new ArrayList<>();
        Path dir = getSchematicsDir();

        if (!Files.exists(dir)) {
            return holoprograms;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                // Remove .nbt extension
                String name = fileName.substring(0, fileName.length() - 4);
                holoprograms.add(name);
            }
        } catch (IOException e) {
            TrekCraftMod.LOGGER.error("Failed to list holoprograms", e);
        }

        holoprograms.sort(String::compareToIgnoreCase);
        return holoprograms;
    }

    /**
     * Delete a holoprogram.
     *
     * @param name The holoprogram name
     * @return true if deletion was successful
     */
    public static boolean delete(String name) {
        try {
            String fileName = sanitizeFileName(name) + ".nbt";
            Path filePath = getSchematicsDir().resolve(fileName);

            if (!Files.exists(filePath)) {
                TrekCraftMod.LOGGER.warn("Holoprogram '{}' not found for deletion", name);
                return false;
            }

            Files.delete(filePath);
            TrekCraftMod.LOGGER.info("Deleted holoprogram '{}'", name);
            return true;

        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to delete holoprogram '{}'", name, e);
            return false;
        }
    }

    /**
     * Check if a holoprogram exists.
     *
     * @param name The holoprogram name
     * @return true if the holoprogram exists
     */
    public static boolean exists(String name) {
        String fileName = sanitizeFileName(name) + ".nbt";
        Path filePath = getSchematicsDir().resolve(fileName);
        return Files.exists(filePath);
    }

    /**
     * Replace structure_void blocks in the palette with air for Create compatibility.
     * Create uses air to represent empty space, not structure_void.
     */
    private static void replaceStructureVoidWithAir(CompoundTag nbt) {
        if (!nbt.contains("palette", Tag.TAG_LIST)) return;

        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag block = palette.getCompound(i);
            if (block.getString("Name").equals("minecraft:structure_void")) {
                block.putString("Name", "minecraft:air");
                // Remove any properties if present
                if (block.contains("Properties")) {
                    block.remove("Properties");
                }
            }
        }
    }

    /**
     * Sanitize a file name to prevent path traversal and invalid characters.
     */
    private static String sanitizeFileName(String name) {
        // Remove path separators and other dangerous characters
        String sanitized = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        // Limit length
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        // Ensure not empty
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
        }
        return sanitized;
    }
}
