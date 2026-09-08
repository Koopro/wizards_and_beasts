package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import at.koopro.wizardsandbeasts.spell.def.SpellReloadListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes the datapack spell table to a client, on login and on every {@code /reload}.
 *
 * <h2>Why this had to exist</h2>
 * {@code SpellReloadListener} is registered on {@code AddServerReloadListenersEvent}, so the JSON
 * slice of {@link Spells} is populated <b>on the server only</b>. 128 of the mod's 155 spells are
 * datapack spells, and the client asks {@code Spells.byId} about them constantly: the wand HUD reads
 * a display name and a cooldown from it every frame, the spell wheel filters its entries on
 * {@code spell != null}, and {@code BeamAppearance} resolves a beam's colour and shape through it.
 * On a dedicated server every one of those lookups returned {@code null} — nameless HUD slots, an
 * empty spell wheel, and no beam for any channelled spell that was not one of the eleven Java ones.
 *
 * <p>It worked in single-player by accident, in exactly the way {@code BroomDefinitionsSyncS2CPayload}
 * describes: the integrated server shares a JVM with the client and the registry is a static field,
 * so both were reading the same map. That is the shape of bug that survives every hour of dev
 * testing and appears the moment somebody joins a real server.
 *
 * <h2>One codec, not two</h2>
 * The stream codec runs {@link SpellDefinition#CODEC} through
 * {@code ByteBufCodecs.fromCodecWithRegistries} rather than spelling the fields out again. A
 * definition carries four nested records and sits at the {@code RecordCodecBuilder} field ceiling;
 * a hand-written serializer would mean every new field has to be added in three places, and
 * forgetting the third is silent — the field simply arrives as its default on the client, which is
 * the class of failure this payload exists to fix.
 *
 * <p>Ids travel alongside the definitions because a {@link SpellDefinition} does not carry its own:
 * on the server it is keyed by the file it was loaded from.
 */
@NullMarked
public record SpellDefinitionsSyncS2CPayload(Map<Identifier, SpellDefinition> definitions)
        implements CustomPacketPayload {

    /**
     * Sanity bound on a packet built from server data. Generous: 155 spells ship, and a datapack
     * adding a few hundred more is a reasonable thing to do.
     */
    private static final int MAX_DEFINITIONS = 2048;

    public static final Type<SpellDefinitionsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_definitions_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellDefinitionsSyncS2CPayload> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Identifier, SpellDefinition, Map<Identifier, SpellDefinition>>map(
                            LinkedHashMap::new,
                            Identifier.STREAM_CODEC,
                            ByteBufCodecs.fromCodecWithRegistries(SpellDefinition.CODEC),
                            MAX_DEFINITIONS)
                    .map(SpellDefinitionsSyncS2CPayload::new, SpellDefinitionsSyncS2CPayload::definitions);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Rebuilds the client's JSON slice of the spell registry from the received table.
     *
     * <p>{@code replaceJsonSpells} publishes an immutable map through a volatile field, so a render
     * thread reading a spell mid-reload sees either the old table or the new one and never a
     * half-built map. In single-player this runs against the same static registry the integrated
     * server just wrote, with identical contents — a redundant swap, not a conflicting one.
     */
    public void applyToClientRegistry() {
        List<JsonSpell> spells = new ArrayList<>(definitions.size());
        for (Map.Entry<Identifier, SpellDefinition> entry : definitions.entrySet()) {
            Identifier id = entry.getKey();
            spells.add(new JsonSpell(id.getNamespace() + ":" + id.getPath(), entry.getValue()));
        }
        Spells.replaceJsonSpells(spells);
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new SpellDefinitionsSyncS2CPayload(SpellReloadListener.loaded()));
    }
}
