package com.one2drive.projeto1.drools.facts;

import java.util.Objects;

/**
 * Degree to which one fuzzy rule concludes a term of the recommended speed (the inference
 * step). Inserted logically: it goes away with the memberships that justify it.
 */
public class FuzzyConclusion {
    private final String term;
    private final double degree;

    public FuzzyConclusion(String term, double degree) {
        this.term = term;
        this.degree = degree;
    }

    public String getTerm() {
        return term;
    }

    public double getDegree() {
        return degree;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FuzzyConclusion that)) {
            return false;
        }
        return Double.compare(degree, that.degree) == 0 && term.equals(that.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, degree);
    }

    @Override
    public String toString() {
        return "FuzzyConclusion{speed is " + term + ", degree=" + degree + "}";
    }
}
