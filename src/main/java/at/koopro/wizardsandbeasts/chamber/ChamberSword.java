package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorService;
import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorSword;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Who the Chamber gives the Sword of Gryffindor to, and when it gives nothing.
 *
 * <p>One sword per world (user ruling 2026-10-22, kept): the slayer of a Chamber's basilisk is given it only while
 * {@link GryffindorSword} records none out in the world. A Chamber cleared while the sword is out still clears, and
 * still credits everyone; it simply has no sword to give, and says so. The award is called once per Chamber, from the
 * basilisk's death, which a Chamber's heart accepts once ({@code SLAIN} is final), so neither a second killer nor a
 * relog can be given a second sword.
 */
@NullMarked
public final class ChamberSword {

    private static final String KEY = "chamber.wizards_and_beasts.";

    private ChamberSword() {}

    /** Whether the Chamber may give the sword now. */
    public static boolean mayAward(boolean artefactsOn, boolean swordInWorld) {
        return artefactsOn && !swordInWorld;
    }

    /** Gives {@code killer} the sword over the dais, or tells the credited why there is none. */
    public static @Nullable ItemEntity award(ServerLevel level, BlockPos heart, ServerPlayer killer,
                                             List<ServerPlayer> credited) {
        boolean out = GryffindorSword.get(level).present() != null;
        if (!mayAward(ModuleManager.isEnabled(Module.ARTEFACTS), out)) {
            if (out) {
                for (ServerPlayer player : credited) {
                    player.sendSystemMessage(Component.translatable(KEY + "sword_out"));
                }
            }
            return null;
        }
        ItemEntity sword = GryffindorService.presentInChamber(level, heart.getX() + 0.5, heart.getY() + 1.6,
                heart.getZ() + 0.5, killer);
        ChamberQuestService.reach(killer, ChamberStage.SWORD_EARNED);
        return sword;
    }
}
