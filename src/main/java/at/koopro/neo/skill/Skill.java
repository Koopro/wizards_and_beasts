package at.koopro.neo.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An individual node in a skill tree. Immutable after construction.
 * Built via {@link #builder(String, String)}.
 */
public final class Skill {

    private final String id;
    private final String displayName;
    private final String description;
    private final SkillTreeId tree;
    private final int maxLevel;
    private final int pointCost;
    private final List<String> prerequisites;
    private final List<SkillEffect> effects;
    private final int tier;
    private final int column;

    private Skill(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.tree = builder.tree;
        this.maxLevel = builder.maxLevel;
        this.pointCost = builder.pointCost;
        this.prerequisites = Collections.unmodifiableList(new ArrayList<>(builder.prerequisites));
        this.effects = Collections.unmodifiableList(new ArrayList<>(builder.effects));
        this.tier = builder.tier;
        this.column = builder.column;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public SkillTreeId getTree() { return tree; }
    public int getMaxLevel() { return maxLevel; }
    public int getPointCost() { return pointCost; }
    public List<String> getPrerequisites() { return prerequisites; }
    public List<SkillEffect> getEffects() { return effects; }
    public int getTier() { return tier; }
    public int getColumn() { return column; }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private String description = "";
        private SkillTreeId tree;
        private int maxLevel = 1;
        private int pointCost = 1;
        private final List<String> prerequisites = new ArrayList<>();
        private final List<SkillEffect> effects = new ArrayList<>();
        private int tier;
        private int column;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder tree(SkillTreeId tree) {
            this.tree = tree;
            return this;
        }

        public Builder maxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }

        public Builder cost(int pointCost) {
            this.pointCost = pointCost;
            return this;
        }

        public Builder prerequisite(String skillId) {
            this.prerequisites.add(skillId);
            return this;
        }

        public Builder effect(SkillEffect effect) {
            this.effects.add(effect);
            return this;
        }

        public Builder position(int tier, int column) {
            this.tier = tier;
            this.column = column;
            return this;
        }

        public Skill build() {
            if (tree == null) throw new IllegalStateException("Skill '" + id + "' must have a tree");
            return new Skill(this);
        }
    }
}
