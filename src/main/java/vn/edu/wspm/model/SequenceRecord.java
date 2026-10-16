package vn.edu.wspm.model;

import java.util.Objects;

/** A sequence database record containing its sequence identifier and sequence value. */
public record SequenceRecord(int sid, Sequence sequence) {

    public SequenceRecord {
        Objects.requireNonNull(sequence, "sequence must not be null");
    }
}
