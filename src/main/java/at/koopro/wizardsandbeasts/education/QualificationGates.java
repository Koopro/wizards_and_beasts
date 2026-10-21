package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.ProfessionEligibility.Requirement;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a qualification unlocks beyond careers: advanced brews and the skill web's capstones.
 *
 * <p>Data, in {@code data/<ns>/qualification_gates/*.json}:
 * <pre>{@code
 * { "gates": [
 *   { "kind": "brew", "id": "wizards_and_beasts:felix_felicis",
 *     "requires": [ { "level": "newt", "subject": "potions", "grade": "A" } ] } ] }
 * }</pre>
 * A brew gate is checked when the pot is put on the heat, a skill-node gate when the node is allocated
 * ({@code SkillSystemAPI.evaluateUnlock}). Ordinary brews and the web below its capstones carry no gate — grades
 * open the advanced end of a discipline, never its everyday use. With the O.W.L. module off no gate is checked:
 * no examinations, no examination requirements.
 */
@NullMarked
public final class QualificationGates {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DIRECTORY = "qualification_gates";

    /** What a gate stands in front of. */
    public enum Kind implements StringRepresentable {
        BREW, SKILL_NODE;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Codec<OWLGrade> GRADE = Codec.STRING.comapFlatMap(raw -> {
        try {
            return DataResult.success(OWLGrade.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown grade: " + raw);
        }
    }, OWLGrade::name);

    public static final Codec<Requirement> REQUIREMENT_CODEC = RecordCodecBuilder.create(i -> i.group(
            OWLSubject.CODEC.fieldOf("subject").forGetter(Requirement::subject),
            GRADE.fieldOf("grade").forGetter(Requirement::minGrade),
            Qualification.CODEC.optionalFieldOf("level", Qualification.OWL).forGetter(Requirement::level)
    ).apply(i, Requirement::new));

    /** One gate. Ids are compared as written: a brew by its full id, a skill node by its id ({@code healers_hands}). */
    public record Gate(Kind kind, String id, List<Requirement> requires) {
        public static final Codec<Gate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(Gate::kind),
                Codec.STRING.fieldOf("id").forGetter(Gate::id),
                REQUIREMENT_CODEC.listOf().fieldOf("requires").forGetter(Gate::requires)
        ).apply(i, Gate::new));
    }

    public record GateFile(List<Gate> gates) {
        public static final Codec<GateFile> CODEC = Gate.CODEC.listOf().fieldOf("gates").xmap(GateFile::new, GateFile::gates)
                .codec();
    }

    private static volatile Map<String, List<Requirement>> gates = Map.of();

    private QualificationGates() {}

    private static String key(Kind kind, String id) {
        return kind.getSerializedName() + "|" + id;
    }

    /** Replaces every gate (the loader, and tests). */
    public static void replace(List<Gate> all) {
        Map<String, List<Requirement>> next = new HashMap<>();
        for (Gate gate : all) {
            next.merge(key(gate.kind(), gate.id()), List.copyOf(gate.requires()), (a, b) -> {
                java.util.ArrayList<Requirement> both = new java.util.ArrayList<>(a);
                both.addAll(b);
                return List.copyOf(both);
            });
        }
        gates = Map.copyOf(next);
    }

    /** The requirements in front of {@code id}; empty when ungated. */
    public static List<Requirement> requirements(Kind kind, String id) {
        return gates.getOrDefault(key(kind, id), List.of());
    }

    /** The first requirement these sheets do not meet, or {@code null}. Pure over its arguments. */
    public static @Nullable Requirement firstUnmet(List<Requirement> requires, Map<OWLSubject, OWLGrade> owl,
                                                   Map<OWLSubject, OWLGrade> newt) {
        for (Requirement r : requires) {
            if (!r.metBy(owl, newt)) return r;
        }
        return null;
    }

    /** The first requirement {@code player} does not meet for {@code id}, or {@code null}; always null with O.W.L.s off. */
    public static @Nullable Requirement firstUnmet(ServerPlayer player, Kind kind, String id) {
        List<Requirement> requires = requirements(kind, id);
        if (requires.isEmpty() || !ModuleManager.isEnabled(Module.OWLS)) return null;
        PlayerOWLData record = player.getData(ModAttachments.OWL_DATA.get());
        return firstUnmet(requires, record.grades(), record.newtGrades());
    }

    /** "Needs N.E.W.T. Potions: Acceptable", for a refusal line. */
    public static Component describe(Requirement r) {
        return Component.translatable("education.gate.needs", Component.translatable(r.level().translationKey()),
                Component.translatable(r.subject().translationKey()), Component.translatable(r.minGrade().translationKey()));
    }

    /** Loads {@code qualification_gates/*.json}. */
    public static final class Loader extends SimpleJsonResourceReloadListener<GateFile> {
        public Loader() {
            super(GateFile.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, GateFile> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Gate> all = files.values().stream().flatMap(f -> f.gates().stream()).toList();
            replace(all);
            LOGGER.info("QualificationGates: {} gates from {} files.", all.size(), files.size());
        }
    }
}
