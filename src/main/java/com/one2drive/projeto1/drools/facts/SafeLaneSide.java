package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

import java.util.Objects;

/**
 * A side of the vehicle (LEFT or RIGHT) it may change lane to, as proved by the
 * safeToChangeLane query (lane-safety.drl). Inserted logically, so it lives only while that
 * proof holds: rules that need "no safe side" test for its absence, a plain negation, instead
 * of negating the query. equals and hashCode are by value, as truth maintenance requires.
 */
public class SafeLaneSide {
    private final Lane side;

    public SafeLaneSide(Lane side) {
        this.side = side;
    }

    public Lane getSide() {
        return side;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SafeLaneSide that)) {
            return false;
        }
        return side == that.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(side);
    }

    @Override
    public String toString() {
        return "SafeLaneSide{side=" + side + "}";
    }
}
