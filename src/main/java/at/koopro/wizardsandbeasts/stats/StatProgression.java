package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.stats.StatLevelUpS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place a stat point being earned becomes something the player can perceive.
 *
 * <p>Before this existed, earning a point was completely silent: {@code addTrainingProgress} wrote a
 * bigger number into an attachment, the sync payload carried it, and unless the character sheet
 * happened to be open at that instant nothing said so. A player landing their 307th spell had no way
 * to tell that the one before it had been worth anything.
 *
 * <p>What crosses the wire is the stat and the two numbers, never a sentence — see
 * {@link StatLevelUpS2CPayload}. The presentation (toast, sound, particles, the row flashing on an
 * open sheet) is entirely the client's, which is both how this mod already handles cast rejections
 * and the only way the wording stays translatable.
 *
 * <p>Deliberately <em>not</em> called from {@link PlayerStatsAPI#setStat}. That is the admin
 * override; an operator correcting a number is not the player achieving something, and announcing it
 * would put a congratulatory toast on a bug fix.
 */
@NullMarked
public final class StatProgression {

    private StatProgression() {}

    /**
     * Tells the player one of their stats went up.
     *
     * @param from  value before, for the {@code 24 → 25} readout
     * @param to    value after
     * @param sourceKey lang key naming what caused it — a {@link StatTraining.Source} description or
     *                  a milestone message — or null when there is nothing useful to say (an admin
     *                  growth grant). Sanitised before it goes on the wire.
     */
    public static void announceLevelUp(ServerPlayer player, PlayerStat stat, int from, int to,
                                       @Nullable String sourceKey) {
        if (to <= from) return;
        String safe = sourceKey == null ? "" : PacketCodecUtils.normalizeIdentifier(sourceKey);
        StatLevelUpS2CPayload.send(player, stat, from, to, safe);
    }

    /** Convenience for the paths that have no useful cause to name. */
    public static void announceLevelUp(ServerPlayer player, PlayerStat stat, int from, int to) {
        announceLevelUp(player, stat, from, to, null);
    }
}
