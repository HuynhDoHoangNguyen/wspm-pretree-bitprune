package vn.edu.wspm.bitprune.statistics;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BitPruneStatisticsTest {

        private static final String[] EXPECTED_COMPONENT_NAMES = {
                        "sequenceCount",
                        "f1Count",
                        "generatedExtensions",
                        "generatedIExtensions",
                        "generatedSExtensions",
                        "cmapRejected",
                        "bitmapRejected",
                        "exactRejected",
                        "acceptedExtensions",
                        "expandedNodes",
                        "patternCount",
                        "exactVerifications"
        };

        // --- 1. Model Structure Tests ---

        @Test
        void testIsRecord() {
                assertTrue(BitPruneStatistics.class.isRecord(), "BitPruneStatistics must be a record");
        }

        @Test
        void testIsFinal() {
                assertTrue(isFinal(BitPruneStatistics.class.getModifiers()), "BitPruneStatistics must be final");
        }

        @Test
        void testRecordComponentCount() {
                RecordComponent[] components = BitPruneStatistics.class.getRecordComponents();
                assertNotNull(components, "Record components must not be null");
                assertEquals(12, components.length,
                                "BitPruneStatistics must have exactly 12 primary record components");
        }

        @Test
        void testRecordComponentNamesAndOrder() {
                RecordComponent[] components = BitPruneStatistics.class.getRecordComponents();
                assertEquals(EXPECTED_COMPONENT_NAMES.length, components.length);
                for (int i = 0; i < EXPECTED_COMPONENT_NAMES.length; i++) {
                        assertEquals(
                                        EXPECTED_COMPONENT_NAMES[i],
                                        components[i].getName(),
                                        "Component at index " + i + " must match expected name");
                }
        }

        @Test
        void testRecordComponentTypesArePrimitiveLong() {
                RecordComponent[] components = BitPruneStatistics.class.getRecordComponents();
                for (RecordComponent component : components) {
                        assertEquals(
                                        long.class,
                                        component.getType(),
                                        "Component '" + component.getName() + "' must be primitive long");
                }
        }

        @Test
        void testNoRejectedExtensionsComponent() {
                RecordComponent[] components = BitPruneStatistics.class.getRecordComponents();
                boolean hasRejectedComponent = Arrays.stream(components)
                                .anyMatch(c -> c.getName().equals("rejectedExtensions"));
                assertFalse(hasRejectedComponent, "rejectedExtensions must not be a record component");
        }

        @Test
        void testRejectedExtensionsMethodExists() throws NoSuchMethodException {
                Method method = BitPruneStatistics.class.getMethod("rejectedExtensions");
                assertNotNull(method, "rejectedExtensions method must exist");
                assertEquals(long.class, method.getReturnType(), "rejectedExtensions must return long");
                assertEquals(0, method.getParameterCount(), "rejectedExtensions must take no arguments");
        }

        // --- 2. Valid Snapshots Tests ---

        @Test
        void testZeroSnapshot() {
                BitPruneStatistics stats = new BitPruneStatistics(
                                0L, 0L, 0L, 0L, 0L,
                                0L, 0L, 0L, 0L, 0L,
                                0L, 0L);

                assertEquals(0L, stats.sequenceCount());
                assertEquals(0L, stats.f1Count());
                assertEquals(0L, stats.generatedExtensions());
                assertEquals(0L, stats.generatedIExtensions());
                assertEquals(0L, stats.generatedSExtensions());
                assertEquals(0L, stats.cmapRejected());
                assertEquals(0L, stats.bitmapRejected());
                assertEquals(0L, stats.exactRejected());
                assertEquals(0L, stats.acceptedExtensions());
                assertEquals(0L, stats.expandedNodes());
                assertEquals(0L, stats.patternCount());
                assertEquals(0L, stats.exactVerifications());
                assertEquals(0L, stats.rejectedExtensions());
        }

        @Test
        void testValidNonZeroSnapshot() {
                BitPruneStatistics stats = new BitPruneStatistics(
                                7L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);

                assertEquals(7L, stats.sequenceCount());
                assertEquals(3L, stats.f1Count());
                assertEquals(10L, stats.generatedExtensions());
                assertEquals(4L, stats.generatedIExtensions());
                assertEquals(6L, stats.generatedSExtensions());
                assertEquals(2L, stats.cmapRejected());
                assertEquals(1L, stats.bitmapRejected());
                assertEquals(3L, stats.exactRejected());
                assertEquals(4L, stats.acceptedExtensions());
                assertEquals(7L, stats.expandedNodes());
                assertEquals(7L, stats.patternCount());
                assertEquals(7L, stats.exactVerifications());
                assertEquals(6L, stats.rejectedExtensions());
        }

        // --- 3. Non-Negative Validation Tests ---

        @Test
        void testNegativeSequenceCountThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(-1L, 3L, 10L, 4L, 6L, 2L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("sequenceCount"),
                                "Message must mention sequenceCount: " + ex.getMessage());
        }

        @Test
        void testNegativeF1CountThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, -1L, 10L, 4L, 6L, 2L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("f1Count"), "Message must mention f1Count: " + ex.getMessage());
        }

        @Test
        void testNegativeGeneratedExtensionsThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, -1L, 4L, 6L, 2L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("generatedExtensions"),
                                "Message must mention generatedExtensions: " + ex.getMessage());
        }

        @Test
        void testNegativeGeneratedIExtensionsThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, -1L, 6L, 2L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("generatedIExtensions"),
                                "Message must mention generatedIExtensions: " + ex.getMessage());
        }

        @Test
        void testNegativeGeneratedSExtensionsThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, -1L, 2L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("generatedSExtensions"),
                                "Message must mention generatedSExtensions: " + ex.getMessage());
        }

        @Test
        void testNegativeCmapRejectedThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, -1L, 1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("cmapRejected"),
                                "Message must mention cmapRejected: " + ex.getMessage());
        }

        @Test
        void testNegativeBitmapRejectedThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, -1L, 3L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("bitmapRejected"),
                                "Message must mention bitmapRejected: " + ex.getMessage());
        }

        @Test
        void testNegativeExactRejectedThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, 1L, -1L, 4L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("exactRejected"),
                                "Message must mention exactRejected: " + ex.getMessage());
        }

        @Test
        void testNegativeAcceptedExtensionsThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, 1L, 3L, -1L, 7L, 7L, 7L));
                assertTrue(ex.getMessage().contains("acceptedExtensions"),
                                "Message must mention acceptedExtensions: " + ex.getMessage());
        }

        @Test
        void testNegativeExpandedNodesThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, 1L, 3L, 4L, -1L, 7L, 7L));
                assertTrue(ex.getMessage().contains("expandedNodes"),
                                "Message must mention expandedNodes: " + ex.getMessage());
        }

        @Test
        void testNegativePatternCountThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, 1L, 3L, 4L, 7L, -1L, 7L));
                assertTrue(ex.getMessage().contains("patternCount"),
                                "Message must mention patternCount: " + ex.getMessage());
        }

        @Test
        void testNegativeExactVerificationsThrows() {
                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> new BitPruneStatistics(7L, 3L, 10L, 4L, 6L, 2L, 1L, 3L, 4L, 7L, 7L, -1L));
                assertTrue(ex.getMessage().contains("exactVerifications"),
                                "Message must mention exactVerifications: " + ex.getMessage());
        }

        // --- 4. Invariant Rejection Tests (Each Tested Independently) ---

        @Test
        void testInvariant1RejectionWhenGeneratedDoesNotEqualIPlusS() {
                // I=4, S=6 => I+S=10; change I to 5 (I+S=11 != 10)
                // Keep Invariants 2, 3, 4 satisfied (terminalSum=10, exactVerifications=7,
                // patternCount=7)
                IllegalStateException ex = assertThrows(
                                IllegalStateException.class,
                                () -> new BitPruneStatistics(
                                                7L, 3L, 10L, 5L, 6L,
                                                2L, 1L, 3L, 4L, 7L,
                                                7L, 7L));
                assertTrue(
                                ex.getMessage().contains("generatedExtensions")
                                                && ex.getMessage().contains("generatedIExtensions"),
                                "Message must identify Invariant 1 violation: " + ex.getMessage());
        }

        @Test
        void testInvariant2RejectionWhenGeneratedDoesNotEqualTerminalOutcomeSum() {
                // Keep Invariant 1 satisfied (I=4, S=6 => 10 == generatedExtensions)
                // Keep Invariant 3 satisfied (exactRejected=3, accepted=4 =>
                // exactVerifications=7)
                // Keep Invariant 4 satisfied (f1=3, accepted=4 => patternCount=7)
                // Break Invariant 2 by changing cmapRejected from 2 to 3 (3+1+3+4 = 11 != 10)
                IllegalStateException ex = assertThrows(
                                IllegalStateException.class,
                                () -> new BitPruneStatistics(
                                                7L, 3L, 10L, 4L, 6L,
                                                3L, 1L, 3L, 4L, 7L,
                                                7L, 7L));
                assertTrue(
                                ex.getMessage().contains("cmapRejected")
                                                || ex.getMessage().contains("acceptedExtensions"),
                                "Message must identify Invariant 2 violation: " + ex.getMessage());
        }

        @Test
        void testInvariant3RejectionWhenExactVerificationsDoesNotEqualExactRejectedPlusAccepted() {
                // Keep Invariant 1 satisfied (4+6=10)
                // Keep Invariant 2 satisfied (2+1+3+4=10)
                // Keep Invariant 4 satisfied (3+4=7)
                // Break Invariant 3 by changing exactVerifications from 7 to 8 (3+4=7 != 8)
                IllegalStateException ex = assertThrows(
                                IllegalStateException.class,
                                () -> new BitPruneStatistics(
                                                7L, 3L, 10L, 4L, 6L,
                                                2L, 1L, 3L, 4L, 7L,
                                                7L, 8L));
                assertTrue(
                                ex.getMessage().contains("exactVerifications"),
                                "Message must identify Invariant 3 violation: " + ex.getMessage());
        }

        @Test
        void testInvariant4RejectionWhenPatternCountDoesNotEqualF1PlusAccepted() {
                // Keep Invariant 1 satisfied (4+6=10)
                // Keep Invariant 2 satisfied (2+1+3+4=10)
                // Keep Invariant 3 satisfied (3+4=7)
                // Break Invariant 4 by changing patternCount from 7 to 8 (3+4=7 != 8)
                IllegalStateException ex = assertThrows(
                                IllegalStateException.class,
                                () -> new BitPruneStatistics(
                                                7L, 3L, 10L, 4L, 6L,
                                                2L, 1L, 3L, 4L, 7L,
                                                8L, 7L));
                assertTrue(
                                ex.getMessage().contains("patternCount"),
                                "Message must identify Invariant 4 violation: " + ex.getMessage());
        }

        // --- 5. Overflow Tests (Checked Arithmetic with Math.addExact) ---

        @Test
        void testOverflowInInvariant1IExtensionsPlusSExtensions() {
                assertThrows(
                                ArithmeticException.class,
                                () -> new BitPruneStatistics(
                                                0L, 0L, 0L, Long.MAX_VALUE, 1L,
                                                0L, 0L, 0L, 0L, 0L,
                                                0L, 0L));
        }

        @Test
        void testOverflowInInvariant2TerminalOutcomeSumTier12() {
                assertThrows(
                                ArithmeticException.class,
                                () -> new BitPruneStatistics(
                                                0L, 0L, 0L, 0L, 0L,
                                                Long.MAX_VALUE, 1L, 0L, 0L, 0L,
                                                0L, 0L));
        }

        @Test
        void testOverflowInInvariant2CombinedTerminalSum() {
                long half = (Long.MAX_VALUE / 2) + 1L;
                assertThrows(
                                ArithmeticException.class,
                                () -> new BitPruneStatistics(
                                                0L, 0L, 0L, 0L, 0L,
                                                half, 0L, half, 0L, 0L,
                                                0L, half));
        }

        @Test
        void testOverflowInInvariant3ExactOutcomeSum() {
                assertThrows(
                                ArithmeticException.class,
                                () -> new BitPruneStatistics(
                                                0L, 0L, 0L, 0L, 0L,
                                                0L, 0L, Long.MAX_VALUE, 1L, 0L,
                                                0L, 0L));
        }

        @Test
        void testOverflowInInvariant4F1PlusAcceptedSum() {
                // Invariant 1: 1 = 1 + 0 (ok)
                // Invariant 2: 1 = 0 + 0 + 0 + 1 (ok)
                // Invariant 3: 1 = 0 + 1 (ok)
                // Invariant 4: f1 + accepted = Long.MAX_VALUE + 1 => overflow!
                assertThrows(
                                ArithmeticException.class,
                                () -> new BitPruneStatistics(
                                                0L, Long.MAX_VALUE, 1L, 1L, 0L,
                                                0L, 0L, 0L, 1L, 0L,
                                                0L, 1L));
        }

        // --- 6. Derived Value Tests ---

        @Test
        void testRejectedExtensionsFormula() {
                BitPruneStatistics stats = new BitPruneStatistics(
                                100L, 10L, 50L, 20L, 30L,
                                15L, 20L, 5L, 10L, 15L,
                                20L, 15L);

                // rejectedExtensions = cmapRejected (15) + bitmapRejected (20) + exactRejected
                // (5) = 40
                assertEquals(40L, stats.rejectedExtensions());
        }

        @Test
        void testRejectedExtensionsIsIdempotent() {
                BitPruneStatistics stats = new BitPruneStatistics(
                                7L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);

                long first = stats.rejectedExtensions();
                long second = stats.rejectedExtensions();
                long third = stats.rejectedExtensions();

                assertEquals(6L, first);
                assertEquals(first, second);
                assertEquals(second, third);
        }

        // --- 7. Record Reproducibility Tests ---

        @Test
        void testRecordEqualityAndHashCode() {
                BitPruneStatistics a = new BitPruneStatistics(
                                7L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);
                BitPruneStatistics b = new BitPruneStatistics(
                                7L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);
                BitPruneStatistics c = new BitPruneStatistics(
                                8L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);

                assertEquals(a, b);
                assertEquals(a.hashCode(), b.hashCode());
                assertNotEquals(a, c);
                assertNotEquals(a.hashCode(), c.hashCode());
        }

        @Test
        void testRecordToStringContainsComponents() {
                BitPruneStatistics stats = new BitPruneStatistics(
                                7L, 3L, 10L, 4L, 6L,
                                2L, 1L, 3L, 4L, 7L,
                                7L, 7L);

                String str = stats.toString();
                assertNotNull(str);
                assertTrue(str.contains("BitPruneStatistics"));
                assertTrue(str.contains("sequenceCount=7"));
                assertTrue(str.contains("f1Count=3"));
                assertTrue(str.contains("generatedExtensions=10"));
                assertTrue(str.contains("cmapRejected=2"));
                assertTrue(str.contains("bitmapRejected=1"));
                assertTrue(str.contains("exactRejected=3"));
                assertTrue(str.contains("acceptedExtensions=4"));
        }
}
