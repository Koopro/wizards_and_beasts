package at.koopro.wizardsandbeasts.heritage.identity;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.grant.PlayerStatusAbilityGrantSource;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageTraits;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.heritage.centaur.StarReading;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.elf.ElfBondData;
import at.koopro.wizardsandbeasts.heritage.elf.HouseElfBond;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile.Section;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile.Tone;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodData;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.ministry.law.CodeOfWandUse;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Builds a character's {@link HeritageProfile} from the rules that already hold — never from a second store.
 *
 * <p>Each line is the player-facing name of something enforced elsewhere: a trait read by {@code hasTrait}, an
 * ability on the wheel, a law that refuses them, a pool that drains. The general pass (traits by polarity, body
 * modifiers, wand law, abilities, professions, practice) is shared by every people; the per-people pass adds what only
 * that people has — the house-elf's bond is the reference ({@link #elf}).
 */
@NullMarked
public final class HeritageProfiles {

    private static final String K = "heritage.wizards_and_beasts.profile.";

    private HeritageProfiles() {}

    public static HeritageProfile build(ServerPlayer player) {
        PlayerHeritageData data = HeritageAPI.getData(player);
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();
        HeritageProfile.Builder b = new HeritageProfile.Builder();
        if (heritage == null || variant == null) {
            return b.build();
        }
        ConditionOrigin condition = data.getCondition();
        Component title = Component.literal(heritage.getDisplayName() + " · " + variant.getDisplayName());
        if (condition != null) {
            title = title.copy().append(" · ").append(Component.translatable(condition.getTranslationKey()));
        }
        b.title(title);

        traits(b, data);
        body(b, heritage, variant);
        wandLaw(b, player, heritage);
        abilities(b, player);
        progression(b, player, data, heritage);

        switch (heritage) {
            case HOUSE_ELF -> elf(b, player, data);
            case GOBLIN -> b.add(Section.SOCIAL, Component.translatable(K + "goblin.gringotts"),
                    Component.translatable(K + "goblin.gringotts.detail"), Tone.GOOD);
            case VAMPIRE -> vampire(b, player);
            case VEELA -> veela(b, player, data);
            case CENTAUR -> centaur(b, player);
            case MERPEOPLE -> merpeople(b, player);
            case WIZARDKIND -> wizard(b, player, data);
            default -> { }
        }
        if (condition != null) {
            if (condition.condition() == MagicalCondition.LYCANTHROPY) {
                werewolf(b, player, data);
            } else if (condition.condition() == MagicalCondition.OBSCURUS) {
                obscurial(b, player);
            }
        }
        return b.build();
    }

    // -- shared --------------------------------------------------------------------------------------------

