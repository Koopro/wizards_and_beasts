package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How the Profiles pages word what the server sent: setting names, issues, outcomes, times. */
@NullMarked
final class ProfileText {

    static final String KEY = "admin.wizards_and_beasts.profiles.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private ProfileText() {}

    /** The setting's display name, or its id path when this client has no name for it. */
    static Component settingName(String nameKey, String settingId) {
        if (!nameKey.isEmpty() && I18n.exists(nameKey)) {
            return Component.translatable(nameKey);
        }
        Identifier id = Identifier.tryParse(settingId);
        return Component.literal(id == null ? settingId : id.getPath());
    }

    /** "Spell Damage: 1.0 → 1.5". */
    static Component change(AdminProfilePayloads.ChangeRow row) {
        return Component.empty().append(settingName(row.nameKey(), row.settingId()))
                .append(": " + shown(row.from()) + " → " + shown(row.to()));
    }

    static String shown(String value) {
        return value.isEmpty() ? "∅" : value;
    }

    /** An issue as a sentence: its code's wording, then what it concerns. */
    static Component issue(AdminProfilePayloads.IssueRow issue) {
        String codeKey = KEY + "issue." + issue.code();
        MutableComponent out = I18n.exists(codeKey) ? Component.translatable(codeKey) : Component.literal(issue.code());
        String subject = issue.subject();
        if (!subject.isEmpty()) {
            Component what = I18n.exists(subject) ? Component.translatable(subject) : Component.literal(subject);
            out = out.append(": ").append(what);
        }
        if (!issue.detail().isEmpty() && !I18n.exists(subject)) {
            out = out.append(" (" + issue.detail() + ")");
        }
        return out;
    }

    /** The outcome of an action, worded for the administrator. */
    static Component outcome(AdminProfilePayloads.ActionReply reply) {
        String key = KEY + "outcome." + reply.outcome();
        if (reply.ok()) {
            String okKey = KEY + "done." + reply.op().name().toLowerCase(Locale.ROOT);
            return Component.translatable(I18n.exists(okKey) ? okKey : key, reply.id(), reply.changed());
        }
        return I18n.exists(key) ? Component.translatable(key, reply.id()) : Component.literal(reply.outcome() + ": " + reply.id());
    }

    static String time(long millis) {
        return millis <= 0 ? "—" : TIME.format(Instant.ofEpochMilli(millis));
    }

    static Component kind(at.koopro.wizardsandbeasts.admin.profile.ProfileDocument.Kind kind) {
        return Component.translatable(KEY + "kind." + kind.name().toLowerCase(Locale.ROOT));
    }
}
