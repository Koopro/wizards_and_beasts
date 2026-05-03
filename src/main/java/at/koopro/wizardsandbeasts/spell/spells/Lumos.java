package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

public class Lumos extends Spell {

    public Lumos() {
        super("lumos", "Lumos", SpellCategory.UTILITY, 200, 0.0f, 0xFFFFFF88);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, true, true))
                .selfEffect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, true, true))
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        super.execute(level, caster, wandStack);
        // Keep Lumos focused on self light utility rather than highlighting nearby players.
        SpellHelper.spawnBurst(level, caster.getEyePosition(), getColor(), 14, 0.24);
    }
}
