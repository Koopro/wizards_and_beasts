package at.koopro.wizardsandbeasts.wand.gui;

import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.network.wand.SyncTrialResonancePayload;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolEntry;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolLoader;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class OllivanderTrialMenu extends AbstractContainerMenu {

    private final List<OllivanderPoolEntry> trials;
    private final Player player;
    private final int[] dataBacking = new int[5];
    private final ContainerData data;

    /** Server-side: builds trials and initial resonance scores (fixed 11\" length for scoring). */
    public OllivanderTrialMenu(int containerId, Inventory inv, List<OllivanderPoolEntry> picks) {
        super(ModMenuTypes.OLLIVANDER_TRIAL.get(), containerId);
        this.player = inv.player;
        this.trials = List.copyOf(picks);
        int matchScaled = (int) (WandResonanceConfigLoader.getConfig(inv.player.level().registryAccess()).matchThreshold() * 100.0f);
        dataBacking[0] = -1;
        dataBacking[4] = matchScaled;
        for (int i = 0; i < Math.min(3, picks.size()); i++) {
            ItemStack probe = createTrialStack(i, false);
            float score = WandResonanceSystem.computeResonance(inv.player, probe, inv.player.level().registryAccess());
            dataBacking[1 + i] = (int) (score * 100.0f);
        }
        this.data = backingData();
        addDataSlots(data);
    }

    /** Client-side: from extraData; scores match server open packet. */
    private OllivanderTrialMenu(int containerId, Inventory inv, List<OllivanderPoolEntry> clientTrials, int matchScaled,
                                float s0, float s1, float s2) {
        super(ModMenuTypes.OLLIVANDER_TRIAL.get(), containerId);
        this.player = inv.player;
        this.trials = clientTrials;
        dataBacking[0] = -1;
        dataBacking[4] = matchScaled;
        dataBacking[1] = (int) (s0 * 100.0f);
        dataBacking[2] = (int) (s1 * 100.0f);
        dataBacking[3] = (int) (s2 * 100.0f);
        this.data = backingData();
        addDataSlots(data);
    }

    private ContainerData backingData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return dataBacking[index];
            }

            @Override
            public void set(int index, int value) {
                dataBacking[index] = value;
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
    }

    public static OllivanderTrialMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int matchScaled = buf.readVarInt();
        List<OllivanderPoolEntry> list = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            Identifier wood = Identifier.parse(buf.readUtf(320));
            Identifier core = Identifier.parse(buf.readUtf(320));
            String flex = buf.readUtf(64);
            list.add(new OllivanderPoolEntry(0, wood, core, flex, 0));
        }
        float s0 = buf.readFloat();
        float s1 = buf.readFloat();
        float s2 = buf.readFloat();
        return new OllivanderTrialMenu(containerId, inv, list, matchScaled, s0, s1, s2);
    }

    public static float[] computeInitialScores(Player player, List<OllivanderPoolEntry> picks) {
        float[] r = new float[3];
        for (int i = 0; i < Math.min(3, picks.size()); i++) {
            ItemStack probe = buildProbeStack(player, picks.get(i));
            r[i] = WandResonanceSystem.computeResonance(player, probe, player.level().registryAccess());
        }
        return r;
    }

    private static ItemStack buildProbeStack(Player player, OllivanderPoolEntry e) {
        WandFlexibility flex = parseFlex(e.flexibility());
        ItemStack stack = new ItemStack(WandItemRegistry.WAND.get());
        stack.set(WandComponents.WAND_WOOD.get(), e.woodKey());
        stack.set(WandComponents.WAND_CORE.get(), e.coreKey());
        stack.set(WandComponents.WAND_FLEXIBILITY.get(), flex);
        stack.set(WandComponents.WAND_LENGTH.get(), 11.0f);
        stack.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
        stack.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        ModDataComponents.refreshElderWandMarker(stack);
        return stack;
    }

    public List<OllivanderPoolEntry> getTrials() {
        return trials;
    }

    public int getSelectedIndex() {
        return dataBacking[0];
    }

    public void setSelectedIndex(int index) {
        dataBacking[0] = index;
        broadcastChanges();
    }

    public float getResonanceScore(int slot) {
        return dataBacking[1 + slot] / 100.0f;
    }

    public void setResonanceScore(int slot, float score) {
        dataBacking[1 + slot] = (int) (score * 100.0f);
        broadcastChanges();
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new SyncTrialResonancePayload(
                    containerId,
                    dataBacking[1] / 100.0f,
                    dataBacking[2] / 100.0f,
                    dataBacking[3] / 100.0f));
        }
    }

    public void applySyncedScores(float s0, float s1, float s2) {
        dataBacking[1] = (int) (s0 * 100.0f);
        dataBacking[2] = (int) (s1 * 100.0f);
        dataBacking[3] = (int) (s2 * 100.0f);
    }

    public float getMatchThreshold() {
        return dataBacking[4] / 100.0f;
    }

    /** @param forGift when true, randomise length for the actual gifted wand */
    public ItemStack createTrialStack(int index, boolean forGift) {
        OllivanderPoolEntry e = trials.get(index);
        WandFlexibility flex = parseFlex(e.flexibility());
        ItemStack stack = new ItemStack(WandItemRegistry.WAND.get());
        stack.set(WandComponents.WAND_WOOD.get(), e.woodKey());
        stack.set(WandComponents.WAND_CORE.get(), e.coreKey());
        stack.set(WandComponents.WAND_FLEXIBILITY.get(), flex);
        float len = forGift ? 9.0f + player.getRandom().nextFloat() * 5.0f : 11.0f;
        stack.set(WandComponents.WAND_LENGTH.get(), len);
        stack.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
        stack.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        ModDataComponents.refreshElderWandMarker(stack);
        return stack;
    }

    public ItemStack createTrialStack(int index) {
        return createTrialStack(index, true);
    }

    private static WandFlexibility parseFlex(String s) {
        for (WandFlexibility f : WandFlexibility.values()) {
            if (f.getSerializedName().equalsIgnoreCase(s)) {
                return f;
            }
        }
        return WandFlexibility.SOLID;
    }

    public static List<OllivanderPoolEntry> pickTrials(Player player) {
        List<OllivanderPoolEntry> pool = new ArrayList<>(OllivanderPoolLoader.getPool(player.level().registryAccess()));
        List<OllivanderPoolEntry> eligible = new ArrayList<>();
        for (OllivanderPoolEntry e : pool) {
            if (player.experienceLevel >= e.minimumPlayerLevel()) {
                eligible.add(e);
            }
        }
        if (eligible.isEmpty()) {
            return List.of(
                    new OllivanderPoolEntry(1,
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "holly"),
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn_hair"),
                            "supple", 0),
                    new OllivanderPoolEntry(1,
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "rowan"),
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn_hair"),
                            "solid", 0),
                    new OllivanderPoolEntry(1,
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "ash"),
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "phoenix_feather"),
                            "rigid", 0));
        }
        List<OllivanderPoolEntry> bag = new ArrayList<>(eligible);
        List<OllivanderPoolEntry> picks = new ArrayList<>();
        for (int n = 0; n < 3 && !bag.isEmpty(); n++) {
            float total = 0.0f;
            for (OllivanderPoolEntry e : bag) {
                total += e.weight();
            }
            float r = player.getRandom().nextFloat() * total;
            int chosen = bag.size() - 1;
            for (int i = 0; i < bag.size(); i++) {
                r -= bag.get(i).weight();
                if (r <= 0) {
                    chosen = i;
                    break;
                }
            }
            picks.add(bag.remove(chosen));
        }
        while (picks.size() < 3 && !eligible.isEmpty()) {
            picks.add(eligible.get(player.getRandom().nextInt(eligible.size())));
        }
        return picks;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
