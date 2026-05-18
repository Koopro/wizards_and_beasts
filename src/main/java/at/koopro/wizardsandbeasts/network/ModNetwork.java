package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import at.koopro.wizardsandbeasts.broom.network.ModNetworkBroom;
import at.koopro.wizardsandbeasts.map.network.ModNetworkMap;
import at.koopro.wizardsandbeasts.currency.network.ModNetworkVault;
import at.koopro.wizardsandbeasts.apparition.network.ModNetworkAbilities;
import at.koopro.wizardsandbeasts.trunk.network.ModNetworkPocket;
import at.koopro.wizardsandbeasts.owl.network.ModNetworkOWLs;
import at.koopro.wizardsandbeasts.floo.network.ModNetworkFloo;
import at.koopro.wizardsandbeasts.bloodpact.network.ModNetworkBloodPact;
import at.koopro.wizardsandbeasts.form.network.ModNetworkForm;
import at.koopro.wizardsandbeasts.skill.network.ModNetworkSkills;
import at.koopro.wizardsandbeasts.heritage.network.ModNetworkType;
import at.koopro.wizardsandbeasts.bestiary.network.ModNetworkBestiary;
import at.koopro.wizardsandbeasts.bestiary.creature.niffler.network.ModNetworkNiffler;
import at.koopro.wizardsandbeasts.spell.network.ModNetworkSpells;
import at.koopro.wizardsandbeasts.spell.teacher.ModNetworkTeacher;
import at.koopro.wizardsandbeasts.spell.beam.ModNetworkBeamDebug;
import at.koopro.wizardsandbeasts.wand.network.ModNetworkWand;

public class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        ModNetworkBroom.register(registrar);
        ModNetworkMap.register(registrar);
        ModNetworkSpells.register(registrar);
        ModNetworkVault.register(registrar);
        ModNetworkType.register(registrar);
        ModNetworkForm.register(registrar);
        ModNetworkAbilities.register(registrar);
        ModNetworkBeamDebug.register(registrar);
        ModNetworkSkills.register(registrar);
        ModNetworkTeacher.register(registrar);
        ModNetworkWand.register(registrar);
        ModNetworkBestiary.register(registrar);
        ModNetworkPocket.register(registrar);
        ModNetworkNiffler.register(registrar);
        ModNetworkOWLs.register(registrar);
        ModNetworkFloo.register(registrar);
        ModNetworkBloodPact.register(registrar);
        ModNetworkAzkaban.register(registrar);
    }
}
