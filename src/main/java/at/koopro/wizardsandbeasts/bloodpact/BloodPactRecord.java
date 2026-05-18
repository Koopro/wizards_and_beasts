package at.koopro.wizardsandbeasts.bloodpact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record BloodPactRecord(UUID pactUUID, UUID partnerUUID) {

    public static final Codec<BloodPactRecord> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("pact_uuid").forGetter(BloodPactRecord::pactUUID),
            UUIDUtil.CODEC.fieldOf("partner_uuid").forGetter(BloodPactRecord::partnerUUID)
    ).apply(inst, BloodPactRecord::new));
}
