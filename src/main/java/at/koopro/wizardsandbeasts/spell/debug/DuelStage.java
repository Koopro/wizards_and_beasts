package at.koopro.wizardsandbeasts.spell.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.gametest.WizardTestSupport;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Set;

/**
 * The server half of the two-client duel capture: a stone floor in open sky, and two wizards armed and set facing
 * each other ten blocks apart as they log in — {@code DuelA} in the west with Stupefy, {@code DuelB} in the east with
 * Expelliarmus. The clients ({@code client/debug/DuelPlaytest}) cast on the same server tick and hold, so the bolts
 * meet and the lock is fought by two real players.
 *
 * <p>Development only, inert unless {@code WB_DUEL_SERVER} is set on the server and not in production.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DuelStage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_DUEL_SERVER") != null && !FMLEnvironment.isProduction();

    public static final int STAGE_Y = 150;
    public static final double WEST_X = 0.5;
    public static final double EAST_X = 10.5;

    private static boolean built;

    private DuelStage() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level();
        if (!built) {
            built = true;
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            level.setWeatherParameters(24000, 0, false, false);
            level.setDayTime(6000);
            for (int x = -8; x <= 18; x++) {
                for (int z = -8; z <= 8; z++) {
                    level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        boolean west = "DuelA".equals(player.getGameProfile().name());
        Spell spell = Spells.byId(west ? "stupefy" : "expelliarmus");
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, west ? WEST_X : EAST_X, STAGE_Y, 0.5, Set.of(), west ? -90.0F : 90.0F, 0.0F, true);
        WizardTestSupport.makeWandkind(player);
        WizardTestSupport.grantNewts(player);
        WizardTestSupport.giveBondedWand(player);
        WizardTestSupport.settleAllegiance(player);
        if (spell != null) {
            var data = player.getData(ModAttachments.SPELL_DATA.get());
            data.learnSpell(spell.getId());
            data.setLoadoutSpell(0, spell.getId());
            data.setActiveSlot(0);
            data.setCooldown(spell.getId(), 0L);
            data.setGlobalCooldownEndTick(0L);
        }
        LOGGER.info("Duel stage: {} armed with {} in the {}", player.getGameProfile().name(),
                spell == null ? "nothing" : spell.getId(), west ? "west" : "east");
        // Both duellists in: mark them both at once, so the two clients start from one moment (the marker is a
        // synced effect, so each client sees it on the same server tick).
        var duellists = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.getGameProfile().name().startsWith("Duel")).toList();
        if (duellists.size() >= 2) {
            for (ServerPlayer duellist : duellists) {
                // Placed again now: whatever moved them after their own login (the heritage onboarding) is done.
                boolean westSide = "DuelA".equals(duellist.getGameProfile().name());
                duellist.teleportTo(level, westSide ? WEST_X : EAST_X, STAGE_Y, 0.5, Set.of(),
                        westSide ? -90.0F : 90.0F, 0.0F, true);
                LOGGER.info("Duel stage: {} at {} {} {}", duellist.getGameProfile().name(),
                        duellist.getX(), duellist.getY(), duellist.getZ());
                duellist.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.LUCK, 20 * 600, 0, true, false));
            }
            LOGGER.info("Duel stage: both in, marked");
        }
    }
}
