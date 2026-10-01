package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.client.spell.SpellVfxClient;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * A local, visual-only preview of a spell: its cast sound, and its family's particles laid out the way its cast
 * type delivers — a streak for a projectile, a beam for a held or targeted spell, a spray for a cone, a burst
 * around the caster for a self-cast. Nothing is sent to the server and nothing in the world is touched.
 *
 * <p>Built from the same pieces the real cast uses client-side ({@link SpellVfxClient}'s tinted particles,
 * {@link SpellFamilies}, the spell's colour and cast sound), which is what makes it the starting point for the
 * later beam/visual editor. It is not a substitute for a test cast: damage, hits and effects need the server.
 */
@NullMarked
public final class SpellPreviewClient {

    private static final double LENGTH = 10.0;

    private SpellPreviewClient() {}

    public static void play(Spell spell) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        SpellProperties props = spell.getProperties();
        if (props != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(props.getCastSound(),
                    props.getSoundPitch(), Math.min(1.0f, props.getSoundVolume())));
        }
        SpellFamily family = SpellFamilies.of(spell);
        int argb = spell.getColor() | 0xFF000000;
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 wandTip = player.getEyePosition().add(look.scale(0.9)).add(right.scale(0.35)).add(0, -0.25, 0);
        CastType type = props == null ? CastType.SELF : props.getCastType();
        double reach = props != null && props.getRange() > 0 ? Math.min(props.getRange(), 24.0) : LENGTH;
        Vec3 end = wandTip.add(look.scale(reach));
        switch (type) {
            case PROJECTILE, TARGETED, BEAM_CHANNEL, BEAM_LETHAL -> {
                SpellVfxClient.spawnTintBeam(wandTip, end, family, argb);
                SpellVfxClient.spawnTintBurst(end, family, argb, 24, 0.4f);
            }
            case CONE -> {
                Vec3 up = right.cross(look).normalize();
                for (int i = -2; i <= 2; i++) {
                    Vec3 spread = look.add(right.scale(i * 0.15)).add(up.scale((i % 2) * 0.1)).normalize();
                    SpellVfxClient.spawnTintBeam(wandTip, wandTip.add(spread.scale(reach)), family, argb);
                }
            }
            case SELF -> SpellVfxClient.spawnTintBurst(player.position().add(0, 1.0, 0), family, argb, 40, 0.8f);
        }
        SpellVfxClient.spawnTintBurst(wandTip, family, argb, 8, 0.08f);
    }
}
