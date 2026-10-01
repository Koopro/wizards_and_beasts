package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.spell.SpellAdminService;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.List;

/**
 * Server side of the Magic section's payloads. Like {@link AdminNetworkService}, every handler builds an
 * {@link AdminContext} from the sender first; reads from a non-administrator are dropped without a reply.
 */
@NullMarked
public final class AdminSpellNetworkService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AdminSpellNetworkService() {}

    public static boolean sendList(ServerPlayer player) {
        if (!authorised(player, "spell list")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new AdminSpellPayloads.ListReply(SpellAdminService.list()));
        return true;
    }

    public static boolean sendDetail(ServerPlayer player, String spellId) {
        if (!authorised(player, "spell detail")) {
            return false;
        }
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            PacketDistributor.sendToPlayer(player, new AdminSpellPayloads.ActionReply(false,
                    "admin.wizards_and_beasts.spell_test.unknown_spell", spellId));
            return false;
        }
        AdminContext viewer = AdminContext.of(player);
        PacketDistributor.sendToPlayer(player, new AdminSpellPayloads.DetailReply(
                SpellAdminService.summary(spell), SpellAdminService.facts(spell), SpellAdminService.settings(spell, viewer)));
        return true;
    }

    /** Batch reset; each value's result is sent back, then the refreshed page and list. */
    public static List<AdminResult> reset(ServerPlayer player, AdminSpellPayloads.ResetRequest request) {
        AdminContext actor = AdminContext.of(player);
        List<AdminResult> results;
        Spell spell = null;
        if (request.scope() == AdminSpellPayloads.ResetScope.SPELL) {
            spell = Spells.byId(request.target());
            results = spell == null ? List.of() : SpellAdminService.resetSpell(actor, spell, request.confirmed());
        } else {
            SpellCategory category = categoryByName(request.target());
            results = category == null ? List.of() : SpellAdminService.resetCategory(actor, category, request.confirmed());
        }
        for (AdminResult result : results) {
            PacketDistributor.sendToPlayer(player, new AdminSettingResultS2CPayload(AdminSettingResultS2CPayload.BATCH, result));
        }
        long applied = results.stream().filter(AdminResult::applied).count();
        PacketDistributor.sendToPlayer(player, new AdminSpellPayloads.ActionReply(applied > 0 || results.isEmpty(),
                "admin.wizards_and_beasts.spell_reset.done", Long.toString(applied)));
        if (actor.canRead()) {
            if (spell != null) {
                sendDetail(player, spell.getId());
            }
            sendList(player);
        }
        return results;
    }

    public static SpellTestService.Outcome test(ServerPlayer player, AdminSpellPayloads.TestRequest request) {
        SpellTestService.Outcome outcome = SpellTestService.test(player, request.spellId(), request.mode(), request.player());
        PacketDistributor.sendToPlayer(player, new AdminSpellPayloads.ActionReply(outcome.success(), outcome.messageKey(), outcome.detail()));
        return outcome;
    }

    private static boolean authorised(ServerPlayer player, String what) {
        AdminContext actor = AdminContext.of(player);
        if (actor.canRead() && actor.canModify(AdminCapability.CONTENT)) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped {} request from unauthorised {} ({})", what, player.getName().getString(), player.getUUID());
        return false;
    }

    private static @org.jspecify.annotations.Nullable SpellCategory categoryByName(String name) {
        for (SpellCategory category : SpellCategory.values()) {
            if (category.name().equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }
}
