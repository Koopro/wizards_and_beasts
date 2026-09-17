package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One piece of magic the law may care about: who, what, where, when, who saw, and how bad.
 *
 * <p>The server always knows who cast it; {@code attributed} is whether the <em>Ministry</em> does. Nothing
 * that acts on an incident — a letter, a case, a hearing — may act on the caster of an unattributed one. The
 * caster is kept so that a later report carrying a name, or a wand examination, reaches the right wizard.
 *
 * @param id              unique within the server's case data
 * @param caster          who cast it (ground truth, not Ministry knowledge)
 * @param casterName      the caster's name at the time, for records read while they are offline
 * @param spellId         the spell
 * @param dimension       where
 * @param pos             where, to the block
 * @param tick            when, in game time
 * @param legalClass      the spell's standing in law
 * @param exposure        how far secrecy was broken
 * @param underageBreach  underage magic outside school
 * @param inDanger        cast in a life-threatening situation
 * @param muggleWitnesses Muggles who saw it
 * @param wizardWitnesses other players who saw it
 * @param offence         the crime the spell is in itself, if any
 * @param attributed      whether the Ministry knows who did it
 */
@NullMarked
public record Incident(
        long id,
        UUID caster,
        String casterName,
        String spellId,
        Identifier dimension,
        BlockPos pos,
        long tick,
        LegalClass legalClass,
        Exposure exposure,
        boolean underageBreach,
        boolean inDanger,
        int muggleWitnesses,
        List<UUID> wizardWitnesses,
        Optional<MagicalOffence> offence,
        boolean attributed) {

    public static final Codec<Incident> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(Incident::id),
            UUIDUtil.CODEC.fieldOf("caster").forGetter(Incident::caster),
            Codec.STRING.optionalFieldOf("caster_name", "").forGetter(Incident::casterName),
            Codec.STRING.fieldOf("spell").forGetter(Incident::spellId),
            Identifier.CODEC.fieldOf("dimension").forGetter(Incident::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Incident::pos),
            Codec.LONG.fieldOf("tick").forGetter(Incident::tick),
            LegalClass.CODEC.fieldOf("legal_class").forGetter(Incident::legalClass),
            Exposure.CODEC.fieldOf("exposure").forGetter(Incident::exposure),
            Codec.BOOL.optionalFieldOf("underage_breach", false).forGetter(Incident::underageBreach),
            Codec.BOOL.optionalFieldOf("in_danger", false).forGetter(Incident::inDanger),
            Codec.INT.optionalFieldOf("muggle_witnesses", 0).forGetter(Incident::muggleWitnesses),
            UUIDUtil.CODEC.listOf().optionalFieldOf("wizard_witnesses", List.of())
                    .forGetter(Incident::wizardWitnesses),
            MagicalOffence.CODEC.optionalFieldOf("offence").forGetter(Incident::offence),
            Codec.BOOL.optionalFieldOf("attributed", false).forGetter(Incident::attributed)
    ).apply(instance, Incident::new));

    public Incident {
        wizardWitnesses = List.copyOf(wizardWitnesses);
        muggleWitnesses = Math.max(0, muggleWitnesses);
    }

    public Incident asAttributed() {
        return attributed ? this : new Incident(id, caster, casterName, spellId, dimension, pos, tick, legalClass,
                exposure, underageBreach, inDanger, muggleWitnesses, wizardWitnesses, offence, true);
    }

    /** The weight of this incident, for choosing the worst charge. */
    public int gravity() {
        return TraceRules.gravity(legalClass, exposure, underageBreach);
    }
}
