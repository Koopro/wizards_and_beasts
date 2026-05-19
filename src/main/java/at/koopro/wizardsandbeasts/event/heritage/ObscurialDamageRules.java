package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

final class ObscurialDamageRules {

    private ObscurialDamageRules() {}

    static boolean isAllowedInDarkForm(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        if (source.is(DamageTypeTags.IS_FALL)) return true;
        if (source.is(DamageTypeTags.IS_FIRE)) return true;
        if (source.is(DamageTypeTags.IS_DROWNING)) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;

        String msgId = source.getMsgId();
        if ("magic".equals(msgId) || "indirectMagic".equals(msgId)) return true;

        if (source.getEntity() instanceof ServerPlayer attacker && WandHelper.isHoldingWand(attacker)) {
            return true;
        }

        return isFutureBossBypass(source);
    }

    private static boolean isFutureBossBypass(DamageSource source) {
        return false;
    }
}
