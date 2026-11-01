package at.koopro.wizardsandbeasts.chamber;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated Chamber template ({@code tools/gen_chamber_nbt.py}) agrees with the code that runs it: one heart
 * facing up its hall, the seal and its knocker, the basin, comparator and gate in a working line, and a hall the
 * heart's measurements fit — the basilisk wakes on open floor inside it, and the landing is outside it.
 */
class ChamberTemplateTest {

    private static final Path TEMPLATE = Path.of("src", "main", "resources", "data", "wizards_and_beasts",
            "structure", "chamber_of_secrets", "chamber.nbt");
    private static final Map<String, String> AT = new HashMap<>();
    private static final Map<String, CompoundTag> STATE = new HashMap<>();
    private static final String SEAL = "wizards_and_beasts:chamber_seal";
    private static final String GATE = "wizards_and_beasts:chamber_gate";
    private static final Set<String> WALKABLE = Set.of("minecraft:air", "minecraft:water", "minecraft:ladder",
            "minecraft:lantern", "minecraft:soul_lantern", "minecraft:spruce_wall_sign",
            "minecraft:polished_blackstone_button", "minecraft:lever");

    @BeforeAll
    static void read() throws IOException {
        CompoundTag root = NbtIo.readCompressed(TEMPLATE, NbtAccounter.unlimitedHeap());
        ListTag palette = root.getListOrEmpty("palette");
        ListTag blocks = root.getListOrEmpty("blocks");
        for (Tag tag : blocks) {
            CompoundTag block = (CompoundTag) tag;
            List<Integer> pos = block.getListOrEmpty("pos").stream()
                    .map(t -> t.asInt().orElseThrow()).toList();
            CompoundTag state = palette.getCompoundOrEmpty(block.getIntOr("state", -1));
            String key = pos.get(0) + "," + pos.get(1) + "," + pos.get(2);
            AT.put(key, state.getStringOr("Name", ""));
            STATE.put(key, state);
        }
    }

    private static String at(int x, int y, int z) {
        return AT.getOrDefault(x + "," + y + "," + z, "structure_void");
    }

    private static String prop(int x, int y, int z, String name) {
        return STATE.get(x + "," + y + "," + z).getCompoundOrEmpty("Properties").getStringOr(name, "");
    }

    @Test
    void oneHeartFacingUpItsHall() {
        long hearts = AT.values().stream().filter("wizards_and_beasts:chamber_heart"::equals).count();
        assertEquals(1, hearts);
        assertEquals("wizards_and_beasts:chamber_heart", at(32, 8, 37));
        assertEquals("north", prop(32, 8, 37, "facing"));
    }

    @Test
    void theSealHasTwoBlocksAndAKnockerOnTheWallAboveIt() {
        assertEquals("wizards_and_beasts:chamber_seal", at(34, 7, 6));
        assertEquals("wizards_and_beasts:chamber_seal", at(34, 8, 6));
        assertEquals("minecraft:stone_bricks", at(34, 9, 6), "the knocker must be set in the block above the seal");
        assertEquals("minecraft:polished_blackstone_button", at(33, 9, 6));
        assertEquals("west", prop(33, 9, 6, "facing"), "the knocker must hang on the seal's wall");
    }

    @Test
    void theBasinComparatorAndGateAreInALine() {
        assertEquals("minecraft:cauldron", at(35, 7, 8));
        assertEquals("minecraft:comparator", at(35, 7, 9));
        assertEquals("north", prop(35, 7, 9, "facing"), "the comparator must read the basin behind it");
        assertEquals("wizards_and_beasts:chamber_gate", at(35, 7, 10), "the comparator must feed a gate block");
    }

    @Test
    void theBasilisksWakingPlaceIsOpenFloorInsideTheHall() {
        // Heart (32, 8, 37) facing north: SPAWN_FORWARD north, SPAWN_UP up.
        int x = 32;
        int y = 8 + ChamberHeartBlockEntity.SPAWN_UP;
        int z = 37 - ChamberHeartBlockEntity.SPAWN_FORWARD;
        for (int dy = 0; dy < 3; dy++) {
            assertEquals("minecraft:air", at(x, y + dy, z), "the basilisk would wake inside " + at(x, y + dy, z));
        }
        assertTrue(at(x, y - 1, z).startsWith("minecraft:") && !at(x, y - 1, z).equals("minecraft:water")
                && !at(x, y - 1, z).equals("minecraft:air"), "no floor under the basilisk");
    }

    /** What a player can walk through. Everything else, the ground around the rooms included, stops them. */
    private static boolean passable(String block, Set<String> alsoOpen) {
        return WALKABLE.contains(block) || alsoOpen.contains(block);
    }

    /** Every cell reachable from the landing by moving a block at a time. */
    private static Set<String> reachableFromLanding(String... alsoOpen) {
        Set<String> open = Set.of(alsoOpen);
        Set<String> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {22, 7, 22});
        seen.add("22,7,22");
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                int z = at[2] + step[2];
                String key = x + "," + y + "," + z;
                if (!seen.contains(key) && passable(at(x, y, z), open)) {
                    seen.add(key);
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        return seen;
    }

