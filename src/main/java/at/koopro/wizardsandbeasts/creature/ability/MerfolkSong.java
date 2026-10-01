package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Signature ability (merpeople): their song. Merfolk song can only be understood under water; above the surface it is
 * a horrible screech (Goblet of Fire — the golden egg, the Black Lake). Every so often a merperson in water sings, and
 * each person in range hears what their ears allow ({@link SignatureRules#merfolkSong}):
 *
 * <ul>
 *   <li>head under water, within {@link SignatureRules#MERFOLK_SONG_RANGE}: the song, and a verse of it in words —
 *       having understood it is the signature;</li>
 *   <li>anywhere within {@link SignatureRules#MERFOLK_SCREECH_RANGE} otherwise: the screech, and nothing to make of it.
 *       </li>
 * </ul>
 *
 * <p>Each listener hears only their own version (the sound is played to that player alone). The verses are written
 * for the mod rather than quoted.
 */
public record MerfolkSong(int intervalTicks, int verses) implements CreatureAbility {

    static final String COOLDOWN = "merfolk_song";

    public static final MapCodec<MerfolkSong> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("interval_ticks", 600).forGetter(MerfolkSong::intervalTicks),
            Codec.INT.optionalFieldOf("verses", 4).forGetter(MerfolkSong::verses)
    ).apply(instance, MerfolkSong::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.MERFOLK_SONG;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.isInWater() && entity.getCooldown(COOLDOWN) == 0) {
            entity.setCooldown(COOLDOWN, intervalTicks + entity.getRandom().nextInt(Math.max(1, intervalTicks / 2)));
            sing(entity);
        }
    }

    /**
     * Sings once. Everyone in range hears their own version.
     *
     * @return how many listeners heard the song and how many only the screech
     */
    public Map<SignatureRules.MerfolkSong, Integer> sing(@NonNull GenericBeastEntity entity) {
        Map<SignatureRules.MerfolkSong, Integer> heard = new EnumMap<>(SignatureRules.MerfolkSong.class);
        if (!(entity.level() instanceof ServerLevel level)) {
            return heard;
        }
        int verse = entity.getRandom().nextInt(Math.max(1, verses));
        double reach = Math.max(SignatureRules.MERFOLK_SONG_RANGE, SignatureRules.MERFOLK_SCREECH_RANGE);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(reach),
                p -> p.isAlive() && !p.isSpectator())) {
            SignatureRules.MerfolkSong what = hears(entity, player);
            switch (what) {
                case SONG -> {
                    hearAlone(player, entity, ModSounds.MERFOLK_SONG);
                    player.displayClientMessage(Component.translatable(
                            "entity.wizards_and_beasts.merperson.song." + verse).withStyle(ChatFormatting.AQUA,
                            ChatFormatting.ITALIC), true);
                    BestiaryDiscoveryHandler.witnessedSignature(player, entity);
                }
                case SCREECH -> hearAlone(player, entity, ModSounds.MERFOLK_SCREECH);
                case NONE -> { }
            }
            heard.merge(what, 1, Integer::sum);
        }
        return heard;
    }

    /** What this person makes of the singing: the song, a screech, or nothing. */
    public static SignatureRules.MerfolkSong hears(GenericBeastEntity singer, ServerPlayer player) {
        return SignatureRules.merfolkSong(eyesUnderWater(player), Math.sqrt(player.distanceToSqr(singer)));
    }

    /** Whether this person's head is under water — read from the block at eye level, not last tick's state. */
    static boolean eyesUnderWater(ServerPlayer player) {
        return player.level().getFluidState(BlockPos.containing(player.getEyePosition())).is(FluidTags.WATER);
    }

    /** Plays {@code sound} from the singer's position to {@code player} and nobody else. */
    private static void hearAlone(ServerPlayer player, GenericBeastEntity singer, Holder<SoundEvent> sound) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, singer.getX(), singer.getY(),
                singer.getZ(), 1.0f, 1.0f, singer.getRandom().nextLong()));
    }
}
