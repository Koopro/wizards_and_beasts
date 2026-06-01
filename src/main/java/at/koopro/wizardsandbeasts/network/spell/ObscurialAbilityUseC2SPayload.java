package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialAbility;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ObscurialAbilityUseC2SPayload(String abilityId) implements CustomPacketPayload {
    public static final Type<ObscurialAbilityUseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "obscurial_ability_use"));

    public static final StreamCodec<ByteBuf, ObscurialAbilityUseC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ObscurialAbilityUseC2SPayload decode(ByteBuf buf) {
            return new ObscurialAbilityUseC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ObscurialAbilityUseC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.abilityId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ObscurialAbilityUseC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
            var typeData = player.getData(ModAttachments.HERITAGE_DATA.get());
            PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());

            if (!ObscurialRules.isObscurial(typeData) || !ObscurialRules.isDarkForm(typeData)) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_NOT_IN_DARK_FORM);
                player.displayClientMessage(Component.literal("\u00A75Obscurial abilities require obscurus form."), true);
                return;
            }

            String safeAbility = PacketCodecUtils.normalizeIdentifier(pkt.abilityId);
            ObscurialAbility ability = ObscurialAbility.bySpellId(safeAbility);
            if (ability == null) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_UNKNOWN);
                player.displayClientMessage(Component.literal("\u00A7cUnknown Obscurial ability."), true);
                return;
            }

            Spell spell = Spells.byId(ability.spellId());
            if (spell == null || !ObscurialRules.isObscurialAbility(spell)) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_SPELL_MISSING);
                player.displayClientMessage(Component.literal("\u00A7cAbility backend is unavailable."), true);
                return;
            }

            long now = level.getGameTime();
            if (spellData.isOnCooldown(spell.getId(), now)) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_COOLDOWN_ACTIVE);
                float sec = (spellData.getCooldownExpiry(spell.getId()) - now) / 20f;
                player.displayClientMessage(Component.literal(String.format("\u00A7e%s recharging %.1fs", ability.displayName(), Math.max(0f, sec))), true);
                return;
            }
            if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(player, spellData)) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_REQUIREMENTS_UNMET);
                player.displayClientMessage(Component.literal("\u00A7c" + spell.getRequirement().getDescription()), true);
                return;
            }

            try {
                spell.execute(level, player, player.getMainHandItem());
            } catch (Exception ex) {
                spellData.incrementRejectReason(SpellRejectCodes.ABILITY_EXECUTE_FAILED);
                player.displayClientMessage(Component.literal("\u00A7cObscurial ability failed to execute."), true);
                return;
            }

            int cooldown = Math.max(1, (int) (spell.getBaseCooldownTicks() * ObscurialCombatRules.getAbilityCooldownMultiplier(player)));
            long expiryTick = now + cooldown;
            int oldCount = spellData.getCastCount(spell.getId());
            spellData.setCooldown(spell.getId(), expiryTick);
            spellData.incrementCastCount(spell.getId());
            SpellDataDeltaS2CPayload.sendTo(
                    player,
                    spell.getId(),
                    expiryTick,
                    oldCount + 1,
                    spellData.getSuccessfulHits(spell.getId()),
                    spellData.getGlobalCooldownEndTick());
            if (ObscurialRules.getStressTier(ObscurialResourceManager.getStress(player)) == ObscurialRules.StressTier.VOLATILE) {
                player.displayClientMessage(Component.literal("\u00A75Volatile stress is prolonging your ability cooldowns."), true);
            }
        });
    }
}
