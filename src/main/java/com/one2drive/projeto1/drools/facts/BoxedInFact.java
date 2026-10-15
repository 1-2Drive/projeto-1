package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

/**
 * The ego is behind slow traffic and cannot move left to overtake it: the lane to its left
 * is the road edge, a continuous line, occupied, or no clearer than its own. Kept until the
 * way ahead or to the left opens again; while it holds, the ego falls back and returns to
 * the right lane (Art. 13 CE) and does not start the same impossible overtake again.
 */
public class BoxedInFact {
    private final Lane lane;
    private final long since;

    public BoxedInFact(Lane lane, long since) {
        this.lane = lane;
        this.since = since;
    }

    /** The lane the ego was boxed in. */
    public Lane getLane() {
        return lane;
    }

    public long getSince() {
        return since;
    }

    @Override
    public String toString() {
        return "BoxedInFact{lane=" + lane + ", since=" + since + "}";
    }
}
