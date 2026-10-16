package vn.edu.wspm.measurement;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** Samples simultaneously used Java heap for phase, algorithm, and total-execution peaks. */
public final class ExecutionMemorySampler implements AutoCloseable {

    public static final long DEFAULT_SAMPLING_INTERVAL_MILLIS = 5L;
    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    private final MemoryMXBean memoryBean;
    private final long samplingIntervalMillis;
    private final long samplingIntervalNanos;
    private final List<HeapCheckpoint> samples = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Throwable> samplingFailure = new AtomicReference<>();
    private final Thread samplingThread;

    public ExecutionMemorySampler() {
        this(ManagementFactory.getMemoryMXBean(), DEFAULT_SAMPLING_INTERVAL_MILLIS);
    }

    public ExecutionMemorySampler(long samplingIntervalMillis) {
        this(ManagementFactory.getMemoryMXBean(), samplingIntervalMillis);
    }

    ExecutionMemorySampler(MemoryMXBean memoryBean, long samplingIntervalMillis) {
        if (samplingIntervalMillis <= 0L) {
            throw new IllegalArgumentException("sampling interval must be positive");
        }
        this.memoryBean = java.util.Objects.requireNonNull(
                memoryBean, "memory bean must not be null");
        this.samplingIntervalMillis = samplingIntervalMillis;
        this.samplingIntervalNanos = Math.multiplyExact(samplingIntervalMillis, 1_000_000L);
        checkpoint();
        this.samplingThread = Thread.ofPlatform()
                .daemon(true)
                .name("wspm-heap-sampler-" + THREAD_SEQUENCE.incrementAndGet())
                .unstarted(this::sampleUntilClosed);
        this.samplingThread.start();
    }

    /** Records a synchronous boundary sample and returns its immutable value. */
    public HeapCheckpoint checkpoint() {
        ensureHealthy();
        long usedBytes = memoryBean.getHeapMemoryUsage().getUsed();
        if (usedBytes < 0L) {
            throw new IllegalStateException("JVM reported negative used heap");
        }
        var checkpoint = new HeapCheckpoint(System.nanoTime(), usedBytes);
        synchronized (samples) {
            samples.add(checkpoint);
        }
        return checkpoint;
    }

    /** Returns the largest sampled used-heap value inside the inclusive checkpoint range. */
    public long observedPeakBytes(HeapCheckpoint start, HeapCheckpoint end) {
        java.util.Objects.requireNonNull(start, "start checkpoint must not be null");
        java.util.Objects.requireNonNull(end, "end checkpoint must not be null");
        if (end.nanoTime() < start.nanoTime()) {
            throw new IllegalArgumentException("end checkpoint must not precede start checkpoint");
        }
        ensureHealthy();
        long peak = Math.max(start.heapUsedBytes(), end.heapUsedBytes());
        synchronized (samples) {
            for (var sample : samples) {
                if (sample.nanoTime() >= start.nanoTime()
                        && sample.nanoTime() <= end.nanoTime()) {
                    peak = Math.max(peak, sample.heapUsedBytes());
                }
            }
        }
        return peak;
    }

    public long samplingIntervalMillis() {
        return samplingIntervalMillis;
    }

    boolean isSamplingThreadAlive() {
        return samplingThread.isAlive();
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            samplingThread.interrupt();
            boolean interrupted = false;
            while (samplingThread.isAlive()) {
                try {
                    samplingThread.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        ensureHealthy();
    }

    private void sampleUntilClosed() {
        try {
            while (running.get()) {
                checkpoint();
                LockSupport.parkNanos(samplingIntervalNanos);
            }
        } catch (Throwable failure) {
            samplingFailure.compareAndSet(null, failure);
            running.set(false);
        }
    }

    private void ensureHealthy() {
        Throwable failure = samplingFailure.get();
        if (failure != null) {
            throw new IllegalStateException("heap sampler failed", failure);
        }
    }

    /** One simultaneous observation of elapsed-clock position and Java heap used. */
    public record HeapCheckpoint(long nanoTime, long heapUsedBytes) {
        public HeapCheckpoint {
            if (heapUsedBytes < 0L) {
                throw new IllegalArgumentException("heap used must not be negative");
            }
        }
    }
}
