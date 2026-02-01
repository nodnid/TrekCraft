package com.csquared.trekcraft.mission;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.StarfleetSavedData;
import com.csquared.trekcraft.mission.objectives.*;
import com.csquared.trekcraft.service.MissionService;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Creates tutorial missions for new players on world first load.
 */
public class TutorialMissions {

    /**
     * Generate tutorial missions if they haven't been generated yet.
     */
    public static void generateIfNeeded(ServerLevel level) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        if (data.areTutorialsGenerated()) {
            return;
        }

        TrekCraftMod.LOGGER.info("Generating tutorial missions...");

        // First Contact - Scan passive mobs
        MissionService.createSystemMission(
                level,
                "First Contact",
                "Use your tricorder to scan and catalog local wildlife. " +
                "This will help Starfleet understand the ecosystem of this new world.",
                new ScanObjective(
                        List.of(
                                "minecraft:cow",
                                "minecraft:pig",
                                "minecraft:sheep",
                                "minecraft:chicken",
                                "minecraft:horse",
                                "minecraft:donkey",
                                "minecraft:rabbit",
                                "minecraft:wolf",
                                "minecraft:fox"
                        ),
                        null,
                        10
                ),
                50, // XP reward
                StarfleetRank.CREWMAN,
                true // isTutorial
        );

        // Resource Survey - Gather basic resources
        MissionService.createSystemMission(
                level,
                "Resource Survey",
                "Starfleet needs resources for the colony. Gather iron, copper, and coal " +
                "to help establish our presence in this sector.",
                new GatherObjective("minecraft:raw_iron", 32),
                75,
                StarfleetRank.CREWMAN,
                true
        );

        // Hostile Territory - Kill hostile mobs
        MissionService.createSystemMission(
                level,
                "Hostile Territory",
                "The area is infested with hostile creatures. Eliminate threats to " +
                "secure our position and protect the crew.",
                new KillObjective(null, 20), // null = any hostile
                100,
                StarfleetRank.CREWMAN,
                true
        );

        // Sector Exploration - Visit multiple biomes
        MissionService.createSystemMission(
                level,
                "Sector Exploration",
                "Map the surrounding terrain by visiting different biomes. " +
                "This data is crucial for future colony expansion.",
                new ExploreObjective(null, 5), // null = any 5 biomes
                125,
                StarfleetRank.CREWMAN,
                true
        );

        // Transporter Resupply - Ongoing contribution mission
        MissionService.createSystemMission(
                level,
                "Transporter Resupply",
                "The transporter network requires latinum for operation. " +
                "Contribute latinum strips to keep the network running. " +
                "This is an ongoing mission - contribute anytime!",
                new ContributionObjective("trekcraft:latinum_strip", Integer.MAX_VALUE, 10),
                0, // No completion reward - XP is per-item
                StarfleetRank.CREWMAN,
                false // Not a tutorial - this is the ongoing resupply mission
        );

        data.setTutorialsGenerated(true);
        TrekCraftMod.LOGGER.info("Tutorial missions generated successfully.");
    }
}
