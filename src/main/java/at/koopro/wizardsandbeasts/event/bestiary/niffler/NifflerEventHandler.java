package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class NifflerEventHandler {

    private NifflerEventHandler() {}

    // Sighting, watching and meeting a Niffler in a fight are the shared bestiary handler's; a kill is an
    // encounter like any other. What only a Niffler has is its pouch.

    // ─── Bestiary: pouch open (STUDIED) ──────────────────────────────────────
    @SubscribeEvent
    public static void onPouchOpen(NifflerPouchOpenEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        BestiaryDataHelper.setTier(event.getPlayer(), NifflerEntity.BESTIARY_ID,
                at.koopro.wizardsandbeasts.bestiary.EncounterRule.onStudied(
                        BestiaryDataHelper.getTier(event.getPlayer(), NifflerEntity.BESTIARY_ID)));
    }

    // ─── Baby Niffler 30% co-spawn chance ────────────────────────────────────
    @SubscribeEvent
    public static void onNifflerJoinLevel(EntityJoinLevelEvent event) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return;
        if (!(event.getEntity() instanceof NifflerEntity niffler)) return;
        if (niffler.isBaby()) return;
        if (event.getLevel().isClientSide()) return;
        // Only a niffler new to the world brings young. A saved one joins the level again on every chunk load,
        // and rolling here for it bred a fresh litter each time the area was revisited.
        if (event.loadedFromDisk()) return;
        if (niffler.getRandom().nextFloat() >= 0.30f) return;

        // Spawn 1–3 baby Nifflers nearby
        var sl = (net.minecraft.server.level.ServerLevel) event.getLevel();
        int count = 1 + niffler.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity baby =
                    new at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity(
                            ModEntities.BABY_NIFFLER.get(), sl);
            double ox = (niffler.getRandom().nextDouble() - 0.5) * 4;
            double oz = (niffler.getRandom().nextDouble() - 0.5) * 4;
            baby.setPos(niffler.getX() + ox, niffler.getY(), niffler.getZ() + oz);
            sl.addFreshEntity(baby);
        }
    }
}
