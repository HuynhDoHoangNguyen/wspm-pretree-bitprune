package vn.edu.wspm.config;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;

class MinimumSupportResolverTest {

    private static final double TOLERANCE = 1.0e-12;

    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    @Test
    void resolvesTableOneFourteenPercentWithoutIntegerRounding() {
        var result = resolver.resolve(MinimumSupport.of(14.0, true), 7);

        assertEquals(MinimumSupportMode.PERCENT, result.mode());
        assertEquals(14.0, result.configuredValue());
        assertEquals(0.14, result.relativeMinimumSupport().orElseThrow(), TOLERANCE);
        assertEquals(0.98, result.effectiveThreshold(), TOLERANCE);
        assertNotEquals(1.0, result.effectiveThreshold());
    }

    @Test
    void reproducesTableOneSupportAcceptanceBoundary()
            throws IOException, URISyntaxException {
        var database = new SpmfSequenceParser().parse(Path.of(requireNonNull(
                MinimumSupportResolverTest.class.getResource(
                        "/datasets/wspm/table1.spmf")).toURI()));
        var statistics = new ItemStatisticsCalculator().calculate(database);
        double meanWeight = new ItemWeightCalculator().calculate(statistics)
                .meanWeight()
                .orElseThrow();
        var result = resolver.resolve(
                MinimumSupport.percentage(14.0), statistics.numberOfSequences());

        assertTrue(result.meetsThreshold(
                statistics.statisticsFor(1).orElseThrow().sequenceSupport(), meanWeight));
        assertTrue(result.meetsThreshold(
                statistics.statisticsFor(2).orElseThrow().sequenceSupport(), meanWeight));
        assertTrue(result.meetsThreshold(
                statistics.statisticsFor(3).orElseThrow().sequenceSupport(), meanWeight));
        assertFalse(result.meetsThreshold(
                statistics.statisticsFor(4).orElseThrow().sequenceSupport(), meanWeight));
    }

    @Test
    void absoluteModeDoesNotScaleWithDatabaseSize() {
        var result = resolver.resolve(MinimumSupport.of(2.0, false), 7);

        assertEquals(MinimumSupportMode.ABSOLUTE, result.mode());
        assertEquals(2.0, result.configuredValue());
        assertTrue(result.relativeMinimumSupport().isEmpty());
        assertEquals(2.0, result.effectiveThreshold());
    }

    @Test
    void inclusiveComparisonAcceptsExactEquality() {
        var result = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        assertTrue(result.meetsThreshold(2, 0.5));
        assertFalse(result.meetsThreshold(1, 0.5));
    }

    @Test
    void validatesPercentageConfigurationBoundaries() {
        assertEquals(0.0,
                resolver.resolve(MinimumSupport.percentage(0.0), 2).effectiveThreshold());
        assertEquals(2.0,
                resolver.resolve(MinimumSupport.percentage(100.0), 2).effectiveThreshold());

        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.percentage(-0.01));
        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.percentage(100.01));
        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.percentage(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.percentage(Double.POSITIVE_INFINITY));
    }

    @Test
    void validatesAbsoluteConfigurationBoundaries() {
        assertEquals(0.0,
                resolver.resolve(MinimumSupport.absolute(0.0), 2).effectiveThreshold());
        assertEquals(0.25,
                resolver.resolve(MinimumSupport.absolute(0.25), 2).effectiveThreshold());

        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.absolute(-0.01));
        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.absolute(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MinimumSupport.absolute(Double.NEGATIVE_INFINITY));
    }

    @Test
    void rejectsPercentageResolutionForEmptyDatabaseButAllowsAbsoluteResolution() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(MinimumSupport.percentage(14.0), 0));

        var absolute = resolver.resolve(MinimumSupport.absolute(2.0), 0);
        assertEquals(2.0, absolute.effectiveThreshold());
        assertFalse(absolute.meetsThreshold(0, 1.0));
    }

    @Test
    void rejectsInvalidResolutionAndDecisionInputs() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null, 7));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(MinimumSupport.absolute(1.0), -1));

        var result = resolver.resolve(MinimumSupport.absolute(1.0), 7);
        assertThrows(IllegalArgumentException.class, () -> result.meetsThreshold(-1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> result.meetsThreshold(8, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> result.meetsThreshold(2, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> result.meetsThreshold(2, -0.5));
    }
}
