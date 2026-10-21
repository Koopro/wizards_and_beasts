package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

/**
 * A read-only page of live facts the Control Center shows and {@code /wandb admin report <id>} prints — one
 * implementation behind both. A report reads the owning systems at the moment of asking and keeps nothing.
 *
 * <p>To add one: implement this (or use {@link #of}), register it in {@link BuiltinReports}, add its lang keys
 * ({@code admin.wizards_and_beasts.report.<id>.*}), and put an {@code AdminReportPanel} for it on a section's tab.
 * Nothing in the network, command or client code changes.
 */
@NullMarked
public interface AdminReport {

    /** Stable id: wire, command argument and lang key root. Lower-case, {@code [a-z0-9_]}. */
    String id();

    AdminCategory section();

    /** What reading this report needs. Defaults to the section's capability. */
    default AdminCapability capability() {
        return section().defaultCapability();
    }

    /** Builds the view for {@code request}. Runs on the server thread; may throw (the service turns it into a refusal). */
    void build(Request request, ReportBuilder out);

    /**
     * One read.
     *
     * @param subject what the report is pointed at ({@code ""} for none): a player name or UUID, an entity UUID …
     */
    record Request(AdminContext actor, MinecraftServer server, String subject) {}

    @FunctionalInterface
    interface Body {
        void build(Request request, ReportBuilder out);
    }

    static AdminReport of(String id, AdminCategory section, AdminCapability capability, Body body) {
        return new AdminReport() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public AdminCategory section() {
                return section;
            }

            @Override
            public AdminCapability capability() {
                return capability;
            }

            @Override
            public void build(Request request, ReportBuilder out) {
                body.build(request, out);
            }
        };
    }

    static AdminReport of(String id, AdminCategory section, Body body) {
        return of(id, section, section.defaultCapability(), body);
    }
}
