package at.koopro.wizardsandbeasts.client.skill.gui;

import net.minecraft.network.chat.Component;

public class ElfSkillScreen extends SimpleSkillPlaceholderScreen {
    public ElfSkillScreen() {
        super(Component.translatable("screen.wizards_and_beasts.elf_skills.title"),
                Component.translatable("screen.wizards_and_beasts.elf_skills.section_threads"),
                Component.translatable("screen.wizards_and_beasts.elf_skills.placeholder"),
                Component.translatable("screen.wizards_and_beasts.elf_skills.hint"));
    }
}
