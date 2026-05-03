package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Alohomora extends Spell {

    public Alohomora() {
        super("alohomora", "Alohomora", SpellCategory.UTILITY, 40, 0.0f, 0xFFAAFF88);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(5.0f)
                .opensBlocks()
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }
}
