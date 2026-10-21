package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.ministry.trace.CaseReport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Cases page on the wire: what is written is what is read, and a hostile count is refused. */
class AdminMinistryPayloadsTest {

    @Test
    void aSnapshotSurvivesTheWire() {
        CaseReport.Snapshot snapshot = new CaseReport.Snapshot(List.of(
                new CaseReport.CaseRow(12L, "case", "Harry", "stupefy", "restricted", "aurors_assigned (cold)", 80, 50,
                        "Tonks", "overworld 1,64,2", "official_report, wizard_testimony(Ron)"),
                new CaseReport.CaseRow(13L, "posted", "", "imperio", "unforgivable", "", 0, 75, "", "overworld 9,9,9",
                        "")), 7, 2, 3);
        ByteBuf buf = Unpooled.buffer();
        AdminMinistryPayloads.write(buf, snapshot);
        assertEquals(snapshot, AdminMinistryPayloads.read(buf));
    }

    @Test
    void anOversizedListIsCutOnSendAndRefusedOnRead() {
        List<CaseReport.CaseRow> rows = new ArrayList<>();
        for (int i = 0; i < AdminMinistryPayloads.MAX_ROWS + 10; i++) {
            rows.add(new CaseReport.CaseRow(i, "case", "", "", "", "", 0, 0, "", "", ""));
        }
        ByteBuf buf = Unpooled.buffer();
        AdminMinistryPayloads.write(buf, new CaseReport.Snapshot(rows, 0, 0, 0));
        assertEquals(AdminMinistryPayloads.MAX_ROWS, AdminMinistryPayloads.read(buf).rows().size());

        ByteBuf hostile = Unpooled.buffer();
        hostile.writeInt(Integer.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> AdminMinistryPayloads.read(hostile));
    }
}
