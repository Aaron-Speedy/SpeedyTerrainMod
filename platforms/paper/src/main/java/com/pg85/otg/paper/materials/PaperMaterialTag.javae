package com.pg85.otg.paper.materials;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.materials.LocalMaterialTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class PaperMaterialTag extends LocalMaterialTag {
    public static LocalMaterialTag ofString(String name) {
        // If otg: or no domain was supplied, try OTG tags.
        // If no domain was supplied, first try OTG tags.
        if (!name.contains(":") || name.startsWith(Constants.MOD_ID_SHORT + ":")) {
            Block[] blockTag = PaperMaterials.OTG_BLOCK_TAGS.get(name.trim().toLowerCase().replace(Constants.MOD_ID_SHORT + ":", ""));
            if (blockTag != null) {
                return new PaperMaterialTag(blockTag, Constants.MOD_ID_SHORT + ":" + name.trim().toLowerCase().replace(Constants.MOD_ID_SHORT + ":", ""));
            }
        }
        final ResourceLocation resourceLocation;
        resourceLocation = new ResourceLocation(name.trim().toLowerCase());
        TagKey<Block> blockTag = getBlockTagFromResourceLocation(resourceLocation);
        return blockTag == null ? null : new PaperMaterialTag(blockTag, resourceLocation.toString());
    }

    private final String name;
    private final TagKey<Block> blockTag;
    private final Block[] otgBlockTag;

    private PaperMaterialTag(TagKey<Block> blockTag, String name) {
        this.otgBlockTag = null;
        this.blockTag = blockTag;
        this.name = name;
    }

    private PaperMaterialTag(Block[] otgBlockTag, String name) {
        this.otgBlockTag = otgBlockTag;
        this.blockTag = null;
        this.name = name;
    }

    public boolean isOTGTag(Block block) {
        if (this.otgBlockTag != null) {
            for (Block otgTagBlock : this.otgBlockTag) {
                if (otgTagBlock == block) {
                    return true;
                }
            }
        }
        return false;
    }

    public TagKey<Block> getTag() {
        return this.blockTag;
    }

    public Block[] getOtgBlockTag() {
        if (otgBlockTag != null) {
            return otgBlockTag.clone();
        }
        return null;
    }

    @Override
    public String toString() {
        // TODO: Fetch the registry name from the tag object.
        return this.name;
    }

    // Adding this because I can't find a method to do lookup by name. Really weird that this doesn't exist.
    // It's scuffed but it's the best we got for now
    // -auth
    private static TagKey<Block> getBlockTagFromResourceLocation(ResourceLocation loc) {
        if (BlockTags.WOOL.location().equals(loc)) return BlockTags.WOOL;
        if (BlockTags.PLANKS.location().equals(loc)) return BlockTags.PLANKS;
        if (BlockTags.STONE_BRICKS.location().equals(loc)) return BlockTags.STONE_BRICKS;
        if (BlockTags.WOODEN_BUTTONS.location().equals(loc)) return BlockTags.WOODEN_BUTTONS;
        if (BlockTags.BUTTONS.location().equals(loc)) return BlockTags.BUTTONS;
        if (BlockTags.WOODEN_DOORS.location().equals(loc)) return BlockTags.WOODEN_DOORS;
        if (BlockTags.WOODEN_STAIRS.location().equals(loc)) return BlockTags.WOODEN_STAIRS;
        if (BlockTags.WOODEN_SLABS.location().equals(loc)) return BlockTags.WOODEN_SLABS;
        if (BlockTags.WOODEN_FENCES.location().equals(loc)) return BlockTags.WOODEN_FENCES;
        if (BlockTags.PRESSURE_PLATES.location().equals(loc)) return BlockTags.PRESSURE_PLATES;
        if (BlockTags.WOODEN_PRESSURE_PLATES.location().equals(loc)) return BlockTags.WOODEN_PRESSURE_PLATES;
        if (BlockTags.STONE_PRESSURE_PLATES.location().equals(loc)) return BlockTags.STONE_PRESSURE_PLATES;
        if (BlockTags.WOODEN_TRAPDOORS.location().equals(loc)) return BlockTags.WOODEN_TRAPDOORS;
        if (BlockTags.DOORS.location().equals(loc)) return BlockTags.DOORS;
        if (BlockTags.SAPLINGS.location().equals(loc)) return BlockTags.SAPLINGS;
        if (BlockTags.LOGS_THAT_BURN.location().equals(loc)) return BlockTags.LOGS_THAT_BURN;
        if (BlockTags.LOGS.location().equals(loc)) return BlockTags.LOGS;
        if (BlockTags.DARK_OAK_LOGS.location().equals(loc)) return BlockTags.DARK_OAK_LOGS;
        if (BlockTags.OAK_LOGS.location().equals(loc)) return BlockTags.OAK_LOGS;
        if (BlockTags.BIRCH_LOGS.location().equals(loc)) return BlockTags.BIRCH_LOGS;
        if (BlockTags.ACACIA_LOGS.location().equals(loc)) return BlockTags.ACACIA_LOGS;
        if (BlockTags.JUNGLE_LOGS.location().equals(loc)) return BlockTags.JUNGLE_LOGS;
        if (BlockTags.SPRUCE_LOGS.location().equals(loc)) return BlockTags.SPRUCE_LOGS;
        if (BlockTags.CRIMSON_STEMS.location().equals(loc)) return BlockTags.CRIMSON_STEMS;
        if (BlockTags.WARPED_STEMS.location().equals(loc)) return BlockTags.WARPED_STEMS;
        if (BlockTags.BANNERS.location().equals(loc)) return BlockTags.BANNERS;
        if (BlockTags.SAND.location().equals(loc)) return BlockTags.SAND;
        if (BlockTags.STAIRS.location().equals(loc)) return BlockTags.STAIRS;
        if (BlockTags.SLABS.location().equals(loc)) return BlockTags.SLABS;
        if (BlockTags.WALLS.location().equals(loc)) return BlockTags.WALLS;
        if (BlockTags.ANVIL.location().equals(loc)) return BlockTags.ANVIL;
        if (BlockTags.RAILS.location().equals(loc)) return BlockTags.RAILS;
        if (BlockTags.LEAVES.location().equals(loc)) return BlockTags.LEAVES;
        if (BlockTags.TRAPDOORS.location().equals(loc)) return BlockTags.TRAPDOORS;
        if (BlockTags.SMALL_FLOWERS.location().equals(loc)) return BlockTags.SMALL_FLOWERS;
        if (BlockTags.BEDS.location().equals(loc)) return BlockTags.BEDS;
        if (BlockTags.FENCES.location().equals(loc)) return BlockTags.FENCES;
        if (BlockTags.TALL_FLOWERS.location().equals(loc)) return BlockTags.TALL_FLOWERS;
        if (BlockTags.FLOWERS.location().equals(loc)) return BlockTags.FLOWERS;
        if (BlockTags.PIGLIN_REPELLENTS.location().equals(loc)) return BlockTags.PIGLIN_REPELLENTS;
        if (BlockTags.GOLD_ORES.location().equals(loc)) return BlockTags.GOLD_ORES;
        if (BlockTags.IRON_ORES.location().equals(loc)) return BlockTags.IRON_ORES;
        if (BlockTags.DIAMOND_ORES.location().equals(loc)) return BlockTags.DIAMOND_ORES;
        if (BlockTags.REDSTONE_ORES.location().equals(loc)) return BlockTags.REDSTONE_ORES;
        if (BlockTags.LAPIS_ORES.location().equals(loc)) return BlockTags.LAPIS_ORES;
        if (BlockTags.COAL_ORES.location().equals(loc)) return BlockTags.COAL_ORES;
        if (BlockTags.EMERALD_ORES.location().equals(loc)) return BlockTags.EMERALD_ORES;
        if (BlockTags.COPPER_ORES.location().equals(loc)) return BlockTags.COPPER_ORES;
        if (BlockTags.NON_FLAMMABLE_WOOD.location().equals(loc)) return BlockTags.NON_FLAMMABLE_WOOD;
        if (BlockTags.CANDLES.location().equals(loc)) return BlockTags.CANDLES;
        if (BlockTags.DIRT.location().equals(loc)) return BlockTags.DIRT;
        if (BlockTags.TERRACOTTA.location().equals(loc)) return BlockTags.TERRACOTTA;
        if (BlockTags.FLOWER_POTS.location().equals(loc)) return BlockTags.FLOWER_POTS;
        if (BlockTags.ENDERMAN_HOLDABLE.location().equals(loc)) return BlockTags.ENDERMAN_HOLDABLE;
        if (BlockTags.ICE.location().equals(loc)) return BlockTags.ICE;
        if (BlockTags.VALID_SPAWN.location().equals(loc)) return BlockTags.VALID_SPAWN;
        if (BlockTags.IMPERMEABLE.location().equals(loc)) return BlockTags.IMPERMEABLE;
        if (BlockTags.UNDERWATER_BONEMEALS.location().equals(loc)) return BlockTags.UNDERWATER_BONEMEALS;
        if (BlockTags.CORAL_BLOCKS.location().equals(loc)) return BlockTags.CORAL_BLOCKS;
        if (BlockTags.WALL_CORALS.location().equals(loc)) return BlockTags.WALL_CORALS;
        if (BlockTags.CORAL_PLANTS.location().equals(loc)) return BlockTags.CORAL_PLANTS;
        if (BlockTags.CORALS.location().equals(loc)) return BlockTags.CORALS;
        if (BlockTags.BAMBOO_PLANTABLE_ON.location().equals(loc)) return BlockTags.BAMBOO_PLANTABLE_ON;
        if (BlockTags.STANDING_SIGNS.location().equals(loc)) return BlockTags.STANDING_SIGNS;
        if (BlockTags.WALL_SIGNS.location().equals(loc)) return BlockTags.WALL_SIGNS;
        if (BlockTags.SIGNS.location().equals(loc)) return BlockTags.SIGNS;
        if (BlockTags.DRAGON_IMMUNE.location().equals(loc)) return BlockTags.DRAGON_IMMUNE;
        if (BlockTags.WITHER_IMMUNE.location().equals(loc)) return BlockTags.WITHER_IMMUNE;
        if (BlockTags.WITHER_SUMMON_BASE_BLOCKS.location().equals(loc)) return BlockTags.WITHER_SUMMON_BASE_BLOCKS;
        if (BlockTags.BEEHIVES.location().equals(loc)) return BlockTags.BEEHIVES;
        if (BlockTags.CROPS.location().equals(loc)) return BlockTags.CROPS;
        if (BlockTags.BEE_GROWABLES.location().equals(loc)) return BlockTags.BEE_GROWABLES;
        if (BlockTags.PORTALS.location().equals(loc)) return BlockTags.PORTALS;
        if (BlockTags.FIRE.location().equals(loc)) return BlockTags.FIRE;
        if (BlockTags.NYLIUM.location().equals(loc)) return BlockTags.NYLIUM;
        if (BlockTags.WART_BLOCKS.location().equals(loc)) return BlockTags.WART_BLOCKS;
        if (BlockTags.BEACON_BASE_BLOCKS.location().equals(loc)) return BlockTags.BEACON_BASE_BLOCKS;
        if (BlockTags.SOUL_SPEED_BLOCKS.location().equals(loc)) return BlockTags.SOUL_SPEED_BLOCKS;
        if (BlockTags.WALL_POST_OVERRIDE.location().equals(loc)) return BlockTags.WALL_POST_OVERRIDE;
        if (BlockTags.CLIMBABLE.location().equals(loc)) return BlockTags.CLIMBABLE;
        if (BlockTags.FALL_DAMAGE_RESETTING.location().equals(loc)) return BlockTags.FALL_DAMAGE_RESETTING;
        if (BlockTags.SHULKER_BOXES.location().equals(loc)) return BlockTags.SHULKER_BOXES;
        if (BlockTags.HOGLIN_REPELLENTS.location().equals(loc)) return BlockTags.HOGLIN_REPELLENTS;
        if (BlockTags.SOUL_FIRE_BASE_BLOCKS.location().equals(loc)) return BlockTags.SOUL_FIRE_BASE_BLOCKS;
        if (BlockTags.STRIDER_WARM_BLOCKS.location().equals(loc)) return BlockTags.STRIDER_WARM_BLOCKS;
        if (BlockTags.CAMPFIRES.location().equals(loc)) return BlockTags.CAMPFIRES;
        if (BlockTags.GUARDED_BY_PIGLINS.location().equals(loc)) return BlockTags.GUARDED_BY_PIGLINS;
        if (BlockTags.PREVENT_MOB_SPAWNING_INSIDE.location().equals(loc)) return BlockTags.PREVENT_MOB_SPAWNING_INSIDE;
        if (BlockTags.FENCE_GATES.location().equals(loc)) return BlockTags.FENCE_GATES;
        if (BlockTags.UNSTABLE_BOTTOM_CENTER.location().equals(loc)) return BlockTags.UNSTABLE_BOTTOM_CENTER;
        if (BlockTags.MUSHROOM_GROW_BLOCK.location().equals(loc)) return BlockTags.MUSHROOM_GROW_BLOCK;
        if (BlockTags.INFINIBURN_OVERWORLD.location().equals(loc)) return BlockTags.INFINIBURN_OVERWORLD;
        if (BlockTags.INFINIBURN_NETHER.location().equals(loc)) return BlockTags.INFINIBURN_NETHER;
        if (BlockTags.INFINIBURN_END.location().equals(loc)) return BlockTags.INFINIBURN_END;
        if (BlockTags.BASE_STONE_OVERWORLD.location().equals(loc)) return BlockTags.BASE_STONE_OVERWORLD;
        if (BlockTags.STONE_ORE_REPLACEABLES.location().equals(loc)) return BlockTags.STONE_ORE_REPLACEABLES;
        if (BlockTags.DEEPSLATE_ORE_REPLACEABLES.location().equals(loc)) return BlockTags.DEEPSLATE_ORE_REPLACEABLES;
        if (BlockTags.BASE_STONE_NETHER.location().equals(loc)) return BlockTags.BASE_STONE_NETHER;
        if (BlockTags.CANDLE_CAKES.location().equals(loc)) return BlockTags.CANDLE_CAKES;
        if (BlockTags.CAULDRONS.location().equals(loc)) return BlockTags.CAULDRONS;
        if (BlockTags.CRYSTAL_SOUND_BLOCKS.location().equals(loc)) return BlockTags.CRYSTAL_SOUND_BLOCKS;
        if (BlockTags.INSIDE_STEP_SOUND_BLOCKS.location().equals(loc)) return BlockTags.INSIDE_STEP_SOUND_BLOCKS;
        if (BlockTags.OCCLUDES_VIBRATION_SIGNALS.location().equals(loc)) return BlockTags.OCCLUDES_VIBRATION_SIGNALS;
        if (BlockTags.DRIPSTONE_REPLACEABLE.location().equals(loc)) return BlockTags.DRIPSTONE_REPLACEABLE;
        if (BlockTags.CAVE_VINES.location().equals(loc)) return BlockTags.CAVE_VINES;
        if (BlockTags.MOSS_REPLACEABLE.location().equals(loc)) return BlockTags.MOSS_REPLACEABLE;
        if (BlockTags.LUSH_GROUND_REPLACEABLE.location().equals(loc)) return BlockTags.LUSH_GROUND_REPLACEABLE;
        if (BlockTags.AZALEA_ROOT_REPLACEABLE.location().equals(loc)) return BlockTags.AZALEA_ROOT_REPLACEABLE;
        if (BlockTags.SMALL_DRIPLEAF_PLACEABLE.location().equals(loc)) return BlockTags.SMALL_DRIPLEAF_PLACEABLE;
        if (BlockTags.BIG_DRIPLEAF_PLACEABLE.location().equals(loc)) return BlockTags.BIG_DRIPLEAF_PLACEABLE;
        if (BlockTags.SNOW.location().equals(loc)) return BlockTags.SNOW;
        if (BlockTags.MINEABLE_WITH_AXE.location().equals(loc)) return BlockTags.MINEABLE_WITH_AXE;
        if (BlockTags.MINEABLE_WITH_HOE.location().equals(loc)) return BlockTags.MINEABLE_WITH_HOE;
        if (BlockTags.MINEABLE_WITH_PICKAXE.location().equals(loc)) return BlockTags.MINEABLE_WITH_PICKAXE;
        if (BlockTags.MINEABLE_WITH_SHOVEL.location().equals(loc)) return BlockTags.MINEABLE_WITH_SHOVEL;
        if (BlockTags.NEEDS_DIAMOND_TOOL.location().equals(loc)) return BlockTags.NEEDS_DIAMOND_TOOL;
        if (BlockTags.NEEDS_IRON_TOOL.location().equals(loc)) return BlockTags.NEEDS_IRON_TOOL;
        if (BlockTags.NEEDS_STONE_TOOL.location().equals(loc)) return BlockTags.NEEDS_STONE_TOOL;
        if (BlockTags.FEATURES_CANNOT_REPLACE.location().equals(loc)) return BlockTags.FEATURES_CANNOT_REPLACE;
        if (BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE.location().equals(loc))
            return BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE;
        if (BlockTags.GEODE_INVALID_BLOCKS.location().equals(loc)) return BlockTags.GEODE_INVALID_BLOCKS;
        if (BlockTags.ANIMALS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.ANIMALS_SPAWNABLE_ON;
        if (BlockTags.AXOLOTLS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.AXOLOTLS_SPAWNABLE_ON;
        if (BlockTags.GOATS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.GOATS_SPAWNABLE_ON;
        if (BlockTags.MOOSHROOMS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.MOOSHROOMS_SPAWNABLE_ON;
        if (BlockTags.PARROTS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.PARROTS_SPAWNABLE_ON;
        if (BlockTags.RABBITS_SPAWNABLE_ON.location().equals(loc)) return BlockTags.RABBITS_SPAWNABLE_ON;
        if (BlockTags.FOXES_SPAWNABLE_ON.location().equals(loc)) return BlockTags.FOXES_SPAWNABLE_ON;
        if (BlockTags.WOLVES_SPAWNABLE_ON.location().equals(loc)) return BlockTags.WOLVES_SPAWNABLE_ON;
        if (BlockTags.AZALEA_GROWS_ON.location().equals(loc)) return BlockTags.AZALEA_GROWS_ON;
        if (BlockTags.REPLACEABLE_PLANTS.location().equals(loc)) return BlockTags.REPLACEABLE_PLANTS;
        return null;
    }
}