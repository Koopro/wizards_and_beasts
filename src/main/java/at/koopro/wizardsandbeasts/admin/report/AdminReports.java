package at.koopro.wizardsandbeasts.admin.report;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Every {@link AdminReport}, by id, in registration order. Filled once by {@link BuiltinReports#registerAll}; a later
 * module may {@link #register} more. Shared by the service, the command's suggestions and the tests.
 */
@NullMarked
public final class AdminReports {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Map<String, AdminReport> BY_ID = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private AdminReports() {}

    public static synchronized void register(AdminReport report) {
        if (!ID.matcher(report.id()).matches()) {
            throw new IllegalArgumentException("Bad admin report id " + report.id());
        }
        if (BY_ID.putIfAbsent(report.id(), report) != null) {
            throw new IllegalStateException("Admin report " + report.id() + " registered twice");
        }
    }

    /** Registers the built-in reports the first time anything asks. */
    public static synchronized void bootstrap() {
        if (!bootstrapped) {
            bootstrapped = true;
            BuiltinReports.registerAll();
        }
    }

    public static synchronized @Nullable AdminReport byId(String id) {
        bootstrap();
        return BY_ID.get(id);
    }

    public static synchronized List<AdminReport> all() {
        bootstrap();
        return new ArrayList<>(BY_ID.values());
    }

    public static boolean validId(String id) {
        return ID.matcher(id).matches();
    }
}
