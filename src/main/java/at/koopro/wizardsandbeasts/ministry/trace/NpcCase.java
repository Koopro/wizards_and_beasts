package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * A case the Auror Office posts against a culprit who is not a player — a dark witch hexing a village, a wizard
 * using the Imperius Curse on a farmer — so the Auror's work exists on a server where nobody else breaks the law.
 *
 * <p>It is worked exactly like a real case and answers to the same evidence rules: examine the scene, follow the
 * residue to where the culprit went, question the villagers who saw, identify, apprehend. The culprit is spawned
 * only once the case is identified and the Auror is close, and is taken alive or the case fails.
 *
 * @param id            board number, unique across the server's cases and incidents
 * @param template      the {@code ministry_cases/} file it was drawn from
 * @param spellId       the magic used, for the case file
 * @param legalClass    how grave it was
 * @param dimension     where
 * @param scene         where it happened; its height is settled when the chunk is first read
 * @param sceneSettled  whether {@code scene}'s height is the ground's
 * @param hideout       where the culprit went to ground
 * @param postedAt      game tick it went on the board
 * @param expiresAt     game tick it comes off the board if nobody closes it
 * @param auror         the Auror working it
 * @param aurorUntil    game tick their hold lapses
 * @param culprit       the culprit entity, once spawned
 * @param identified    the evidence has put a face to it
 */
@NullMarked
public record NpcCase(long id, Identifier template, String spellId, LegalClass legalClass, Identifier dimension,
                      BlockPos scene, boolean sceneSettled, BlockPos hideout, long postedAt, long expiresAt,
                      Optional<UUID> auror, long aurorUntil, Optional<UUID> culprit, boolean identified) {

    public static final Codec<NpcCase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(NpcCase::id),
            Identifier.CODEC.fieldOf("template").forGetter(NpcCase::template),
            Codec.STRING.optionalFieldOf("spell", "").forGetter(NpcCase::spellId),
            LegalClass.CODEC.fieldOf("legal_class").forGetter(NpcCase::legalClass),
            Identifier.CODEC.fieldOf("dimension").forGetter(NpcCase::dimension),
            BlockPos.CODEC.fieldOf("scene").forGetter(NpcCase::scene),
            Codec.BOOL.optionalFieldOf("scene_settled", false).forGetter(NpcCase::sceneSettled),
            BlockPos.CODEC.fieldOf("hideout").forGetter(NpcCase::hideout),
            Codec.LONG.fieldOf("posted_at").forGetter(NpcCase::postedAt),
            Codec.LONG.fieldOf("expires_at").forGetter(NpcCase::expiresAt),
            UUIDUtil.CODEC.optionalFieldOf("auror").forGetter(NpcCase::auror),
            Codec.LONG.optionalFieldOf("auror_until", 0L).forGetter(NpcCase::aurorUntil),
            UUIDUtil.CODEC.optionalFieldOf("culprit").forGetter(NpcCase::culprit),
            Codec.BOOL.optionalFieldOf("identified", false).forGetter(NpcCase::identified)
    ).apply(instance, NpcCase::new));

    public Optional<UUID> aurorAt(long now) {
        return auror.isPresent() && now < aurorUntil ? auror : Optional.empty();
    }

    public NpcCase withAuror(Optional<UUID> holder, long until) {
        return new NpcCase(id, template, spellId, legalClass, dimension, scene, sceneSettled, hideout, postedAt,
                expiresAt, holder, holder.isPresent() ? until : 0L, culprit, identified);
    }

    public NpcCase withScene(BlockPos settledScene, BlockPos settledHideout) {
        return new NpcCase(id, template, spellId, legalClass, dimension, settledScene, true, settledHideout,
                postedAt, expiresAt, auror, aurorUntil, culprit, identified);
    }

    public NpcCase withCulprit(Optional<UUID> entity) {
        return new NpcCase(id, template, spellId, legalClass, dimension, scene, sceneSettled, hideout, postedAt,
                expiresAt, auror, aurorUntil, entity, identified);
    }

    public NpcCase asIdentified() {
        return new NpcCase(id, template, spellId, legalClass, dimension, scene, sceneSettled, hideout, postedAt,
                expiresAt, auror, aurorUntil, culprit, true);
    }
}
