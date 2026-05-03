package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.data.PlayerVaultData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WizardsAndBeastsMod.MODID);

    public static final Supplier<AttachmentType<PlayerSpellData>> SPELL_DATA =
            registerData("spell_data", PlayerSpellData::new);

    public static final Supplier<AttachmentType<PlayerVaultData>> VAULT_DATA =
            registerData("vault_data", PlayerVaultData::new);

    public static final Supplier<AttachmentType<PlayerTypeData>> TYPE_DATA =
            registerData("type_data", PlayerTypeData::new);

    public static final Supplier<AttachmentType<PlayerSkillData>> SKILL_DATA =
            registerData("skill_data", PlayerSkillData::new);

    private ModAttachments() {
    }

    /**
     * Generic registration for any player data type that implements save()/load().
     */
    private static <T extends NbtSerializable> Supplier<AttachmentType<T>> registerData(
            String name, Supplier<T> factory) {
        return ATTACHMENTS.register(name, () ->
                AttachmentType.builder(factory)
                        .serialize(new IAttachmentSerializer<T>() {
                            @Override
                            public T read(IAttachmentHolder holder, ValueInput input) {
                                T data = factory.get();
                                CompoundTag tag = input.read("data", CompoundTag.CODEC)
                                        .orElse(new CompoundTag());
                                data.load(tag);
                                return data;
                            }

                            @Override
                            public boolean write(T data, ValueOutput output) {
                                output.store("data", CompoundTag.CODEC, data.save());
                                return true;
                            }
                        })
                        .copyOnDeath()
                        .build());
    }

    /**
     * Interface for player data types that can serialize to/from CompoundTag.
     */
    public interface NbtSerializable {
        CompoundTag save();
        void load(CompoundTag tag);
    }
}
