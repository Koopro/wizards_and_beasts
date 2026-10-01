package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ActionReply;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ActionRequest;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ChangeRow;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.HistoryRow;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.IssueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ListReply;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.Op;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.PreviewReply;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ProfileRow;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Profiles section's wire formats: every record survives the trip, and a crafted action cannot invent an op. */
class ProfilePayloadCodecTest {

    @Test
    void theListingRoundTrips() {
        ListReply sent = new ListReply(new AdminProfilePayloads.Listing(
                List.of(new ProfileRow("hardcore", "Hardcore", "Magic bites.", "Wizards & Beasts",
                        ProfileDocument.Kind.PRESET, 0, 7)),
                List.of("usable.json"),
                List.of(new HistoryRow(42, "wizards_and_beasts:spell_damage_multiplier", "k", AdminChangeRecord.Kind.PROFILE,
                        "1", "1.5", "Dev", 1_790_000_000_000L, true, "", false, "profile:hardcore:1")),
                "saves/world/wizards_and_beasts/admin_profiles"));
        ByteBuf buf = Unpooled.buffer();
        ListReply.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, ListReply.STREAM_CODEC.decode(buf));
    }

    @Test
    void aPreviewRoundTripsAndKnowsWhetherItApplies() {
        PreviewReply sent = new PreviewReply("hardcore", "Hardcore",
                List.of(new ChangeRow("wizards_and_beasts:spell_damage_multiplier", "k", "1", "1.5", "RUNTIME")),
                List.of(), List.of(new IssueRow("unknown_setting", "x:y", "")), 0, true, false);
        ByteBuf buf = Unpooled.buffer();
        PreviewReply.STREAM_CODEC.encode(buf, sent);
        PreviewReply decoded = PreviewReply.STREAM_CODEC.decode(buf);
        assertEquals(sent, decoded);
        assertTrue(decoded.applicable());
        assertFalse(new PreviewReply("x", "x", List.of(), List.of(new IssueRow("invalid_value", "a", "b")), List.of(),
                0, false, false).applicable());
    }

    @Test
    void actionsRoundTripAndAnUnknownOpIsRefused() {
        for (Op op : Op.values()) {
            ActionRequest sent = new ActionRequest(op, "hardcore", "My copy", op.needsConfirmation());
            ByteBuf buf = Unpooled.buffer();
            ActionRequest.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, ActionRequest.STREAM_CODEC.decode(buf));
        }
        ByteBuf crafted = Unpooled.buffer();
        crafted.writeByte(Op.values().length + 3);
        assertThrows(RuntimeException.class, () -> ActionRequest.STREAM_CODEC.decode(crafted));

        ActionReply reply = new ActionReply(Op.IMPORT, "invalid_file", "broken.json", 0,
                List.of(new IssueRow("malformed", "file", "")));
        ByteBuf buf = Unpooled.buffer();
        ActionReply.STREAM_CODEC.encode(buf, reply);
        assertEquals(reply, ActionReply.STREAM_CODEC.decode(buf));
    }

    @Test
    void applyingDeletingAndRevertingNeedConfirmation() {
        assertTrue(Op.APPLY.needsConfirmation());
        assertTrue(Op.DELETE.needsConfirmation());
        assertTrue(Op.REVERT_ONE.needsConfirmation());
        assertTrue(Op.REVERT_GROUP.needsConfirmation());
        assertFalse(Op.SAVE_AS.needsConfirmation());
        assertFalse(Op.IMPORT.needsConfirmation());
    }
}