    @Test
    void theSealAndTheGateAreTheOnlyWayOnward() {
        Set<String> closed = reachableFromLanding();
        assertTrue(closed.contains("33,7,5"), "the landing does not reach the seal's end of the pipes");
        assertFalse(closed.contains("37,7,5"), "the basin room can be walked into past the closed seal");
        assertFalse(closed.contains("32,7,25"), "the hall can be walked into with everything closed");

        Set<String> sealOpen = reachableFromLanding(SEAL);
        assertTrue(sealOpen.contains("37,7,5"), "the open seal does not lead to the basin room");
        assertFalse(sealOpen.contains("32,7,25"), "the hall can be walked into past the closed gate");

        Set<String> bothOpen = reachableFromLanding(SEAL, GATE);
        assertTrue(bothOpen.contains("32,7,25"), "the open gate does not lead to the hall");
        assertTrue(bothOpen.contains("32,9,36"), "the dais cannot be stood on");
        assertTrue(bothOpen.contains("26,7,21"), "the shortcut lever cannot be reached");
    }

    @Test
    void theShortcutIsTheWayOutOfTheHall() {
        assertEquals("minecraft:iron_door", at(25, 7, 20));
        assertEquals("minecraft:lever", at(26, 8, 21));
        assertEquals("east", prop(26, 8, 21, "facing"), "the lever must hang on the hall's side of the wall");
        assertEquals("minecraft:air", at(24, 7, 20), "the niche behind the door must open into the landing");
    }

    @Test
    void everyRoomIsSealedFromTheGroundAroundIt() {
        List<String> gaps = new ArrayList<>();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (String key : AT.keySet()) {
            if (!WALKABLE.contains(AT.get(key))) {
                continue;
            }
            String[] p = key.split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            for (int[] step : steps) {
                if (at(x + step[0], y + step[1], z + step[2]).equals("structure_void")) {
                    gaps.add(key + " opens onto the ground towards " + step[0] + "," + step[1] + "," + step[2]);
                }
            }
        }
        assertTrue(gaps.isEmpty(), gaps.size() + " gaps, first: " + gaps.stream().limit(6).toList());
    }

    @Test
    void waterStaysInItsChannels() {
        for (String key : AT.keySet()) {
            if (!AT.get(key).equals("minecraft:water")) {
                continue;
            }
            String[] p = key.split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            assertTrue(Set.of("minecraft:stone_bricks", "minecraft:deepslate_tiles", "minecraft:mossy_stone_bricks",
                            "minecraft:cracked_stone_bricks").contains(at(x, y - 1, z)),
                    "water at " + key + " sits on " + at(x, y - 1, z) + " and would run away");
        }
    }

    @Test
    void theGeneratedPlacementPutsTheRingOnTheGround() throws IOException {
        Path resources = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
        JsonObject structure;
        try (Reader reader = Files.newBufferedReader(resources.resolve(
                Path.of("worldgen", "structure", "chamber_of_secrets.json")))) {
            structure = JsonParser.parseReader(reader).getAsJsonObject();
        }
        // Jigsaw puts template y 0 at surface + start_height, so the ring's y must cancel it.
        int ringY = -structure.getAsJsonObject("start_height").getAsJsonObject("value").get("absolute").getAsInt();
        assertEquals("minecraft:spruce_trapdoor", at(20, ringY, 20), "the entrance is not at ground level");
        assertEquals("WORLD_SURFACE_WG", structure.get("project_start_to_heightmap").getAsString());
        assertEquals("minecraft:ladder", at(20, ringY - 1, 20), "the shaft must sit at the piece's centre, "
                + "where the heightmap is sampled");
        assertEquals(0, structure.getAsJsonObject("spawn_overrides").size(),
                "a natural spawn override would put an unbound basilisk in the Chamber");
        assertEquals(1, structure.get("size").getAsInt());
        // Not underground_structures: mineshafts generate in that step too, after a Chamber, and on real terrain one cut
        // through the walls and let the hall be walked into with the seal and gate shut.
        assertEquals("underground_decoration", structure.get("step").getAsString(),
                "the Chamber must generate after mineshafts and ore veins, or they cut through its walls");
    }

    @Test
    void theHeartsHallCoversTheHallAndNotTheLanding() {
        int forwardMax = 37 - ChamberHeartBlockEntity.FORWARD_MAX;   // northmost z the heart watches
        assertTrue(forwardMax <= 15, "the heart does not see the hall's north end");
        assertTrue(forwardMax > 13, "the heart's hall reaches past the gate into the passage");
        assertTrue(32 - ChamberHeartBlockEntity.LATERAL > 23, "the heart's hall reaches the landing");
        assertNotNull(STATE.get("17,7,17"), "setup: the landing is not where this test thinks");
    }
}
