package at.koopro.neo.network;

import at.koopro.neo.Config;
import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.event.SkillEvents;
import at.koopro.neo.item.WandItem;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.skill.SkillSystemAPI;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.spell.wand.WandStats;
import at.koopro.neo.spell.wand.WandStatsResolver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellCastC2SPacket() implements CustomPacketPayload {

    public static final Type<SpellCastC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "spell_cast"));

    public static final StreamCodec<ByteBuf, SpellCastC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellCastC2SPacket decode(ByteBuf buf) {
            return new SpellCastC2SPacket();
        }

        @Override
        public void encode(ByteBuf buf, SpellCastC2SPacket pkt) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellCastC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            if (!(player.getMainHandItem().getItem() instanceof WandItem)
                    && !(player.getOffhandItem().getItem() instanceof WandItem)) {
                return;
            }

            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            String spellId = data.getActiveSpellId();
            if (spellId == null) return;

            Spell spell = Spells.byId(spellId);
            if (spell == null) return;

            if (!data.knowsSpell(spellId)) return;

            if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(data)) {
                player.displayClientMessage(
                        Component.literal("\u00A7c" + spell.getRequirement().getDescription()),
                        true);
                return;
            }

            long currentTick = serverLevel.getGameTime();
            if (data.isOnCooldown(spellId, currentTick)) {
                long remaining = (data.getCooldownExpiry(spellId) - currentTick) / 20;
                player.displayClientMessage(
                        Component.literal("\u00A7c" + spell.getDisplayName() + " on cooldown (" + remaining + "s)"),
                        true);
                return;
            }

            var wandStack = player.getMainHandItem().getItem() instanceof WandItem
                    ? player.getMainHandItem() : player.getOffhandItem();
            spell.execute(serverLevel, player, wandStack);

            WandStats wand = WandStatsResolver.resolve(wandStack);
            float cooldownMult = SkillSystemAPI.getCooldownMultiplier(player, spell)
                    * wand.cooldownFor(spell);
            int cooldown = Math.max(1, (int)(spell.getBaseCooldownTicks() * cooldownMult));
            long expiryTick = currentTick + cooldown;
            data.setCooldown(spellId, expiryTick);
            int oldCount = data.getCastCount(spellId);
            data.incrementCastCount(spellId);
            int newCount = data.getCastCount(spellId);
            SkillEvents.checkProficiencyMilestone(player, spellId, oldCount);

            SpellDataDeltaS2CPacket.sendTo(player, spellId, expiryTick, newCount);
        });
    }
}
