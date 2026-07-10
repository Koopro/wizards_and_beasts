package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.skill.SkillNodeEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Applies / removes a Vocation's commitment stat profile. Parallel to {@code SkillAttributeApplicator} rather
 * than reusing it: that class is a full-rebuild singleton driven off {@code unlockedSkills} and strips all
 * {@code skill/}-prefixed modifiers wholesale — it cannot apply an arbitrary effect list for a non-node source.
 *
 * <p>Vocation modifiers are keyed under the {@code vocation/} path prefix, disjoint from {@code skill/}, so the
 * two applicators never clobber each other. Only {@link SkillNodeEffect.AttributeBoost} entries are honored
 * (the rest of the union aggregates into spell/bonus state that has no meaning for a static commitment).
 * {@code addOrReplacePermanentModifier} makes apply idempotent; teardown removes by keyed {@code Identifier}.
 */
@NullMarked
public final class VocationEffectApplicator {
    private static final String VOCATION_MODIFIER_PATH_PREFIX = "vocation/";

    private VocationEffectApplicator() {}

    /** Applies the declared Vocation's commitment profile (idempotent via addOrReplace). */
    public static void apply(ServerPlayer player, VocationDefinition vocation) {
        for (SkillNodeEffect effect : vocation.commitmentEffects()) {
            if (effect instanceof SkillNodeEffect.AttributeBoost boost) {
                AttributeInstance instance = player.getAttribute(boost.attribute());
                if (instance == null) {
                    continue;
                }
                instance.addOrReplacePermanentModifier(new AttributeModifier(
                        boost.modifierId(), boost.value(), boost.operation()));
            }
        }
    }

    public static void remove(ServerPlayer player, VocationDefinition vocation) {
        for (SkillNodeEffect effect : vocation.commitmentEffects()) {
            if (effect instanceof SkillNodeEffect.AttributeBoost boost) {
                AttributeInstance instance = player.getAttribute(boost.attribute());
                if (instance != null) {
                    instance.removeModifier(boost.modifierId());
                }
            }
        }
    }

    /** Strip every {@code vocation/}-prefixed modifier — exact, idempotent teardown independent of definitions. */
    public static void removeAll(ServerPlayer player) {
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            List<AttributeModifier> toRemove = instance.getModifiers().stream()
                    .filter(modifier -> modifier.id().getNamespace().equals("wizards_and_beasts"))
                    .filter(modifier -> modifier.id().getPath().startsWith(VOCATION_MODIFIER_PATH_PREFIX))
                    .toList();
            for (AttributeModifier modifier : toRemove) {
                instance.removeModifier(modifier.id());
            }
        }
    }
}
