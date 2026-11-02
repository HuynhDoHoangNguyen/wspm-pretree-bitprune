package vn.edu.wspm.mining;

/**
 * Governs the post-mining retention lifecycle of internal Prime-block encodings.
 *
 * <p>Both policies execute the exact same DFS traversal and produce identical
 * pattern-support outcomes. They differ solely in whether heavy compact position
 * encodings are retained in {@link WspmMiningResult} after traversal completes.</p>
 */
public enum ResultRetentionPolicy {

    /**
     * Retains all compact Prime-block encodings in the mining result.
     *
     * <p>Intended for research, differential correctness testing, and supporting SID/position audits.
     * Retains large heap arrays proportional to the number of accepted patterns.</p>
     */
    FULL_ENCODING,

    /**
     * Retains only accepted patterns, integer supports, and Prefix Tree structure.
     *
     * <p>Internal Prime-block encodings are discarded as DFS subtrees complete.
     * Recommended for production mining and high-pattern benchmarks (such as Pumsb 0.60%).</p>
     */
    SUPPORT_ONLY
}
