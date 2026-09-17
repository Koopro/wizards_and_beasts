package at.koopro.wizardsandbeasts.spell.study;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.spell.SpellSourceItem;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * The desk a wizard writes a spell down at.
 *
 * <h2>What this used to be</h2>
 * The spell teacher: right-clicking it opened a catalogue of every spell you were eligible for and
 * sold you one for Knuts. The vendor is gone, and with it the screen and both of its packets. The
 * block is <em>not</em> gone — deleting a registered block turns every placed copy into air, and
 * orphans a sprite, a recipe, an advancement, a loot table and a bench-enhancer entry — so it keeps
 * its id ({@code wizards_and_beasts:spell_teacher}) and its lectern model and takes the other half
 * of the job instead.
 *
 * <h2>What it does now</h2>
 * Reading a spell source happens anywhere, in hand ({@link SpellSourceItem}). <em>Writing</em> one
 * happens here: hold a blank Standard Book of Spells, know the spell in your active slot, spend an
 * ink bottle, and the book is now that spell's book. That is what closes the loop — the first copy
 * of Lumos on a server is found, and every copy after it is written by somebody who already knows it.
 *
 * <p>It costs an {@link MiscItemRegistry#INK_BOTTLE} and nothing else. A second currency for spells
 * is exactly what was just removed; ink is a consumable a scribe uses up, not a price.
 *
 * <p>The class name and the registry id deliberately disagree. The id is frozen by every save that
 * has one placed, the same way {@code wand_wood_legacy} is frozen by every wand that has one.
 *
 * <h2>Geometry (unchanged)</h2>
 * Horizontally directional, and it has to be: the model is a lectern with a <em>tilted reading
 * surface</em>, so unlike a cauldron it has a front. Without the property every one placed faced
 * north and a row of them along a wall read as a mistake.
 *
 * <p>{@link #FACING} is the side the reader stands on — {@code getHorizontalDirection().getOpposite()},
 * matching vanilla's own lectern and {@code OccamyEggshellBlock} — so the desk tilts up toward
 * whoever placed it. North is the unrotated model, which is the face
 * {@code BlockModelGenerators.ROTATION_HORIZONTAL_FACING} assumes.
 *
 * <p>{@code rotate}/{@code mirror} come from {@link HorizontalDirectionalBlock}, so a structure
 * block or a clone with a rotation turns one correctly with no override here.
 *
 * <p>The collision shape is unchanged by facing on purpose: all three of its boxes are centred on
 * the block, so a per-facing shape would be four copies of one value.
 */
public class SpellScriptoriumBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SpellScriptoriumBlock> CODEC = simpleCodec(SpellScriptoriumBlock::new);

    /** Foot, column and tilted reading surface — the lectern the model draws. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(5, 2, 5, 11, 10, 11),
            Block.box(2, 10, 2, 14, 13, 14));

    public SpellScriptoriumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Empty-handed, the desk says what it wants. It used to open a shop, so a player who knew the
     * old block will click it this way first and deserves an answer rather than nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("block.wizards_and_beasts.spell_teacher.usage")
                            .withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Scribes the player's active spell into a blank source held against the desk.
     *
     * <p>The spell comes from the active loadout slot rather than a menu: the wheel already is the
     * "which spell am I thinking about" control, and a second picker for the same question is a
     * second thing to keep in step.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof SpellSourceItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        Component refusal = scribeRefusal(serverPlayer, stack);
        if (refusal != null) {
            PlayerFeedback.actionBar(serverPlayer, refusal.copy().withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        PlayerSpellData data = serverPlayer.getData(ModAttachments.SPELL_DATA.get());
        Spell spell = data.getActiveSpell();
        ItemStack written = stack.split(1);
        SpellSource.write(written, spell);
        consumeInk(serverPlayer);
        giveOrDrop(serverPlayer, written);

        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.0f);
        PlayerFeedback.unlocked(serverPlayer,
                Component.translatable("block.wizards_and_beasts.spell_teacher.scribed",
                        SpellSource.writtenName(spell.getId())),
                null);
        return InteractionResult.SUCCESS;
    }

    /** Why this scribing cannot happen, or {@code null} if it can. */
    private static @Nullable Component scribeRefusal(ServerPlayer player, ItemStack stack) {
        if (!SpellSource.isBlank(stack)) {
            return Component.translatable("block.wizards_and_beasts.spell_teacher.already_written");
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        Spell spell = data.getActiveSpell();
        if (spell == null) {
            return Component.translatable("block.wizards_and_beasts.spell_teacher.no_active_spell");
        }
        if (!data.knowsSpell(spell.getId())) {
            return Component.translatable("block.wizards_and_beasts.spell_teacher.unknown_spell");
        }
        if (!player.getAbilities().instabuild && findInkSlot(player) < 0) {
            return Component.translatable("block.wizards_and_beasts.spell_teacher.no_ink");
        }
        return null;
    }

    private static void consumeInk(Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        int slot = findInkSlot(player);
        if (slot >= 0) {
            player.getInventory().getItem(slot).shrink(1);
        }
    }

    /**
     * Scans the whole inventory rather than the off-hand, unlike {@code BroomPolishItem}: which broom
     * gets the tin is a decision, but every ink bottle is the same ink bottle and there is nothing
     * for the player to choose.
     */
    private static int findInkSlot(Player player) {
        Item ink = MiscItemRegistry.INK_BOTTLE.get();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ink)) {
                return slot;
            }
        }
        return -1;
    }

    /** The written copy came out of the held stack, so it must land somewhere it cannot be lost. */
    private static void giveOrDrop(Player player, ItemStack written) {
        if (!player.getInventory().add(written)) {
            player.drop(written, false);
        }
    }
}
