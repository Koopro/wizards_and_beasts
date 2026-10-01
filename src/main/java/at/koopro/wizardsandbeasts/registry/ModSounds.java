package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_CRASH =
            register("broom_crash");

    // Broom flight, per tier. Every one of these is a vanilla sample re-pitched in sounds.json,
    // which is the mod's whole audio strategy -- there is not one .ogg in the repo. A broom names
    // one of these in its definition's boostSound / idleLoopSound; a broom that names neither keeps
    // the shared elytra rush and gets no boost cue at all.
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_WIND_SLOW =
            register("broom_wind_slow");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_WIND_MID =
            register("broom_wind_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_WIND_FAST =
            register("broom_wind_fast");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_BOOST_SCHOOL =
            register("broom_boost_school");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_BOOST_STANDARD =
            register("broom_boost_standard");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_BOOST_RACING =
            register("broom_boost_racing");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_BOOST_FIREBOLT =
            register("broom_boost_firebolt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_BOOST_HEAVY =
            register("broom_boost_heavy");

    // Impact and mount cues. broom_crash (the original) stays as the catch-all the entity's own
    // break path plays; these two are the graded pair BroomImpacts picks between by severity.
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_CRASH_MINOR =
            register("broom_crash_minor");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_CRASH_SEVERE =
            register("broom_crash_severe");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_MOUNT =
            register("broom_mount");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_DISMOUNT =
            register("broom_dismount");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_POLISH_APPLY =
            register("broom_polish_apply");

    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_GENERIC =
            register("spell_cast_generic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_DARK =
            register("spell_cast_dark");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_CHARM =
            register("spell_cast_charm");

    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_GENERIC =
            register("spell_impact_generic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_EXPELLIARMUS =
            register("spell_impact_expelliarmus");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_STUPEFY =
            register("spell_impact_stupefy");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_AVADA =
            register("spell_impact_avada");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_FIZZLE =
            register("spell_fizzle");

    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_EQUIP =
            register("wand_equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_UNEQUIP =
            register("wand_unequip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_SWING =
            register("wand_swing");

    public static final DeferredHolder<SoundEvent, SoundEvent> PATRONUS_SUMMON =
            register("patronus_summon");

    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_RAISE =
            register("protego_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_BLOCK =
            register("protego_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_SHATTER =
            register("protego_shatter"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_HORRIBILIS_ABSORB =
            register("protego_horribilis_absorb"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_TOTALUM_RAISE =
            register("protego_totalum_raise"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_MAXIMA_RAISE =
            register("protego_maxima_raise"); // vanilla-event remap in sounds.json; bespoke asset optional
    /** One step up the tier ladder while the wand is held. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_CHARGE =
            register("protego_charge"); // vanilla-event remap in sounds.json; bespoke asset optional
    /** A dome being set down rather than carried. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_PLANT =
            register("protego_plant"); // vanilla-event remap in sounds.json; bespoke asset optional
    /** A ward running out of time — the quiet ending, never the broken one. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_FADE =
            register("protego_fade"); // vanilla-event remap in sounds.json; bespoke asset optional
    /** The last third of the pool: the ward singing under pressure. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_STRAIN =
            register("protego_strain"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CLASH =
            register("spell_clash");

    // Niffler
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_AMBIENT =
            register("entity.niffler.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HURT =
            register("entity.niffler.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_DEATH =
            register("entity.niffler.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_EAT =
            register("entity.niffler.eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HAPPY =
            register("entity.niffler.happy");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HISS =
            register("entity.niffler.hiss");

    // Kelpie: an ordinary horse's call for the lure, something wrong underneath it once revealed, and the water.
    public static final DeferredHolder<SoundEvent, SoundEvent> KELPIE_WHINNY = register("entity.kelpie.whinny");
    public static final DeferredHolder<SoundEvent, SoundEvent> KELPIE_SNARL = register("entity.kelpie.snarl");
    public static final DeferredHolder<SoundEvent, SoundEvent> KELPIE_SPLASH = register("entity.kelpie.splash");

    // Bowtruckle: small wood-and-leaf noises, vanilla sounds through sounds.json.
    public static final DeferredHolder<SoundEvent, SoundEvent> BOWTRUCKLE_RUSTLE = register("entity.bowtruckle.rustle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOWTRUCKLE_CREAK = register("entity.bowtruckle.creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOWTRUCKLE_SNAP = register("entity.bowtruckle.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOWTRUCKLE_CLICK = register("entity.bowtruckle.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOWTRUCKLE_PICK = register("entity.bowtruckle.pick");

    // Dementor. Vanilla sounds through sounds.json until recordings exist: a rattling breath, the memory voices, a
    // failing heartbeat while it feeds, the Kiss, the recoil from a blow, the shriek from a Patronus, dissipation.
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_BREATH =
            register("entity.dementor.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_WHISPER =
            register("entity.dementor.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_DRAIN =
            register("entity.dementor.drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_KISS =
            register("entity.dementor.kiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_RECOIL =
            register("entity.dementor.recoil");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_REPELLED =
            register("entity.dementor.repelled");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEMENTOR_DISSIPATE =
            register("entity.dementor.dissipate");

    // Phoenix. Each points at vanilla sounds through sounds.json until a recording exists; the events
    // are the phoenix's own so a real call, song or burning can replace them without a code change.
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_CRY =
            register("entity.phoenix.cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_SONG =
            register("entity.phoenix.song");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_BURST =
            register("entity.phoenix.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_RISE =
            register("entity.phoenix.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_FLAME =
            register("entity.phoenix.flame");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOENIX_TEARS =
            register("entity.phoenix.tears");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_DIG =
            register("entity.niffler.dig");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_SQUIRM =
            register("entity.niffler.squirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> BABY_NIFFLER_AMBIENT =
            register("entity.baby_niffler.ambient");

    // Species voices from the creature identity pass: vanilla audio through sounds.json until recordings exist.
    // The Augurey's low throbbing cry before rain; a Cornish Pixie's shrill jabber; the Skrewt's "phut" of a blast;
    // a Griffin's screech over its gold; merfolk song as heard under water and as the screech it is above it.
    public static final DeferredHolder<SoundEvent, SoundEvent> AUGUREY_CRY = register("entity.augurey.cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORNISH_PIXIE_JABBER =
            register("entity.cornish_pixie.jabber");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKREWT_BLAST =
            register("entity.blast_ended_skrewt.blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRIFFIN_SCREECH = register("entity.griffin.screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> MERFOLK_SONG = register("entity.merperson.song");
    public static final DeferredHolder<SoundEvent, SoundEvent> MERFOLK_SCREECH = register("entity.merperson.screech");

    // Deluminator
    public static final DeferredHolder<SoundEvent, SoundEvent> DELUMINATOR_ABSORB = register("deluminator_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> DELUMINATOR_RESTORE = register("deluminator_restore");

    // Two-Way Mirror
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_CONNECT = register("mirror_connect");

    // Horcrux
    public static final DeferredHolder<SoundEvent, SoundEvent> HORCRUX_SHATTER = register("horcrux_shatter");

    // Resurrection Stone
    public static final DeferredHolder<SoundEvent, SoundEvent> RESURRECTION_STONE_TURN = register("resurrection_stone_turn");

    // Blood Pact
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_OFFER = register("blood_pact_offer");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_SEAL = register("blood_pact_seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_SHATTER = register("blood_pact_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_BLOCK = register("blood_pact_block");

    // Floo Network
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_WHOOSH = register("floo_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_IGNITE = register("floo_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_LAND = register("floo_land");

    // Sneakoscope — one spinning top wound tighter and tighter, so the three tiers read as the
    // same object rather than three unrelated noises. Pitch does the winding; see SneakoscopeTuning.
    public static final DeferredHolder<SoundEvent, SoundEvent> SNEAKOSCOPE_SPIN = register("sneakoscope_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNEAKOSCOPE_WHIRR = register("sneakoscope_whirr");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNEAKOSCOPE_SHRIEK = register("sneakoscope_shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNEAKOSCOPE_FOCUS = register("sneakoscope_focus");

    // Butterbeer. A fizz as the mug comes up and a warm swallow as it goes down -- the generic
    // drink burp reads as a potion, and this is a pint in a pub.
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTERBEER_FIZZ = register("butterbeer_fizz");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTERBEER_CHUG = register("butterbeer_chug");

    // Floo, beyond the three it started with. Every one is a vanilla sample re-pitched in
    // sounds.json -- there is not one .ogg in this repo, and these are not the place to start.
    //
    // FLOO_LAND stays as the thud of a traveller hitting the floor. FLOO_ARRIVAL is the different
    // event of a grate flaring as somebody comes out of it, which is what the people already in the
    // room hear; borrowing the whoosh for it made a departure and an arrival sound identical.
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_TRAVEL_LOOP = register("floo_travel_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_ARRIVAL = register("floo_arrival");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_FAIL_SPUTTER = register("floo_fail_sputter");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_SEALED = register("floo_sealed");

    // Apparition. Three cracks rather than one, because the server already resolves which crack an
    // observer hears (ApparitionCrackVariant) and had nothing to play. All three are the firework blast
    // and the generic explosion re-pitched -- deliberately NOT entity.enderman.teleport, which is what
    // this used to borrow: an ender pearl and a wizard folding space are not the same event, and sharing
    // a sample made them the same thing to anyone with their eyes shut.
    public static final DeferredHolder<SoundEvent, SoundEvent> APPARITION_CRACK_WIZARD =
            register("apparition_crack_wizard");
    public static final DeferredHolder<SoundEvent, SoundEvent> APPARITION_CRACK_ELF =
            register("apparition_crack_elf");
    public static final DeferredHolder<SoundEvent, SoundEvent> APPARITION_CRACK_MUFFLED =
            register("apparition_crack_muffled");
    /**
     * The gathering, before the crack. Played twice per attempt at different pitches -- once as the charge
     * begins and once as the Deliberation window opens -- which makes the moment to let go audible as well
     * as visible, and readable by a player who cannot see the ring behind them.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> APPARITION_WINDUP =
            register("apparition_windup");
    /** The tear. Played at the origin only, where the part that did not travel stays. */
    public static final DeferredHolder<SoundEvent, SoundEvent> APPARITION_SPLINCH =
            register("apparition_splinch");

    // The change, and what comes out of it. Re-pitched vanilla samples like every other sound in this
    // mod -- see sounds.json. The transform cue plays at the onset and covers the whole
    // werewolfTransformDelayTicks window; the howl marks the change landing and every target acquired.
    public static final DeferredHolder<SoundEvent, SoundEvent> WEREWOLF_TRANSFORM =
            register("werewolf_transform");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEREWOLF_HOWL =
            register("werewolf_howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEREWOLF_REVERT =
            register("werewolf_revert");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUND_EVENTS.register(path, () ->
                SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path)));
    }

    private ModSounds() {
    }
}
