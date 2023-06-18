package nourl.mythicmetals.misc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wispforest.owo.util.ReflectionUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import nourl.mythicmetals.armor.ArmorSet;
import nourl.mythicmetals.armor.MythicArmor;
import nourl.mythicmetals.config.MythicOreConfigs;
import nourl.mythicmetals.config.OreConfig;
import org.apache.commons.lang3.mutable.MutableInt;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class MythicCommands {

    private MythicCommands() {
    }

    public static void register() {
        // Dump Ore Config values
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccessdedicated, environment) -> {
            dispatcher.register(CommandManager.literal("mythicmetals")
                // TODO - Add new command for grabbing the data-generated ore features, and create a datapack skeleton
                .requires(source -> source.hasPermissionLevel(1))
                // TODO - Make this useful command more useful for current use-cases:
                // TODO -- Allow operator to get range for a specific OreConfig
                // TODO -- Allow dumping output in a spreadsheet friendly format
                // TODO -- Allow dumping output in a way that is friendly for the Wiki
                .then(CommandManager.literal("range")
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests(MythicCommands::dumpType)
                        .executes(context -> {
                            if (StringArgumentType.getString(context, "type").equals("console")) {
                                ReflectionUtils.iterateAccessibleStaticFields(MythicOreConfigs.class, OreConfig.class, (feature, name, field) -> {
                                    if (!feature.offset && !feature.trapezoid) {
                                        context.getSource().sendFeedback(() -> Text.literal(
                                            name.toUpperCase(Locale.ROOT)
                                                + " has the range between "
                                                + feature.bottom
                                                + " to "
                                                + feature.top
                                                + ", with a discard chance of "
                                                + feature.discardChance * 100 + "%"), false);
                                    }
                                    if (feature.offset) {
                                        context.getSource().sendFeedback(() -> Text.literal(
                                            name.toUpperCase(Locale.ROOT)
                                                + " has the range between "
                                                + feature.bottom
                                                + "(offset) to "
                                                + feature.top
                                                + ", with a discard chance of "
                                                + feature.discardChance * 100 + "%"), false);
                                    }
                                    if (feature.trapezoid) {
                                        context.getSource().sendFeedback(() -> Text.literal(
                                            name.toUpperCase(Locale.ROOT)
                                                + " has a triangle range between "
                                                + feature.bottom
                                                + " to "
                                                + feature.top
                                                + ", where the sweet spot is at Y = "
                                                + ((feature.bottom + feature.top) / 2)
                                                + " with a discard chance of "
                                                + feature.discardChance * 100 + "%"), false);
                                    }

                                });
                                return 1;
                            }
                            return 1;

                        })
                    )
                )
                .then(CommandManager.literal("armor-stand")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("material", StringArgumentType.word())
                        .suggests(MythicCommands::armorMaterial)
                        .then(CommandManager.argument("trim_pattern", StringArgumentType.word())
                            .suggests(MythicCommands::trimTypes)
                            .executes(context -> {
                                // TODO -- Make armor trims truly optional, instead of providing "none"
                                // All of the armor at once, not recommended due to the sheer density of them
                                String material = StringArgumentType.getString(context, "material");
                                String trimQuery = StringArgumentType.getString(context, "trim_pattern");
                                var armorTrims = new ArrayList<ArmorTrim>();
                                var world = context.getSource().getWorld();
                                var pos = context.getSource().getPosition();

                                if (trimQuery.equals("none")) {
                                    if (material.equals("all")) {
                                        MutableInt mutX = new MutableInt(pos.x);
                                        MutableInt count = new MutableInt(0);
                                        MythicArmor.ARMOR_MAP.forEach((s, armorSet) -> {
                                            summonArmorStandWithTrim(world, null, armorSet, mutX.getAndIncrement(), 0);
                                            count.increment();
                                        });
                                        context.getSource().sendFeedback(() -> Text.literal("Summoned %d armorstands".formatted(count.getValue())), true);
                                    } else {
                                        summonArmorStandWithTrim(world, null, MythicArmor.ARMOR_MAP.get(material), 0, 0);
                                        context.getSource().sendFeedback(() -> Text.literal("Summoned armorstands"), true);
                                    }
                                    return 1;
                                }

                                if (material.equals("all")) {
                                    if (MythicArmor.ARMOR_MAP.isEmpty()) {
                                        context.getSource().sendFeedback(() -> Text.literal("Unable to summon. Somehow the armor map is empty..."), false);
                                        return 1; // "how did this happen?" "a long time ago, actually never..."
                                    }

                                    if (trimQuery.equals("all")) {
                                        armorTrims.addAll(getAllArmorTrims(world)
                                            .stream()
                                            .toList());
                                    }
                                    else if (getAllTrimPatternStrs(world).contains(trimQuery)) {
                                        armorTrims.addAll(getAllArmorTrims(world).stream().filter(trim -> trim.getPattern().value().assetId().getPath().equals(trimQuery)).toList());
                                    }

                                    MutableInt mutX = new MutableInt(pos.x);
                                    MutableInt mutZ = new MutableInt(pos.z);
                                    MutableInt count = new MutableInt(0);

                                    ArrayList<ArmorTrim> finalArmorTrims = armorTrims;
                                    MythicArmor.ARMOR_MAP.forEach((s, armorSet) -> {
                                        finalArmorTrims.forEach(armorTrim -> {
                                            summonArmorStandWithTrim(world, armorTrim, armorSet, mutX.getAndIncrement(), mutZ.getValue());
                                            count.increment();
                                        });
                                        mutZ.increment();
                                        mutX.setValue(0);
                                    });
                                    context.getSource().sendFeedback(() -> Text.literal("Summoned %d armorstands with trims".formatted(count.getValue())), true);

                                    return 1;
                                } else if (MythicArmor.ARMOR_MAP.get(material) != null) {
                                    if (trimQuery.equals("all")) {
                                        armorTrims = getAllArmorTrims(world);
                                    } else {
                                        var trims = getAllArmorTrims(world).stream().filter(trim -> trim.getPattern().value().assetId().getPath().equals(trimQuery)).toList();
                                        armorTrims.addAll(trims);
                                    }

                                    // Split the armor stands into groups using these offsets
                                    int splitPoint = armorTrims.size() / world.getRegistryManager().get(RegistryKeys.TRIM_MATERIAL).size();

                                    // flip-flop
                                    int xOffset = 0;
                                    int zOffset = 0;
                                    int count = 0;

                                    for (int i = 0; i < armorTrims.size(); i++) {
                                        if (i % splitPoint == 0) {
                                            xOffset += 2;
                                            zOffset = 0;
                                        }
                                        var armorSet = MythicArmor.ARMOR_MAP.get(material);
                                        summonArmorStandWithTrim(world, armorTrims.get(i), armorSet, (int) pos.x + xOffset, (int) pos.z + zOffset);
                                        count++;
                                        zOffset += 2;
                                    }
                                    String feedback = "Summoned %d armorstands".formatted(count);
                                    context.getSource().sendFeedback(() -> Text.literal(feedback), true);
                                }
                                return 1;
                            })
                        )
                    )
                )
            );
        });

    }

    /**
     * Summons an armor stand with a specific armor set and trim on top of the world <br>
     * Create the {@link ArmorTrim} using a pattern from {@link net.minecraft.item.trim.ArmorTrimPatterns}
     * and a material from {@link net.minecraft.item.trim.ArmorTrimMaterials}
     *
     * @param world    The world where you want to summon the armor stand, needs to be on the server
     * @param trim     {@link ArmorTrim} you wish to use on the armor.
     * @param armorSet {@link ArmorSet} that you wish to equip on the armor stand.
     * @param x        x-coordinate where the armor stand should spawn
     * @param z        z-coordinate where the armor stand should spawn
     * @see ArmorTrim
     * @see MythicArmor
     * @see ArmorSet
     */
    public static void summonArmorStandWithTrim(World world, @Nullable ArmorTrim trim, ArmorSet armorSet, int x, int z) {
        if (world.isClient) return;

        var armorStand = new ArmorStandEntity(world, x, world.getTopY() - 50, z);
        armorSet.getArmorItems().forEach(armorItem -> {
            var armorStack = new ItemStack(armorItem);
            if (trim != null) {
                ArmorTrim.apply(world.getRegistryManager(), armorStack, trim);
            }
            armorStand.equipStack(armorItem.getSlotType(), armorStack);
        });
        world.spawnEntity(armorStand);
    }

    /**
     * @param world The world to get the DynamicRegistryManager from. This should only happen on the server
     * @return Returns all armor trims in a sorted ArrayList
     */
    public static ArrayList<ArmorTrim> getAllArmorTrims(World world) {
        if (world.isClient) return new ArrayList<>();

        var list = new ArrayList<ArmorTrim>();
        world.getRegistryManager().get(RegistryKeys.TRIM_MATERIAL).streamEntries().forEach(armorMaterialEntry -> {
            world.getRegistryManager().get(RegistryKeys.TRIM_PATTERN).streamEntries().forEach(armorTrimEntry -> {
                list.add(new ArmorTrim(armorMaterialEntry, armorTrimEntry));
            });
        });
        return list;
    }

    /**
     * @param world The world to get the DynamicRegistryManager from. This should only happen on the server
     * @return Returns all the trim patterns in the registry as a list of strings
     */
    public static ArrayList<String> getAllTrimPatternStrs(World world) {
        if (world.isClient) return new ArrayList<>();

        var list = new ArrayList<String>();
        world.getRegistryManager().get(RegistryKeys.TRIM_PATTERN).streamEntries().forEach(armorTrimEntry -> list.add(armorTrimEntry.value().assetId().getPath()));
        return list;
    }

    /**
     * Suggest which format/location to use when dumping all the ore configs in {@link MythicOreConfigs}
     */
    private static CompletableFuture<Suggestions> dumpType(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder suggestion) {
        suggestion.suggest("console");
        return suggestion.buildFuture();
    }

    /**
     * Suggests spawning armor stands with all the armor sets defined in {@link MythicArmor}
     * Includes one extra suggestion for "all"
     */
    private static CompletableFuture<Suggestions> armorMaterial(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder suggestion) {
        MythicArmor.ARMOR_MAP.forEach((s, armorSet) -> suggestion.suggest(s));
        suggestion.suggest("all");
        return suggestion.buildFuture();
    }

    /**
     * Suggests all the armor trim types available in the dynamic registry
     * Includes two extra suggestions for "all" and "none"
     */
    private static CompletableFuture<Suggestions> trimTypes(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder suggestion) {
        var list = new ArrayList<ArmorTrimPattern>();
        ctx.getSource().getRegistryManager().get(RegistryKeys.TRIM_PATTERN).streamEntries().forEach(armorTrimEntry -> list.add(armorTrimEntry.value()));
        list.forEach(trimPattern -> suggestion.suggest(trimPattern.assetId().getPath()));
        suggestion.suggest("all").suggest("none");
        return suggestion.buildFuture();
    }
}
