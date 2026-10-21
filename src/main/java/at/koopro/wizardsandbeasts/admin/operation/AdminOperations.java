package at.koopro.wizardsandbeasts.admin.operation;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Every {@link AdminOperation}, by id. Filled once by {@link BuiltinOperations#registerAll}. */
@NullMarked
public final class AdminOperations {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Map<String, AdminOperation> BY_ID = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private AdminOperations() {}

    public static synchronized void register(AdminOperation operation) {
        if (!ID.matcher(operation.id()).matches()) {
            throw new IllegalArgumentException("Bad admin operation id " + operation.id());
        }
        if (BY_ID.putIfAbsent(operation.id(), operation) != null) {
            throw new IllegalStateException("Admin operation " + operation.id() + " registered twice");
        }
    }

    public static synchronized void bootstrap() {
        if (!bootstrapped) {
            bootstrapped = true;
            BuiltinOperations.registerAll();
        }
    }

    public static synchronized @Nullable AdminOperation byId(String id) {
        bootstrap();
        return BY_ID.get(id);
    }

    public static synchronized List<AdminOperation> all() {
        bootstrap();
        return new ArrayList<>(BY_ID.values());
    }

    public static boolean validId(String id) {
        return ID.matcher(id).matches();
    }
}
