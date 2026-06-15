package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import at.koopro.wizardsandbeasts.network.broom.ModNetworkBroom;
import at.koopro.wizardsandbeasts.network.map.ModNetworkMap;
import at.koopro.wizardsandbeasts.network.currency.ModNetworkVault;
import at.koopro.wizardsandbeasts.network.apparition.ModNetworkAbilities;
import at.koopro.wizardsandbeasts.network.trunk.ModNetworkPocket;
import at.koopro.wizardsandbeasts.network.owl.ModNetworkOWLs;
import at.koopro.wizardsandbeasts.network.floo.ModNetworkFloo;
import at.koopro.wizardsandbeasts.network.bloodpact.ModNetworkBloodPact;
import at.koopro.wizardsandbeasts.network.form.ModNetworkForm;
import at.koopro.wizardsandbeasts.network.skill.ModNetworkSkills;
import at.koopro.wizardsandbeasts.network.heritage.ModNetworkType;
import at.koopro.wizardsandbeasts.network.bestiary.ModNetworkBestiary;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.ModNetworkNiffler;
import at.koopro.wizardsandbeasts.network.spell.ModNetworkSpells;
import at.koopro.wizardsandbeasts.spell.teacher.ModNetworkTeacher;
import at.koopro.wizardsandbeasts.spell.beam.ModNetworkBeamDebug;
import at.koopro.wizardsandbeasts.network.wand.ModNetworkWand;
import at.koopro.wizardsandbeasts.network.character.ModNetworkCharacter;
import at.koopro.wizardsandbeasts.network.stats.ModNetworkStats;
import at.koopro.wizardsandbeasts.network.trinket.ModNetworkTrinkets;

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
        ModNetworkCharacter.register(registrar);
        ModNetworkStats.register(registrar);
        ModNetworkTrinkets.register(registrar);
    }
}
