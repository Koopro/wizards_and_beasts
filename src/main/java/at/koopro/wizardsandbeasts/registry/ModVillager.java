package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public final class ModVillager {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, WizardsAndBeastsMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, WizardsAndBeastsMod.MODID);

    public static final ResourceKey<PoiType> OLLIVANDERS_BENCH_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ollivanders_bench"));

    public static final DeferredHolder<PoiType, PoiType> OLLIVANDERS_BENCH = POI_TYPES.register("ollivanders_bench", () -> {
        ImmutableSet.Builder<BlockState> states = ImmutableSet.builder();
        for (BlockState state : ModBlocks.WANDMAKERS_BENCH.get().getStateDefinition().getPossibleStates()) {
            states.add(state);
        }
        return new PoiType(states.build(), 1, 1);
    });

    public static final DeferredHolder<VillagerProfession, VillagerProfession> WANDMAKER =
            PROFESSIONS.register("wandmaker", () -> new VillagerProfession(
                    Component.translatable("entity.wizards_and_beasts.villager.wandmaker"),
                    h -> h.is(OLLIVANDERS_BENCH_KEY),
                    h -> h.is(OLLIVANDERS_BENCH_KEY),
                    ImmutableSet.of(
                            WandItemRegistry.PHOENIX_FEATHER.get(),
                            WandItemRegistry.DRAGON_HEARTSTRING.get(),
                            WandItemRegistry.UNICORN_HAIR.get(),
                            WandItemRegistry.WAND_BLANK.get()),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    private ModVillager() {
    }
}
