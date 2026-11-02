package vn.edu.wspm.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** An immutable ordered collection of uniquely identified sequence records. */
public final class SequenceDatabase {

    private final List<SequenceRecord> records;

    public SequenceDatabase(Collection<SequenceRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        var seenSids = new HashSet<Integer>();
        for (var record : records) {
            Objects.requireNonNull(record, "a sequence database must not contain null records");
            if (!seenSids.add(record.sid())) {
                throw new IllegalArgumentException("duplicate sequence identifier: " + record.sid());
            }
        }
        this.records = List.copyOf(records);
    }

    public List<SequenceRecord> records() {
        return records;
    }

    public int size() {
        return records.size();
    }
}
