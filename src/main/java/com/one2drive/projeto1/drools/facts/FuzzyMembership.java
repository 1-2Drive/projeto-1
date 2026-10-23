package com.one2drive.projeto1.drools.facts;

import java.util.Objects;

/**
 * Degree to which a crisp input (visibility or grip) belongs to one of its linguistic terms,
 * the output of the fuzzification step. Inserted logically, so it lives only while the
 * reading it was computed from is current: equals and hashCode are by value, as truth
 * maintenance requires.
 */
public class FuzzyMembership {
    private final String variable;
    private final String term;
    private final double degree;

    public FuzzyMembership(String variable, String term, double degree) {
        this.variable = variable;
        this.term = term;
        this.degree = degree;
    }

    public String getVariable() {
        return variable;
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
        if (!(other instanceof FuzzyMembership that)) {
            return false;
        }
        return Double.compare(degree, that.degree) == 0
            && variable.equals(that.variable)
            && term.equals(that.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, term, degree);
    }

    @Override
    public String toString() {
        return "FuzzyMembership{" + variable + " is " + term + ", degree=" + degree + "}";
    }
}
