package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

/**
 * One issued challenge, held server-side until it is submitted, replaced or cancelled.
 *
 * @param id       random nonce; a submission must quote it, and it is good for one submission only
 * @param seed     what the path was generated from — logged on failure so a bad path can be reproduced
 * @param issuedAt server game time when the challenge was sent
 */
@NullMarked
public record TraceChallenge(long id, String spellId, TraceDifficulty difficulty, long seed, TracePath path,
                             long issuedAt, TraceSource source) {

    /** Latest acceptable submission time, generous by the start allowance and network slack. */
    public long deadline(boolean assist) {
        return issuedAt + TraceDifficulty.START_ALLOWANCE_TICKS
                + TraceJudge.timeLimitTicks(path, difficulty, assist)
                + TraceDifficulty.LATENCY_SLACK_TICKS;
    }
}
