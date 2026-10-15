package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.drools.facts.FuzzyConclusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Arithmetic of the fuzzy weather controller (Mamdani): membership functions, and the
 * centroid of the aggregated output. It holds no driving rule: which terms combine into
 * which speed is decided by the rules in fuzzy-weather.drl. The Prolog backend
 * (driving_rules.pl, "Lógica difusa") repeats these sets and the same centroid sampling, so
 * both engines compute the same speed.
 *
 * <p>Inputs (visibility and grip, 0..1) share four terms that add up to 1 everywhere:
 * poor (0, 0, 0.35, 0.5), reduced (0.35, 0.5, 0.7), fair (0.5, 0.7, 0.85), good (0.7, 0.85, 1, 1).
 * Output, recommended speed in km/h: very_slow (15, 25, 35), slow (30, 40, 50),
 * moderate (45, 55, 65), prudent (55, 65, 75), light (65, 75, 85).
 */
public final class FuzzySupport {
    public static final List<String> INPUT_TERMS = List.of("poor", "reduced", "fair", "good");
    /** Output terms, most restrictive first. */
    public static final List<String> OUTPUT_TERMS = List.of("very_slow", "slow", "moderate", "prudent", "light");
    /** The output term that only refines a ceiling: alone it does not reduce the speed. */
    public static final String NON_RESTRICTIVE_TERM = "light";

    private static final double CENTROID_STEP_KMH = 0.5;
    private static final int CENTROID_SAMPLES = 260;   // 0 .. 130 km/h

    private FuzzySupport() {
    }

    public static double triangle(double x, double a, double b, double c) {
        if (x <= a || x >= c) {
            return 0.0;
        }
        return x <= b ? (x - a) / (b - a) : (c - x) / (c - b);
    }

    public static double trapezoid(double x, double a, double b, double c, double d) {
        if (x < a || x > d) {
            return 0.0;
        }
        if (x >= b && x <= c) {
            return 1.0;
        }
        return x < b ? (x - a) / (b - a) : (d - x) / (d - c);
    }

    /** Degree of a visibility or grip reading (clamped to 0..1) in one of the input terms. */
    public static double inputDegree(String term, double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return switch (term) {
            case "poor" -> trapezoid(x, 0.0, 0.0, 0.35, 0.5);
            case "reduced" -> triangle(x, 0.35, 0.5, 0.7);
            case "fair" -> triangle(x, 0.5, 0.7, 0.85);
            case "good" -> trapezoid(x, 0.7, 0.85, 1.0, 1.0);
            default -> throw new IllegalArgumentException("Unknown input term: " + term);
        };
    }

    /** Degree of a speed (km/h) in one of the output terms. */
    public static double outputDegree(String term, double kmh) {
        return switch (term) {
            case "very_slow" -> triangle(kmh, 15.0, 25.0, 35.0);
            case "slow" -> triangle(kmh, 30.0, 40.0, 50.0);
            case "moderate" -> triangle(kmh, 45.0, 55.0, 65.0);
            case "prudent" -> triangle(kmh, 55.0, 65.0, 75.0);
            case "light" -> triangle(kmh, 65.0, 75.0, 85.0);
            default -> throw new IllegalArgumentException("Unknown output term: " + term);
        };
    }

    /** True when some restrictive term holds: only then is the result a speed ceiling. */
    public static boolean restricts(Collection<?> conclusions) {
        return conclusions(conclusions).stream()
            .anyMatch(c -> c.getDegree() > 0.0 && !NON_RESTRICTIVE_TERM.equals(c.getTerm()));
    }

    /**
     * Centroid of the aggregated output: each conclusion clips its term (min), the clipped
     * sets are joined (max), and the centre of gravity is sampled every 0.5 km/h.
     */
    public static double centroid(Collection<?> conclusions) {
        List<FuzzyConclusion> list = conclusions(conclusions);
        double weighted = 0.0;
        double area = 0.0;
        for (int i = 0; i <= CENTROID_SAMPLES; i++) {
            double x = i * CENTROID_STEP_KMH;
            double mu = 0.0;
            for (FuzzyConclusion c : list) {
                mu = Math.max(mu, Math.min(c.getDegree(), outputDegree(c.getTerm(), x)));
            }
            weighted += x * mu;
            area += mu;
        }
        return area == 0.0 ? Double.NaN : weighted / area;
    }

    /** The term with the highest degree; a tie goes to the more restrictive term. */
    public static String strongestTerm(Collection<?> conclusions) {
        String best = null;
        double bestDegree = -1.0;
        for (String term : OUTPUT_TERMS) {
            for (FuzzyConclusion c : conclusions(conclusions)) {
                if (c.getTerm().equals(term) && c.getDegree() > bestDegree) {
                    best = term;
                    bestDegree = c.getDegree();
                }
            }
        }
        return best;
    }

    /** One readable line per step of the inference, for the decision's evidence. */
    public static List<String> evidence(double visibility, double grip, boolean rain,
                                        Collection<?> conclusions, double speedKmh) {
        List<String> lines = new ArrayList<>();
        lines.add("visibility=" + format(visibility) + ": " + memberships(visibility));
        lines.add("grip=" + format(grip) + ": " + memberships(grip));
        lines.add("rain: " + (rain ? "yes" : "no"));
        lines.add("concluded: " + conclusions(conclusions).stream()
            .sorted((a, b) -> Integer.compare(OUTPUT_TERMS.indexOf(a.getTerm()), OUTPUT_TERMS.indexOf(b.getTerm())))
            .map(c -> c.getTerm() + " " + format(c.getDegree()))
            .collect(Collectors.joining(", ")));
        lines.add("centroid: " + String.format(Locale.ROOT, "%.1f", speedKmh) + " km/h");
        return lines;
    }

    private static String memberships(double value) {
        return INPUT_TERMS.stream()
            .filter(term -> inputDegree(term, value) > 0.0)
            .map(term -> term + " " + format(inputDegree(term, value)))
            .collect(Collectors.joining(", "));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static List<FuzzyConclusion> conclusions(Collection<?> facts) {
        List<FuzzyConclusion> list = new ArrayList<>();
        for (Object fact : facts) {
            if (!(fact instanceof FuzzyConclusion conclusion)) {
                throw new IllegalArgumentException("Not a fuzzy conclusion: " + fact);
            }
            list.add(conclusion);
        }
        return list;
    }
}
