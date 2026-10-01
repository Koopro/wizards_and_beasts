package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The dashboard's snapshot header: recent changes carry what Undo needs, and the content counts survive the trip. */
class AdminSessionInfoCodecTest {

    private static AdminSessionInfo info(List<AdminSessionInfo.RecentChange> recent) {
        return new AdminSessionInfo(EnumSet.of(AdminCapability.CONFIG, AdminCapability.DEBUG), "0.1.0", true, 3, 20,
                12.5f, false, 17, 20, true, recent, new AdminSessionInfo.Counts(128, 40, 111, 9, 2, 1));
    }

    @Test
    void theSessionInfoRoundTrips() {
        AdminSessionInfo sent = info(List.of(
                new AdminSessionInfo.RecentChange(41, "wizards_and_beasts:broom_server_speed_scale",
                        "admin.wizards_and_beasts.setting.broom_server_speed_scale", "Dev", "1.0", "1.15",
                        1_700_000_000_000L, true, true),
                new AdminSessionInfo.RecentChange(40, "wizards_and_beasts:gone", "", "Dev", "a", "b", 1L, false, false)));
        ByteBuf buf = Unpooled.buffer();
        AdminSessionInfo.write(buf, sent);
        assertEquals(sent, AdminSessionInfo.read(buf));
        assertEquals(0, buf.readableBytes(), "nothing left over: the reader consumed exactly what the writer wrote");
    }

    @Test
    void onlyTheNewestRecentChangesAreSent() {
        List<AdminSessionInfo.RecentChange> many = new ArrayList<>();
        for (int i = 0; i < AdminSessionInfo.MAX_RECENT + 5; i++) {
            many.add(new AdminSessionInfo.RecentChange(i, "wizards_and_beasts:x", "", "Dev", "0", "1", i, true, true));
        }
        ByteBuf buf = Unpooled.buffer();
        AdminSessionInfo.write(buf, info(many));
        AdminSessionInfo read = AdminSessionInfo.read(buf);
        assertEquals(AdminSessionInfo.MAX_RECENT, read.recentChanges().size());
        assertEquals(0L, read.recentChanges().get(0).sequence());
    }
}