    /** Every trait, under Advantages, Disadvantages or both, with its sentence. */
    static void traits(HeritageProfile.Builder b, PlayerHeritageData data) {
        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            for (HeritageTraits.Trait trait : HeritageTraits.of(data, kind)) {
                Component name = Component.translatable(trait.getNameKey());
                Component desc = Component.translatable(trait.getDescriptionKey());
                switch (trait.polarity()) {
                    case ADVANTAGE -> b.add(Section.ADVANTAGES, name, desc, Tone.GOOD);
                    case DISADVANTAGE -> b.add(Section.DISADVANTAGES, name, desc, Tone.BAD);
                    case MIXED -> {
                        b.add(Section.ADVANTAGES, name, desc, Tone.MIXED);
                        b.add(Section.DISADVANTAGES, name, desc, Tone.MIXED);
                    }
                }
            }
        }
    }

    /** The body's numbers, as the stats layer applies them (HeritageAPI.applyStats). */
    static void body(HeritageProfile.Builder b, Heritage heritage, HeritageVariant variant) {
        double health = heritage.getBaseHealth() + variant.getHealthMod();
        double armor = heritage.getBaseArmor() + variant.getArmorMod();
        double speed = heritage.getBaseSpeed() + variant.getSpeedMod();
        if (health != 0) {
            b.add(health > 0 ? Section.ADVANTAGES : Section.DISADVANTAGES,
                    Component.translatable(K + "health", signed(health)), health > 0 ? Tone.GOOD : Tone.BAD);
        }
        if (armor != 0) {
            b.add(armor > 0 ? Section.ADVANTAGES : Section.DISADVANTAGES,
                    Component.translatable(K + "armor", signed(armor)), armor > 0 ? Tone.GOOD : Tone.BAD);
        }
        if (speed != 0) {
            b.add(speed > 0 ? Section.ADVANTAGES : Section.DISADVANTAGES,
                    Component.translatable(K + "speed", signed(Math.round(speed / 0.1 * 100.0))
                            + "%"), speed > 0 ? Tone.GOOD : Tone.BAD);
        }
    }

    /** Clause Three, for the peoples it binds. */
    static void wandLaw(HeritageProfile.Builder b, ServerPlayer player, Heritage heritage) {
        if (!heritage.canUseWand()) {
            b.add(Section.DISADVANTAGES, Component.translatable(K + "wand_code"),
                    Component.translatable(K + "wand_code.detail"), Tone.BAD);
            if (CodeOfWandUse.carrying(player)) {
                b.add(Section.STATE, Component.translatable(K + "wand_code.carrying"), Tone.BAD);
            }
        }
    }

    /** What the wheel holds because of who they are (status grants: Apparition, allure, elf magic…). */
    static void abilities(HeritageProfile.Builder b, ServerPlayer player) {
        for (String id : new PlayerStatusAbilityGrantSource().grantsFor(player)) {
            Identifier key = Identifier.tryParse(id);
            AbilityDefinition def = key == null ? null : AbilityDefinitionRegistry.get(key);
            if (def == null || key.getPath().startsWith("animagus")) {
                continue; // the Animagus form is learned, not inherited
            }
            b.add(Section.ACTIVE, Component.translatable(def.nameKey()), Component.translatable(def.descriptionKey()),
                    Tone.GOOD);
        }
    }

    static void progression(HeritageProfile.Builder b, ServerPlayer player, PlayerHeritageData data, Heritage heritage) {
        String selected = data.getSelectedProfessionId();
        int unlocked = 0;
        int total = 0;
        for (ProfessionNode node : ProfessionNode.values()) {
            if (node.getParentHeritage() != heritage) {
                continue;
            }
            total++;
            if (data.hasUnlockedProfession(node.getId())) {
                unlocked++;
            }
            if (node.getId().equals(selected)) {
                b.add(Section.PROGRESSION, Component.translatable(K + "profession", node.getDisplayName()),
                        Component.literal(node.getDescription()), Tone.GOOD);
            }
        }
        b.add(Section.PROGRESSION, Component.translatable(K + "professions", unlocked, total,
                data.getProfessionPoints()), Tone.NEUTRAL);
        if (heritage.canUseWand()) {
            b.add(Section.PROGRESSION, Component.translatable(K + "points_from_spells"), Tone.NEUTRAL);
        }
        boolean practises = false;
        for (PracticeKind kind : PracticeKind.values()) {
            if (kind.heritage() == heritage) {
                practises = true;
                break;
            }
        }
        if (practises) {
            HeritagePracticeData practice = HeritagePractice.of(player);
            long day = SpellPractice.day(player.level().getGameTime());
            b.add(Section.PROGRESSION, Component.translatable(K + "practice", practice.progress(),
                    HeritagePracticeRules.PER_POINT, HeritagePracticeRules.leftToday(practice, day)),
                    Component.translatable(K + "practice." + heritage.getId()), Tone.NEUTRAL);
        }
    }

    // -- the reference: house-elves ---------------------------------------------------------------------

    static void elf(HeritageProfile.Builder b, ServerPlayer player, PlayerHeritageData data) {
        ElfBondData bond = HouseElfBond.bondOf(player);
        if (bond.bound()) {
            boolean online = player.level().getServer() != null
                    && player.level().getServer().getPlayerList().getPlayer(bond.master().orElseThrow()) != null;
            b.add(Section.SOCIAL, Component.translatable(K + "elf.serves", bond.masterName()),
                    Component.translatable(K + "elf.serves.detail"), Tone.MIXED);
            b.add(Section.DISADVANTAGES, Component.translatable(K + "elf.no_harm"),
                    Component.translatable(K + "elf.no_harm.detail"), Tone.BAD);
            b.add(Section.STATE, Component.translatable(online ? K + "elf.master_online" : K + "elf.master_offline",
                    bond.masterName()), Tone.NEUTRAL);
            b.add(Section.STATE, Component.translatable(K + "elf.summons", bond.summons()), Tone.NEUTRAL);
            b.add(Section.PROGRESSION, Component.translatable(K + "elf.freedom"),
                    Component.translatable(K + "elf.freedom.detail"), Tone.MIXED);
        } else if (data.hasTrait("bound_service")) {
            b.add(Section.SOCIAL, Component.translatable(K + "elf.unsworn"),
                    Component.translatable(K + "elf.unsworn.detail"), Tone.NEUTRAL);
        } else {
            b.add(Section.SOCIAL, Component.translatable(K + "elf.free"),
                    Component.translatable(K + "elf.free.detail"), Tone.GOOD);
        }
        b.add(Section.SOCIAL, Component.translatable(K + "elf.trace"),
                Component.translatable(K + "elf.trace.detail"), Tone.GOOD);
    }

    // -- the other peoples ---------------------------------------------------------------------------------

    static void wizard(HeritageProfile.Builder b, ServerPlayer player, PlayerHeritageData data) {
        if (!data.canCast()) {
            return;
        }
        b.add(Section.SOCIAL, Component.translatable(K + "wizard.trace"),
                Component.translatable(K + "wizard.trace.detail"), Tone.MIXED);
        b.add(Section.STATE, Component.translatable(ApparitionServerLogic.isLicensed(player)
                ? K + "wizard.apparition_licensed" : K + "wizard.apparition_unlicensed"), Tone.NEUTRAL);
    }

    static void vampire(HeritageProfile.Builder b, ServerPlayer player) {
        VampireBloodData blood = VampireBloodAPI.getData(player);
        b.add(Section.STATE, Component.translatable(K + "vampire.blood", Math.round(blood.getBlood()),
                Math.round(blood.getMaxBlood()), Component.translatable("thirst.wizards_and_beasts."
                        + blood.getThirstStage().name().toLowerCase(Locale.ROOT))),
                blood.getThirstStage().ordinal() == 0 ? Tone.GOOD : Tone.BAD);
        if (player.level() instanceof ServerLevel level && HeritageAPI.getData(player).hasTrait("sunlight_weakness")
                && level.isBrightOutside() && level.canSeeSky(player.blockPosition())) {
            b.add(Section.STATE, Component.translatable(K + "vampire.sunlit"), Tone.BAD);
        }
    }

    static void veela(HeritageProfile.Builder b, ServerPlayer player, PlayerHeritageData data) {
        b.add(Section.STATE, Component.translatable(HeritageTransformService.isTransformed(data)
                ? K + "veela.harpy" : K + "veela.human"), Tone.NEUTRAL);
    }

    static void centaur(HeritageProfile.Builder b, ServerPlayer player) {
        if (!StarReading.canRead(player)) {
            return;
        }
        boolean readable = StarReading.read(player) instanceof StarReading.Reading.Seen;
        b.add(Section.STATE, Component.translatable(readable ? K + "centaur.sky_clear" : K + "centaur.sky_hidden"),
                Tone.NEUTRAL);
    }

    static void merpeople(HeritageProfile.Builder b, ServerPlayer player) {
        b.add(Section.STATE, Component.translatable(player.isUnderWater() ? K + "mer.underwater" : K + "mer.ashore"),
                Tone.NEUTRAL);
    }

    static void werewolf(HeritageProfile.Builder b, ServerPlayer player, PlayerHeritageData data) {
        long dayTime = player.level().getDayTime();
        int phase = WerewolfRules.moonPhase(dayTime);
        int nights = Math.floorMod(WerewolfRules.FULL_MOON - phase, 8);
        b.add(Section.STATE, Component.translatable(nights == 0 ? K + "wolf.full_moon" : K + "wolf.moon", nights),
                nights == 0 ? Tone.BAD : Tone.NEUTRAL);
        b.add(Section.STATE, Component.translatable(WerewolfRules.isTransformed(data) ? K + "wolf.wolf" : K + "wolf.human"),
                Tone.NEUTRAL);
        if (PlayerAbilityHelper.isWolfsbaneActive(player)) {
            b.add(Section.STATE, Component.translatable(K + "wolf.wolfsbane"), Tone.GOOD);
        }
        b.add(Section.SOCIAL, Component.translatable(K + "wolf.silver"),
                Component.translatable(K + "wolf.silver.detail"), Tone.BAD);
    }

    static void obscurial(HeritageProfile.Builder b, ServerPlayer player) {
        int stress = Math.round(ObscurialResourceManager.getStress(player));
        b.add(Section.STATE, Component.translatable(K + "obscurus.stress", stress), stress >= 60 ? Tone.BAD : Tone.NEUTRAL);
        b.add(Section.STATE, Component.translatable(HeritageTransformService.isTransformed(HeritageAPI.getData(player))
                ? K + "obscurus.unleashed" : K + "obscurus.held"), Tone.NEUTRAL);
    }

    private static String signed(double value) {
        String text = value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
        return value > 0 ? "+" + text : text;
    }
}
