package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.vocation.HealersWard;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.spell.cast.SpellExecutor;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/** B5, the Healer: Episkey lands on the patient in view, and only a Healer's does. */
public final class HealersWardTests {

    private HealersWardTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("healer_episkey_reaches_the_patient_in_view",
                "vocation: a Healer's Episkey heals the villager they look at five blocks off; anyone else's heals only themself",
                HealersWardTests::episkeyReaches);
    }

    private static void episkeyReaches(GameTestHelper helper) {
        ServerPlayer healer = WizardTestSupport.placeMockPlayer(helper, "PoppyPomfrey", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, healer);
        var patient = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 0));
        patient.setNoAi(true);
        patient.setNoGravity(true);
        try {
            Spell episkey = Spells.byId("episkey");
            check(helper, episkey != null, () -> "no Episkey");
            lookAt(healer, patient);
            patient.setHealth(patient.getMaxHealth() - 8.0f);

            float before = patient.getHealth();
            SpellExecutor.executeGeneric(episkey, helper.getLevel(), healer, ItemStack.EMPTY);
            check(helper, patient.getHealth() == before, () -> "an ordinary wizard's Episkey healed someone else");

            healer.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(
                    Map.of(OWLSubject.HERBOLOGY, OWLGrade.E), true, 1, 0L));
            check(helper, VocationManager.commit(healer, Identifier.fromNamespaceAndPath("wizards_and_beasts",
                    "herbologist")) == VocationManager.CommitResult.OK, () -> "setup: could not commit to Herbology");
            check(helper, HealersWard.reaches(healer), () -> "the Herbologist is not granted the Healer's reach");
            check(helper, HealersWard.patientInView(healer) == patient, () -> "the Healer does not see the patient");

            SpellExecutor.executeGeneric(episkey, helper.getLevel(), healer, ItemStack.EMPTY);
            check(helper, patient.getHealth() > before, () -> "the Healer's Episkey did not reach the patient");
            helper.succeed();
        } finally {
            VocationManager.clear(healer);
            patient.discard();
            WizardTestSupport.retire(helper, healer);
        }
    }

    private static void lookAt(ServerPlayer from, LivingEntity at) {
        double dx = at.getX() - from.getX();
        double dz = at.getZ() - from.getZ();
        double dy = at.getEyeY() - from.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        from.setYRot(yaw);
        from.setXRot(pitch);
        from.setYHeadRot(yaw);
    }
}
