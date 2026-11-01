package at.koopro.wizardsandbeasts.settlement.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hamlet's templates and pools agree: the plaza asks for every required role by its own pool, each pool names real
 * templates, and each building has the one connector that lets jigsaw hang it on the plaza. A broken link here is a
 * building that silently never appears.
 */
class SettlementLayoutTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path TEMPLATES = DATA.resolve("structure/wizarding_hamlet");
    private static final Path POOLS = DATA.resolve("worldgen/template_pool/wizarding_hamlet");
    private static final String POOL_PREFIX = "wizards_and_beasts:wizarding_hamlet/";

    private static List<CompoundTag> jigsaws(Path template) throws IOException {
        CompoundTag root = NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap());
        List<CompoundTag> found = new ArrayList<>();
        for (Tag tag : root.getListOrEmpty("blocks")) {
            CompoundTag nbt = ((CompoundTag) tag).getCompoundOrEmpty("nbt");
            if (nbt.getStringOr("id", "").equals("minecraft:jigsaw")) {
                found.add(nbt);
            }
        }
        return found;
    }

    private static List<String> templatesOf(String role) throws IOException {
        JsonObject pool = JsonParser.parseString(Files.readString(POOLS.resolve(role + ".json"))).getAsJsonObject();
        List<String> names = new ArrayList<>();
        pool.getAsJsonArray("elements").forEach(entry -> {
            JsonObject element = entry.getAsJsonObject().getAsJsonObject("element");
            if (element.has("location")) {
                names.add(element.get("location").getAsString().replace("wizards_and_beasts:wizarding_hamlet/", ""));
            }
        });
        return names;
    }

    @Test
    void thePlazaAsksForEveryRoleExactlyOnce() throws IOException {
        for (String plaza : templatesOf("plaza")) {
            List<String> asked = jigsaws(TEMPLATES.resolve(plaza + ".nbt")).stream()
                    .map(nbt -> nbt.getStringOr("pool", "")).toList();
            for (String role : SettlementSite.REQUIRED_ROLES) {
                if (!role.equals("plaza")) {
                    assertEquals(1, asked.stream().filter((POOL_PREFIX + role)::equals).count(), role);
                }
            }
        }
    }

    @Test
    void everyPoolNamesTemplatesThatExist() throws IOException {
        for (String role : SettlementSite.REQUIRED_ROLES) {
            List<String> templates = templatesOf(role);
            assertFalse(templates.isEmpty(), role);
            for (String template : templates) {
                assertTrue(Files.exists(TEMPLATES.resolve(template + ".nbt")), template);
                assertEquals(role, template.split("/")[0], "a pool holds one role's variants");
            }
        }
    }

    private static List<String> postRoles(Path template) throws IOException {
        CompoundTag root = NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap());
        List<String> found = new ArrayList<>();
        for (Tag tag : root.getListOrEmpty("blocks")) {
            CompoundTag nbt = ((CompoundTag) tag).getCompoundOrEmpty("nbt");
            if (nbt.getStringOr("id", "").equals("wizards_and_beasts:settlement_post")) {
                found.add(nbt.getStringOr("role", ""));
            }
        }
        return found;
    }

    @Test
    void everyBuildingHasOnePostAndItsRoleHasAFile() throws IOException {
        for (String role : SettlementSite.REQUIRED_ROLES) {
            for (String template : templatesOf(role)) {
                List<String> posts = postRoles(TEMPLATES.resolve(template + ".nbt"));
                assertEquals(role.equals("plaza") ? 0 : 1, posts.size(), template);
                for (String post : posts) {
                    String file = post.replace("wizards_and_beasts:", "") + ".json";
                    assertTrue(Files.exists(DATA.resolve("npc_roles").resolve(file)), template + " names " + post);
                }
            }
        }
    }

    @Test
    void everyBuildingHasTheConnectorThePlazaLooksFor() throws IOException {
        List<CompoundTag> plaza = jigsaws(TEMPLATES.resolve(templatesOf("plaza").get(0) + ".nbt"));
        String target = plaza.get(0).getStringOr("target", "");
        try (Stream<Path> roles = Files.list(TEMPLATES)) {
            for (Path role : roles.toList()) {
                if (role.getFileName().toString().equals("plaza")) {
                    continue;
                }
                try (Stream<Path> files = Files.list(role)) {
                    for (Path template : files.toList()) {
                        long doors = jigsaws(template).stream()
                                .filter(nbt -> nbt.getStringOr("name", "").equals(target)).count();
                        assertEquals(1, doors, template.toString());
                    }
                }
            }
        }
    }
}
