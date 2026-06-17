package at.koopro.wizardsandbeasts.creature;

/**
 * Body-plan taxonomy. Recorded on each {@link CreatureDefinition} purely to document the
 * placeholder rig so a real Blockbench model can be swapped in against the same bone contract.
 * Not consumed by gameplay logic.
 */
public enum BodyPlan {
    QUADRUPED,
    WINGED_QUADRUPED,
    BIPED_HUMANOID,
    LARGE_HUMANOID,
    AVIAN,
    INSECTOID_FLYER,
    SERPENTINE,
    WORM_LARVA,
    BLOB_SPHERE,
    ARTHROPOD_MULTILEG,
    AQUATIC,
    SESSILE
}
