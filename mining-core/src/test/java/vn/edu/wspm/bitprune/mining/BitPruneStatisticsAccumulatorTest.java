package vn.edu.wspm.bitprune.mining;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;

class BitPruneStatisticsAccumulatorTest {

    // --- A. Visibility, Ownership and Structure Tests ---

    @Test
    void testAccumulatorClassOwnershipAndPackage() {
        Class<?> clazz = BitPruneStatisticsAccumulator.class;
        assertEquals(
                "vn.edu.wspm.bitprune.mining",
                clazz.getPackageName(),
                "BitPruneStatisticsAccumulator must belong to vn.edu.wspm.bitprune.mining");
        int modifiers = clazz.getModifiers();
        assertTrue(isFinal(modifiers), "BitPruneStatisticsAccumulator must be final");
        assertFalse(isPublic(modifiers), "BitPruneStatisticsAccumulator must not be public");
        assertFalse(isProtected(modifiers), "BitPruneStatisticsAccumulator must not be protected");
        assertFalse(isPrivate(modifiers), "BitPruneStatisticsAccumulator must not be private");
    }

    @Test
    void testConstructorIsPackagePrivate() throws NoSuchMethodException {
        Constructor<?> constructor = BitPruneStatisticsAccumulator.class
                .getDeclaredConstructor(long.class, long.class);
        int modifiers = constructor.getModifiers();

        assertFalse(isPublic(modifiers), "Constructor must not be public");
        assertFalse(isProtected(modifiers), "Constructor must not be protected");
        assertFalse(isPrivate(modifiers), "Constructor must not be private");
    }

    @Test
    void testNoPublicFieldsOrMethods() {
        Field[] publicFields = BitPruneStatisticsAccumulator.class.getFields();
        assertEquals(0, publicFields.length, "Accumulator must have no public fields");

        Method[] methods = BitPruneStatisticsAccumulator.class.getDeclaredMethods();
        for (Method m : methods) {
            assertFalse(
                    isPublic(m.getModifiers()),
                    "Method " + m.getName() + " must not be public");
        }
    }

    @Test
    void testFinishMethodReturnsBitPruneStatistics() throws NoSuchMethodException {
        Method finishMethod = BitPruneStatisticsAccumulator.class.getDeclaredMethod("finish", long.class);
        assertEquals(
                BitPruneStatistics.class,
                finishMethod.getReturnType(),
                "finish(long) must return vn.edu.wspm.bitprune.statistics.BitPruneStatistics");
    }

