package at.koopro.wizardsandbeasts.client.skill.gui;

import net.minecraft.network.chat.Component;

public class GoblinSkillScreen extends SimpleSkillPlaceholderScreen {
    public GoblinSkillScreen() {
        super(Component.translatable("screen.wizards_and_beasts.goblin_skills.title"),
                Component.translatable("screen.wizards_and_beasts.goblin_skills.section_ledger"),
                Component.translatable("screen.wizards_and_beasts.goblin_skills.placeholder"),
                Component.translatable("screen.wizards_and_beasts.goblin_skills.hint"));
    }
}
