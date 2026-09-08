package at.koopro.wizardsandbeasts.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to {@link ClientInput}'s movement vector.
 *
 * <p>{@code ClientInput.keyPresses} is public but {@code moveVector} is protected, and the two are not
 * the same thing: {@code KeyboardInput.tick()} derives the vector from the keys and
 * {@code LocalPlayer.applyInput} reads only the vector. {@code MovementInputUpdateEvent} fires
 * <em>after</em> that derivation, so clearing the key presses there leaves the vector set and the
 * player still walking.
 *
 * <p>An accessor is the whole of the mixin — no injection, no overwrite, nothing that can conflict with
 * another mod's view of player input. It exists solely so a feral werewolf's own keyboard stops moving
 * them; the server does not depend on it (see {@code FeralController}'s drift guard, which assumes a
 * client that ignores all of this).
 */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("moveVector")
    void wandb$setMoveVector(Vec2 moveVector);
}
