package at.koopro.wizardsandbeasts.client.spell.network;

import at.koopro.wizardsandbeasts.client.spell.SpellVfxClient;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellTeacherState;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.network.spell.AvadaBlastS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.CrucioIntentFeedbackS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioControlS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioVictimBoundS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.PatronusFormSetS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ProtegoSpawnS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.teacher.SpellTeacherOpenS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handlers for S2C spell payloads. Keeps payload records pure data
 * (dist-safe): common-code registrars reference these handlers via method
 * reference, the NeoForge-documented pattern for client-only payload handling.
 */
public final class SpellClientPayloadHandlers {
    private SpellClientPayloadHandlers() {}

    private static final int AVADA_ARGB = 0xFF00FF00;

    public static void handleAvadaBlast(AvadaBlastS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }

            Vec3 from = new Vec3(pkt.startX(), pkt.startY(), pkt.startZ());
            Vec3 to = new Vec3(pkt.endX(), pkt.endY(), pkt.endZ());
            SpellVfxClient.spawnTintBeam(from, to, SpellFamily.DARK, AVADA_ARGB);
            SpellVfxClient.spawnTintBurst(to, SpellFamily.DARK, AVADA_ARGB, 20, 0.4f);
        });
    }

    public static void handleCrucioIntentFeedback(CrucioIntentFeedbackS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setLastIntentMultiplier(pkt.intentMultiplier());
                ClientSignatureSpellState.triggerImperioScreenShake();
            }
        });
    }

    public static void handleImperioControl(ImperioControlS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setImperioControl(pkt.isControlled(), pkt.controllerUUID());
            }
        });
    }

    public static void handleImperioResist(ImperioResistS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String msg = pkt.success()
                    ? "§6You throw off the Imperius Curse!"
                    : "§eYou struggle against the Imperius Curse... (" + String.format("%.0f%%", pkt.resistanceProgress() * 100f) + ")";
            mc.player.displayClientMessage(Component.literal(msg), true);
        });
    }

    public static void handleImperioVictimBound(ImperioVictimBoundS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setImperioBoundVictim(pkt.victimUuid());
            }
        });
    }

    public static void handlePatronusFormSet(PatronusFormSetS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setPatronusForm(pkt.form());
            }
        });
    }

    public static void handleProtegoSpawn(ProtegoSpawnS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProtegoClientPacketHandlers.handleSpawn(pkt));
    }

    public static void handleProtegoAnimation(ProtegoAnimationS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProtegoClientPacketHandlers.handleAnimation(pkt));
    }

    public static void handleSpellDataDelta(SpellDataDeltaS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSpellDataState.applyDelta(pkt));
    }

    public static void handleSpellDataSync(SpellDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSpellDataState.applyFullSync(pkt));
    }

    public static void handleSpellDenied(SpellDeniedS2CPayload pkt, IPayloadContext ctx) {
        // Routed through SpellVfxClient (not an inline SoundManager.play) so this class —
        // loaded server-side when the registrar resolves the method ref — never forces
        // verification-time loading of the client-only SoundInstance type.
        ctx.enqueueWork(SpellVfxClient::playDeniedFeedback);
    }

    public static void handleSpellImpactBurst(SpellImpactBurstS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Vec3 pos = new Vec3(pkt.x(), pkt.y(), pkt.z());
            SpellFamily family = SpellFamily.byOrdinal(pkt.familyOrdinal());
            SpellVfxClient.spawnTintBurst(pos, family, pkt.argb(), Math.max(1, pkt.count()), pkt.spread());
        });
    }

    public static void handleSpellProficiencySync(SpellProficiencySyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSpellDataState.applyProficiencyDelta(pkt));
    }

    public static void handleSpellTeacherOpen(SpellTeacherOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSpellTeacherState.setOffers(pkt.offers());
            openClientScreenSafe();
        });
    }

    private static void openClientScreenSafe() {
        ClientScreenHooksInvoker.invoke("openSpellTeacherScreen");
    }
}
