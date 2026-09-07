package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodData;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * A blood-drinker's pool, pushed to its owner.
 *
 * <p>Separate from {@code HeritageDataSyncS2CPayload} on purpose, and not merely for tidiness. The
 * heritage payload carries a heritage, a variant, a lock, a form, a profession tree and a flag map; it
 * is sent when one of those changes, which is rarely. Blood changes several times a second. Riding the
 * heritage payload would have meant re-sending all of that to move a bar, and the two would have had to
 * share a sync-version counter — so a blood tick would have raced the heritage writes it has nothing to
 * do with.
 *
 * <p><b>Carries the policy as well as the number</b>, which is what makes the HUD side of this
 * heritage-agnostic. The client does not re-derive "is this player a blood-drinker" from the variant
 * tags it happens to have; it is told, by the same authority that decided to stop applying nutrition. A
 * future {@link NutritionPolicy#NONE} race gets the right HUD by sending a different ordinal here and
 * changing nothing else.
 *
 * <p><b>The thirst band is decided server-side and sent</b>, rather than re-derived from the percentage
 * on arrival. The band's floors are config values, and a common config is not synchronised — a server
 * that moved {@code vampireBloodParchedFloorPercent} would have had a client colouring the bar for one
 * band while the server applied another's debuffs. This is the argument {@code StandingSyncS2CPayload}
 * makes for sending resolved bands instead of the record they come from, and it applies here for the
 * same reason.
 *
 * @param policyOrdinal the owner's {@link NutritionPolicy}, by ordinal
 * @param stageOrdinal  the owner's {@link ThirstStage}, decided with the server's own thresholds
 * @param blood         current pool
 * @param maxBlood      pool ceiling, so the meter can be drawn to scale without knowing the config
 */
public record BloodDataSyncS2CPayload(int policyOrdinal, int stageOrdinal, float blood, float maxBlood)
        implements CustomPacketPayload {

    public static final Type<BloodDataSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "blood_data_sync"));

    public static final StreamCodec<ByteBuf, BloodDataSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NonNull BloodDataSyncS2CPayload decode(@NonNull ByteBuf buf) {
            return new BloodDataSyncS2CPayload(
                    PacketCodecUtils.clampNonNegative(buf.readByte()),
                    PacketCodecUtils.clampNonNegative(buf.readByte()),
                    buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public void encode(@NonNull ByteBuf buf, @NonNull BloodDataSyncS2CPayload pkt) {
            buf.writeByte(pkt.policyOrdinal);
            buf.writeByte(pkt.stageOrdinal);
            buf.writeFloat(pkt.blood);
            buf.writeFloat(pkt.maxBlood);
        }
    };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Resolves an ordinal back to a policy, defaulting to {@link NutritionPolicy#VANILLA}.
     *
     * <p>Vanilla rather than throwing, because the failure mode has to be the harmless one: an unknown
     * ordinal means a newer server than this client, and a client that draws no blood meter is a client
     * missing a feature, whereas a client that crashes on the network thread is a client that cannot
     * play at all.
     */
    public NutritionPolicy policy() {
        NutritionPolicy[] policies = NutritionPolicy.values();
        return policyOrdinal >= 0 && policyOrdinal < policies.length
                ? policies[policyOrdinal]
                : NutritionPolicy.VANILLA;
    }

    /** Resolves the band ordinal, defaulting to {@link ThirstStage#SATED} for the same reason. */
    public ThirstStage stage() {
        ThirstStage[] stages = ThirstStage.values();
        return stageOrdinal >= 0 && stageOrdinal < stages.length
                ? stages[stageOrdinal]
                : ThirstStage.SATED;
    }

    public static void syncToPlayer(@NonNull ServerPlayer player) {
        VampireBloodData data = VampireBloodAPI.getData(player);
        PacketDistributor.sendToPlayer(player, new BloodDataSyncS2CPayload(
                NutritionPolicyResolver.resolve(player).ordinal(),
                data.getThirstStage().ordinal(),
                data.getBlood(),
                data.getMaxBlood()));
    }
}