    @Test
    void testOldStatisticsPackageDoesNotContainAccumulator() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("vn.edu.wspm.bitprune.statistics.BitPruneStatisticsAccumulator"),
                "Source package statistics must no longer contain BitPruneStatisticsAccumulator");
    }

    @Test
    void testDirectPackageAccessWithoutReflection() {
        // Verifies that code in vn.edu.wspm.bitprune.mining can directly instantiate
        // and invoke the accumulator
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(10L, 2L);
        acc.recordGeneratedIExtension();
        acc.recordAcceptedExtension();
        acc.recordExpandedNode();
        BitPruneStatistics stats = acc.finish(3L);

        assertNotNull(stats);
        assertEquals(3L, stats.patternCount());
    }

    @Test
    void testNoForbiddenDependenciesInClass() {
        String[] forbiddenPrefixes = {
                "vn.edu.wspm.tree",
                "vn.edu.wspm.measurement",
                "vn.edu.wspm.prism"
        };

        Field[] fields = BitPruneStatisticsAccumulator.class.getDeclaredFields();
        for (Field f : fields) {
            String typeName = f.getType().getName();
            for (String prefix : forbiddenPrefixes) {
                assertFalse(
                        typeName.startsWith(prefix),
                        "Field " + f.getName() + " must not reference " + prefix);
            }
        }

        Method[] methods = BitPruneStatisticsAccumulator.class.getDeclaredMethods();
        for (Method m : methods) {
            String returnTypeName = m.getReturnType().getName();
            for (String prefix : forbiddenPrefixes) {
                assertFalse(
                        returnTypeName.startsWith(prefix),
                        "Method " + m.getName() + " return type must not reference " + prefix);
            }
            for (Class<?> paramType : m.getParameterTypes()) {
                for (String prefix : forbiddenPrefixes) {
                    assertFalse(
                            paramType.getName().startsWith(prefix),
                            "Method " + m.getName() + " parameter must not reference " + prefix);
                }
            }
        }
    }

    // --- B. Constructor Validation Tests ---

    @Test
    void testValidZeroSequenceAndF1Count() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        assertNotNull(acc);
    }

    @Test
    void testNegativeSequenceCountThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new BitPruneStatisticsAccumulator(-1L, 0L));
        assertTrue(
                ex.getMessage().contains("sequenceCount"),
                "Message must identify sequenceCount: " + ex.getMessage());
    }

    @Test
    void testNegativeF1CountThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new BitPruneStatisticsAccumulator(0L, -1L));
        assertTrue(
                ex.getMessage().contains("f1Count"),
                "Message must identify f1Count: " + ex.getMessage());
    }

    // --- C. Zero Snapshot Test ---

    @Test
    void testZeroSnapshot() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        BitPruneStatistics stats = acc.finish(0L);

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

    // --- D. Controlled Mixed-Routing Snapshot Test ---

    @Test
    void testControlledMixedRoutingSnapshot() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(7L, 3L);

        // 1. I-extension -> CMAP_REJECTED
        acc.recordGeneratedIExtension();
        acc.recordCmapRejected();

        // 2. S-extension -> BITMAP_REJECTED
        acc.recordGeneratedSExtension();
        acc.recordBitmapRejected();

        // 3. I-extension -> EXACT_REJECTED
        acc.recordGeneratedIExtension();
        acc.recordExactRejected();

        // 4. S-extension -> ACCEPTED
        acc.recordGeneratedSExtension();
        acc.recordAcceptedExtension();

        // Record expanded nodes
        acc.recordExpandedNode();
        acc.recordExpandedNode();
        acc.recordExpandedNode();
        acc.recordExpandedNode();

        // patternCount = f1Count (3) + acceptedExtensions (1) = 4
        BitPruneStatistics stats = acc.finish(4L);

        assertEquals(7L, stats.sequenceCount());
        assertEquals(3L, stats.f1Count());
        assertEquals(4L, stats.generatedExtensions());
        assertEquals(2L, stats.generatedIExtensions());
        assertEquals(2L, stats.generatedSExtensions());
        assertEquals(1L, stats.cmapRejected());
        assertEquals(1L, stats.bitmapRejected());
        assertEquals(1L, stats.exactRejected());
        assertEquals(1L, stats.acceptedExtensions());
        assertEquals(2L, stats.exactVerifications());
        assertEquals(3L, stats.rejectedExtensions());
        assertEquals(4L, stats.expandedNodes());
        assertEquals(4L, stats.patternCount());
    }

    // --- E. I/S Partition Tests ---

    @Test
    void testIExtensionPartition() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(5L, 2L);

        acc.recordGeneratedIExtension();
        acc.recordAcceptedExtension();

        BitPruneStatistics stats = acc.finish(3L);
        assertEquals(1L, stats.generatedExtensions());
        assertEquals(1L, stats.generatedIExtensions());
        assertEquals(0L, stats.generatedSExtensions());
    }

    @Test
    void testSExtensionPartition() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(5L, 2L);

        acc.recordGeneratedSExtension();
        acc.recordAcceptedExtension();

        BitPruneStatistics stats = acc.finish(3L);
        assertEquals(1L, stats.generatedExtensions());
        assertEquals(0L, stats.generatedIExtensions());
        assertEquals(1L, stats.generatedSExtensions());
    }

    // --- F. Exact-Verification Routing Tests ---

    @Test
    void testCmapRejectionDoesNotIncrementExactVerifications() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.recordGeneratedIExtension();
        acc.recordCmapRejected();

        BitPruneStatistics stats = acc.finish(0L);
        assertEquals(1L, stats.cmapRejected());
        assertEquals(0L, stats.exactVerifications());
    }

    @Test
    void testBitmapRejectionDoesNotIncrementExactVerifications() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.recordGeneratedSExtension();
        acc.recordBitmapRejected();

        BitPruneStatistics stats = acc.finish(0L);
        assertEquals(1L, stats.bitmapRejected());
        assertEquals(0L, stats.exactVerifications());
    }

    @Test
    void testExactRejectionIncrementsExactVerifications() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.recordGeneratedIExtension();
        acc.recordExactRejected();

        BitPruneStatistics stats = acc.finish(0L);
        assertEquals(1L, stats.exactRejected());
        assertEquals(1L, stats.exactVerifications());
    }

    @Test
    void testAcceptedExtensionIncrementsExactVerifications() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.recordGeneratedSExtension();
        acc.recordAcceptedExtension();

        BitPruneStatistics stats = acc.finish(1L);
        assertEquals(1L, stats.acceptedExtensions());
        assertEquals(1L, stats.exactVerifications());
    }

    // --- G. Pending Candidate State Machine & Overlap Prevention ---

    @Test
    void testTerminalOutcomesWithoutPendingCandidateThrow() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);

        assertThrows(IllegalStateException.class, acc::recordCmapRejected);
        assertThrows(IllegalStateException.class, acc::recordBitmapRejected);
        assertThrows(IllegalStateException.class, acc::recordExactRejected);
        assertThrows(IllegalStateException.class, acc::recordAcceptedExtension);
    }

    @Test
    void testGeneratingCandidateWhileAnotherPendingThrows() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);

        acc.recordGeneratedIExtension();
        assertThrows(IllegalStateException.class, acc::recordGeneratedIExtension);
        assertThrows(IllegalStateException.class, acc::recordGeneratedSExtension);
    }

    @Test
    void testDuplicateTerminalOutcomeForSameCandidateThrows() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);

        acc.recordGeneratedIExtension();
        acc.recordCmapRejected();

        // Second terminal outcome must fail because pending state is now cleared
        assertThrows(IllegalStateException.class, acc::recordCmapRejected);
        assertThrows(IllegalStateException.class, acc::recordBitmapRejected);
        assertThrows(IllegalStateException.class, acc::recordExactRejected);
        assertThrows(IllegalStateException.class, acc::recordAcceptedExtension);
    }

    @Test
    void testSequentialCandidateWorkflowProceedsCleanly() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(2L, 1L);

        acc.recordGeneratedIExtension();
        acc.recordCmapRejected();

        // Allowed to generate next candidate after terminal outcome
        acc.recordGeneratedSExtension();
        acc.recordAcceptedExtension();

        BitPruneStatistics stats = acc.finish(2L);
        assertEquals(2L, stats.generatedExtensions());
        assertEquals(1L, stats.cmapRejected());
        assertEquals(1L, stats.acceptedExtensions());
    }

    // --- H. Finish Lifecycle Tests ---

    @Test
    void testFinishWhileCandidatePendingThrows() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.recordGeneratedIExtension();

        assertThrows(IllegalStateException.class, () -> acc.finish(0L));
    }

    @Test
    void testFinishIsOneShot() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.finish(0L);

        assertThrows(IllegalStateException.class, () -> acc.finish(0L));
    }

    @Test
    void testMutationAfterFinishThrows() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 0L);
        acc.finish(0L);

        assertThrows(IllegalStateException.class, acc::recordGeneratedIExtension);
        assertThrows(IllegalStateException.class, acc::recordGeneratedSExtension);
        assertThrows(IllegalStateException.class, acc::recordCmapRejected);
        assertThrows(IllegalStateException.class, acc::recordBitmapRejected);
        assertThrows(IllegalStateException.class, acc::recordExactRejected);
        assertThrows(IllegalStateException.class, acc::recordAcceptedExtension);
        assertThrows(IllegalStateException.class, acc::recordExpandedNode);
    }

    // --- I. Failed Finish Closes Accumulator Tests ---

    @Test
    void testFailedFinishDueToPatternCountMismatchClosesAccumulator() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(2L, 1L);
        acc.recordGeneratedIExtension();
        acc.recordAcceptedExtension();

        // f1Count=1, acceptedExtensions=1 => patternCount must be 2; 999 violates
        // invariant!
        assertThrows(IllegalStateException.class, () -> acc.finish(999L));

        // Accumulator must now be permanently closed
        assertThrows(IllegalStateException.class, acc::recordGeneratedIExtension);
        assertThrows(IllegalStateException.class, acc::recordExpandedNode);
        assertThrows(IllegalStateException.class, () -> acc.finish(2L));
    }

    @Test
    void testFailedFinishDueToNegativePatternCountClosesAccumulator() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(2L, 0L);

        assertThrows(IllegalArgumentException.class, () -> acc.finish(-1L));

        // Accumulator must now be permanently closed
        assertThrows(IllegalStateException.class, acc::recordGeneratedIExtension);
        assertThrows(IllegalStateException.class, () -> acc.finish(0L));
    }

    // --- J. Checked Overflow & Atomicity Tests (Reflection Seeding) ---

    @Test
    void testOverflowGeneratedExtensionsInIExtension() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "generatedExtensions", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, acc::recordGeneratedIExtension);
    }

    @Test
    void testOverflowGeneratedIExtensionsAtomicity() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "generatedIExtensions", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, acc::recordGeneratedIExtension);

        // Atomicity check: generatedExtensions was not assigned, candidatePending
        // remained false
        assertEquals(0L, getPrivateField(acc, "generatedExtensions"));
        assertFalse((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowGeneratedSExtensionsAtomicity() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "generatedSExtensions", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, acc::recordGeneratedSExtension);

        // Atomicity check: generatedExtensions was not assigned, candidatePending
        // remained false
        assertEquals(0L, getPrivateField(acc, "generatedExtensions"));
        assertFalse((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowCmapRejected() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "cmapRejected", Long.MAX_VALUE);
        acc.recordGeneratedIExtension();

        assertThrows(ArithmeticException.class, acc::recordCmapRejected);
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowBitmapRejected() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "bitmapRejected", Long.MAX_VALUE);
        acc.recordGeneratedSExtension();

        assertThrows(ArithmeticException.class, acc::recordBitmapRejected);
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowExactRejected() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "exactRejected", Long.MAX_VALUE);
        acc.recordGeneratedIExtension();

        assertThrows(ArithmeticException.class, acc::recordExactRejected);
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowExactVerificationsOnExactRejectedAtomicity() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "exactVerifications", Long.MAX_VALUE);
        acc.recordGeneratedIExtension();

        assertThrows(ArithmeticException.class, acc::recordExactRejected);

        // Atomicity check: exactRejected was not mutated, candidatePending remained
        // true
        assertEquals(0L, getPrivateField(acc, "exactRejected"));
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowAcceptedExtensions() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "acceptedExtensions", Long.MAX_VALUE);
        acc.recordGeneratedSExtension();

        assertThrows(ArithmeticException.class, acc::recordAcceptedExtension);
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowExactVerificationsOnAcceptedExtensionAtomicity() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "exactVerifications", Long.MAX_VALUE);
        acc.recordGeneratedSExtension();

        assertThrows(ArithmeticException.class, acc::recordAcceptedExtension);

        // Atomicity check: acceptedExtensions was not mutated, candidatePending
        // remained true
        assertEquals(0L, getPrivateField(acc, "acceptedExtensions"));
        assertTrue((boolean) getPrivateField(acc, "candidatePending"));
    }

    @Test
    void testOverflowExpandedNodes() throws Exception {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(0L, 0L);
        setPrivateField(acc, "expandedNodes", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, acc::recordExpandedNode);
    }

    // --- K. Independent Snapshots Test ---

    @Test
    void testIndependentSnapshots() {
        BitPruneStatisticsAccumulator acc1 = new BitPruneStatisticsAccumulator(5L, 2L);
        BitPruneStatisticsAccumulator acc2 = new BitPruneStatisticsAccumulator(10L, 4L);

        acc1.recordGeneratedIExtension();
        acc1.recordAcceptedExtension();

        BitPruneStatistics stats1 = acc1.finish(3L);

        acc2.recordGeneratedSExtension();
        acc2.recordCmapRejected();
        BitPruneStatistics stats2 = acc2.finish(4L);

        assertEquals(5L, stats1.sequenceCount());
        assertEquals(1L, stats1.acceptedExtensions());
        assertEquals(0L, stats1.cmapRejected());

        assertEquals(10L, stats2.sequenceCount());
        assertEquals(0L, stats2.acceptedExtensions());
        assertEquals(1L, stats2.cmapRejected());
    }

    // --- L. Equality and Consistency Test ---

    @Test
    void testReturnedStatisticsConsistency() {
        BitPruneStatisticsAccumulator acc = new BitPruneStatisticsAccumulator(1L, 1L);
        acc.recordGeneratedIExtension();
        acc.recordAcceptedExtension();
        BitPruneStatistics stats = acc.finish(2L);

        assertNotNull(stats);
        assertEquals(2L, stats.patternCount());
        assertEquals(stats, new BitPruneStatistics(
                1L, 1L, 1L, 1L, 0L,
                0L, 0L, 0L, 1L, 0L,
                2L, 1L));
    }

    // --- Reflection Helpers (Test Scope Only) ---

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
