package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.market.IllicitRisk;
import at.koopro.wizardsandbeasts.market.MarketDefinition;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityFilter;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A place in the wizarding world as a gameplay hub, loaded from {@code data/<ns>/locations/<id>.json}.
 *
 * <p>A location is not a structure. The world may build it (a generated fortress), players may build it (a hall of
 * Gringotts marble the map already recognises) or an NPC may <em>be</em> it (a goblin keeping a counter). What makes
 * it a hub is what the file declares on top:
 *
 * <ul>
 *   <li><b>presence</b> — how the server decides a player is there ({@link Presence});</li>
 *   <li><b>standing</b> — the player's reputation there, as a {@link CareerTrack}: tiers, reputation paid for
 *       activity, and a board of contracts. The same engine careers use, so a location never grows a second
 *       contract system;</li>
 *   <li><b>services</b> — Java behaviours ({@link LocationServices}) offered from a tier, at a price.</li>
 * </ul>
 *
 * <p>Everything a location counts is an existing activity ({@link ActivityType}), reported once by the hook where it
 * happens; a new place is a data file and lang lines, plus a service class only if it sells something new.
 *
 * @param module   the module that owns the place; off, the location is invisible and inert
 * @param papers   a licence its hosts check before dealing with a player, if any
 * @param presence where the place is
 * @param standing tiers ({@code ranks}), reputation rules ({@code xp_rules}) and the contract board
 * @param services what it sells
 * @param skin     the client GUI skin its board is drawn in (a {@code WizardsPalette.GuiSkin} folder), if any
 * @param market   what its keeper buys and sells, at prices that move with trade ({@code market/})
 * @param risk     present when the place deals outside the law: every deal there may reach the Ministry
 */
@NullMarked
public record LocationDefinition(Module module, Optional<LicenseType> papers, Presence presence, CareerTrack standing,
                                 List<ServiceOffer> services, Optional<String> skin, Optional<MarketDefinition> market,
                                 Optional<IllicitRisk> risk) {

    static final Codec<Module> MODULE_CODEC = Codec.STRING.comapFlatMap(name -> {
        try {
            return DataResult.success(Module.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown module " + name);
        }
    }, module -> module.name().toLowerCase(Locale.ROOT));

    static final Codec<LicenseType> LICENCE_CODEC = Codec.STRING.comapFlatMap(name -> {
        try {
            return DataResult.success(LicenseType.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown licence " + name);
        }
    }, type -> type.name().toLowerCase(Locale.ROOT));

    public static final Codec<LocationDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            MODULE_CODEC.fieldOf("module").forGetter(LocationDefinition::module),
            LICENCE_CODEC.optionalFieldOf("papers").forGetter(LocationDefinition::papers),
            Presence.CODEC.fieldOf("presence").forGetter(LocationDefinition::presence),
            CareerTrack.CODEC.fieldOf("standing").forGetter(LocationDefinition::standing),
            ServiceOffer.CODEC.listOf().optionalFieldOf("services", List.of()).forGetter(LocationDefinition::services),
            Codec.STRING.optionalFieldOf("skin").forGetter(LocationDefinition::skin),
            MarketDefinition.CODEC.optionalFieldOf("market").forGetter(LocationDefinition::market),
            IllicitRisk.CODEC.optionalFieldOf("risk").forGetter(LocationDefinition::risk)
    ).apply(i, LocationDefinition::new));

    public LocationDefinition {
        services = List.copyOf(services);
    }

    /**
     * Where a location is. A player is there when <em>any</em> declared form holds.
     *
     * @param hosts     entity types that keep the place (a goblin teller, a wandmaker): talking to one admits the
     *                  player, who stays "there" while within {@code hostReach} of it
     * @param hostReach blocks from the host that still count as at its counter
     * @param hostProfession for a villager host, the profession it must hold (a wandmaker, not any villager)
     * @param landmark  a block palette whose density marks the place ({@code MinistryArea}'s rule), e.g.
     *                  {@code wizards_and_beasts:landmark/gringotts}
     * @param structure a worldgen structure whose pieces are the place
     */
    public record Presence(List<Identifier> hosts, double hostReach, Optional<Identifier> hostProfession,
                           Optional<Identifier> landmark, Optional<Identifier> structure) {
        public static final Codec<Presence> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.listOf().optionalFieldOf("hosts", List.of()).forGetter(Presence::hosts),
                Codec.doubleRange(1.0, 32.0).optionalFieldOf("host_reach", 8.0).forGetter(Presence::hostReach),
                Identifier.CODEC.optionalFieldOf("host_profession").forGetter(Presence::hostProfession),
                Identifier.CODEC.optionalFieldOf("landmark").forGetter(Presence::landmark),
                Identifier.CODEC.optionalFieldOf("structure").forGetter(Presence::structure)
        ).apply(i, Presence::new));

        public Presence {
            hosts = List.copyOf(hosts);
        }
    }

    public @Nullable ServiceOffer service(Identifier id) {
        for (ServiceOffer offer : services) {
            if (offer.service().equals(id)) {
                return offer;
            }
        }
        return null;
    }

    /**
     * Every activity type the standing reads — in a reputation rule, a contract or a tier goal. Activity of any
     * other type is not counted here at all, so a bank does not keep a tally of every creature its customers feed.
     */
    public Set<ActivityType> activityTypes() {
        Set<ActivityType> types = EnumSet.noneOf(ActivityType.class);
        for (CareerTrack.XpRule rule : standing.xpRules()) {
            types.add(rule.when().activity());
        }
        for (ContractDefinition contract : standing.contracts()) {
            if (contract.objective() instanceof ContractDefinition.Activity activity) {
                types.add(activity.filter().activity());
            }
        }
        for (CareerTrack.Rank rank : standing.ranks()) {
            for (CareerGoal goal : rank.requires()) {
                if (goal instanceof CareerGoal.ActivityTotal total) {
                    types.add(total.filter().activity());
                }
            }
        }
        return types;
    }

    /** Problems the codec cannot see; the loader refuses a location with any. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>(standing.validate());
        Set<Identifier> seen = new HashSet<>();
        for (ServiceOffer offer : services) {
            if (!seen.add(offer.service())) {
                problems.add("duplicate service " + offer.service());
            }
            if (standing.rankIndex(offer.minTier()) < 0) {
                problems.add("service " + offer.service() + " names unknown tier " + offer.minTier());
            }
        }
        if (market.isPresent()) {
            Set<String> tiers = new HashSet<>();
            standing.ranks().forEach(rank -> tiers.add(rank.id()));
            problems.addAll(market.get().validate(tiers));
        }
        if (risk.isPresent() && module != Module.MINISTRY) {
            // Crime without the law that punishes it would only be a better-paying shop.
            problems.add("a location with a risk must belong to the ministry module");
        }
        if (presence.hosts().isEmpty() && presence.landmark().isEmpty() && presence.structure().isEmpty()) {
            problems.add("presence declares no host, landmark or structure: no one could ever be there");
        }
        return problems;
    }

    /** Filters a contract or rule may use; exposed for data tests. */
    public List<ActivityFilter> filters() {
        List<ActivityFilter> out = new ArrayList<>();
        standing.xpRules().forEach(rule -> out.add(rule.when()));
        for (ContractDefinition contract : standing.contracts()) {
            if (contract.objective() instanceof ContractDefinition.Activity activity) {
                out.add(activity.filter());
            }
        }
        return out;
    }
}
