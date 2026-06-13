package at.koopro.wizardsandbeasts.item.map;

import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.map.MaraudersMapTracker;
import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class MaraudersMapItem extends GeoItemBase {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final int DEFAULT_RADIUS = 128;

    private static final RawAnimation FOLDED_IDLE =
            RawAnimation.begin().thenLoop("animation.marauders_map.folded_idle");
    private static final RawAnimation UNFOLDING =
            RawAnimation.begin().thenPlay("animation.marauders_map.unfolding");
    private static final RawAnimation OPEN_IDLE =
            RawAnimation.begin().thenLoop("animation.marauders_map.open_idle");

    private static final List<String> INSULTS = List.of(
            "Mr. Moony presents his compliments to %s, and begs them to keep their abnormally large nose out of other people's business.",
            "Mr. Prongs agrees with Mr. Moony, and would like to add that %s is an ugly git.",
            "Mr. Padfoot would like to register his astonishment that an idiot like %s ever became a player of note.",
            "Mr. Wormtail bids %s good day, and advises them to wash their hair, the slimeball."
    );

    public MaraudersMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            initializeIfNeeded(stack, player, serverLevel);

            if (isTrusted(stack, player.getUUID())) {
                BlockPos center = getBoundPos(stack);
                Identifier dimension = getBoundDimension(stack);

                MaraudersMapTracker.addPlayer(
                        (ServerPlayer) player, center, DEFAULT_RADIUS, dimension);

                PacketDistributor.sendToPlayer(
                        (ServerPlayer) player,
                        new MapOpenS2CPayload(center, DEFAULT_RADIUS, dimension));

                triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel),
                        "map_controller", "unfold");
            } else {
                sendInsult(stack, player);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private void initializeIfNeeded(ItemStack stack, Player player, ServerLevel level) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains("BoundX")) {
            tag.putInt("BoundX", player.blockPosition().getX());
            tag.putInt("BoundZ", player.blockPosition().getZ());
            tag.putString("BoundDimension", level.dimension().identifier().toString());

            ListTag trusted = new ListTag();
            trusted.add(StringTag.valueOf(player.getUUID().toString()));
            tag.put("TrustedPlayers", trusted);
            tag.putInt("InsultIndex", 0);

            setCustomTag(stack, tag);
        }
    }

    public static boolean isTrusted(ItemStack stack, UUID playerUuid) {
        CompoundTag tag = getCustomTag(stack);
        ListTag trusted = tag.getList("TrustedPlayers").orElse(new ListTag());
        String uuidStr = playerUuid.toString();
        for (int i = 0; i < trusted.size(); i++) {
            if (trusted.getString(i).orElse("").equals(uuidStr)) {
                return true;
            }
        }
        return false;
    }

    public static void addTrusted(ItemStack stack, UUID playerUuid) {
        if (isTrusted(stack, playerUuid)) return;
        CompoundTag tag = getCustomTag(stack);
        ListTag trusted = tag.getList("TrustedPlayers").orElse(new ListTag());
        trusted.add(StringTag.valueOf(playerUuid.toString()));
        tag.put("TrustedPlayers", trusted);
        setCustomTag(stack, tag);
    }

    public static void removeTrusted(ItemStack stack, UUID playerUuid) {
        CompoundTag tag = getCustomTag(stack);
        ListTag trusted = tag.getList("TrustedPlayers").orElse(new ListTag());
        String uuidStr = playerUuid.toString();
        ListTag newTrusted = new ListTag();
        for (int i = 0; i < trusted.size(); i++) {
            if (!trusted.getString(i).orElse("").equals(uuidStr)) {
                newTrusted.add(trusted.get(i));
            }
        }
        tag.put("TrustedPlayers", newTrusted);
        setCustomTag(stack, tag);
    }

    public static List<UUID> getTrustedPlayers(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        ListTag trusted = tag.getList("TrustedPlayers").orElse(new ListTag());
        List<UUID> result = new ArrayList<>();
        for (int i = 0; i < trusted.size(); i++) {
            try {
                result.add(UUID.fromString(trusted.getString(i).orElse("")));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[WizardsAndBeasts] Skipping malformed trusted-player UUID '{}' on Marauder's Map",
                        trusted.getString(i).orElse(""));
            }
        }
        return result;
    }

    private void sendInsult(ItemStack stack, Player player) {
        CompoundTag tag = getCustomTag(stack);
        int index = tag.getInt("InsultIndex").orElse(0);
        String insult = String.format(
                INSULTS.get(index % INSULTS.size()),
                player.getName().getString());
        player.displayClientMessage(
                Component.literal("\u00A76\u00A7o" + insult), false);
        tag.putInt("InsultIndex", (index + 1) % INSULTS.size());
        setCustomTag(stack, tag);
    }

    public static BlockPos getBoundPos(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return new BlockPos(
                tag.getInt("BoundX").orElse(0), 0,
                tag.getInt("BoundZ").orElse(0));
    }

    public static Identifier getBoundDimension(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        String dim = tag.getString("BoundDimension").orElse("");
        return dim.isEmpty()
                ? Identifier.fromNamespaceAndPath("minecraft", "overworld")
                : Identifier.parse(dim);
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // -- GeckoLib --

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = ClientClassBridge.instantiate(
                            "at.koopro.wizardsandbeasts.client.map.MaraudersMapRenderer",
                            GeoItemRenderer.class,
                            new Class<?>[0],
                            new Object[0]);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MaraudersMapItem>(
                "map_controller", 5,
                state -> state.setAndContinue(FOLDED_IDLE))
                .triggerableAnim("unfold", UNFOLDING)
                .triggerableAnim("fold", FOLDED_IDLE));
    }

}
