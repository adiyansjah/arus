package io.github.adiyansjah.arus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TransferTest {
    @Test
    void transfersOneBatchAtATimeAndClosesResources() throws TransferException {
        var source = new RecordingSource(10);
        var sink = new RecordingSink(false);
        var transfer = new Transfer(catalog(source, sink));

        var result = transfer.run(new TransferRequest(10, 4));

        assertAll(
                () -> assertEquals(new TransferResult(10, 3), result),
                () -> assertEquals(List.of(4, 4, 2), sink.batchSizes),
                () -> assertTrue(source.closed),
                () -> assertTrue(sink.closed));
    }

    @Test
    void closesResourcesWhenTheSinkFails() {
        var source = new RecordingSource(10);
        var sink = new RecordingSink(true);
        var transfer = new Transfer(catalog(source, sink));

        var exception = assertThrows(
                TransferException.class,
                () -> transfer.run(new TransferRequest(10, 4)));

        assertAll(
                () -> assertEquals("sink failed", exception.getMessage()),
                () -> assertTrue(source.closed),
                () -> assertTrue(sink.closed));
    }

    @Test
    void rejectsAMissingProvider() {
        var transfer = new Transfer(new ProviderCatalog(List.of(), List.of()));

        var exception = assertThrows(
                TransferException.class,
                () -> transfer.run(new TransferRequest(10, 4)));

        assertEquals("missing source provider: memory", exception.getMessage());
    }

    @Test
    void rejectsDuplicateProviders() {
        var source = new RecordingSource(10);
        var sink = new RecordingSink(false);
        var transfer = new Transfer(new ProviderCatalog(
                List.of(sourceProvider(source), sourceProvider(source)),
                List.of(sinkProvider(sink))));

        var exception = assertThrows(
                TransferException.class,
                () -> transfer.run(new TransferRequest(10, 4)));

        assertEquals("duplicate source provider: memory", exception.getMessage());
    }

    @Test
    void rejectsAnOversizedBatchAndClosesResources() {
        class OversizedSource implements Source {
            private boolean returned;
            private boolean closed;

            @Override
            public Optional<Batch> next(int maximumSize) {
                if (returned) {
                    return Optional.empty();
                }
                returned = true;
                return Optional.of(new Batch(5));
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var source = new OversizedSource();
        var sink = new RecordingSink(false);
        var transfer = new Transfer(catalog(source, sink));

        var exception = assertThrows(
                TransferException.class,
                () -> transfer.run(new TransferRequest(10, 4)));

        assertAll(
                () -> assertEquals("source returned an oversized batch", exception.getMessage()),
                () -> assertTrue(source.closed),
                () -> assertTrue(sink.closed));
    }

    private static ProviderCatalog catalog(Source source, Sink sink) {
        return new ProviderCatalog(
                List.of(sourceProvider(source)),
                List.of(sinkProvider(sink)));
    }

    private static SourceProvider sourceProvider(Source source) {
        return new SourceProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Source open(long records) {
                return source;
            }
        };
    }

    private static SinkProvider sinkProvider(Sink sink) {
        return new SinkProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Sink open() {
                return sink;
            }
        };
    }

    private static final class RecordingSource implements Source {
        private long remaining;
        private boolean closed;

        private RecordingSource(long records) {
            remaining = records;
        }

        @Override
        public Optional<Batch> next(int maximumSize) {
            if (remaining == 0) {
                return Optional.empty();
            }
            int size = (int) Math.min(remaining, maximumSize);
            remaining -= size;
            return Optional.of(new Batch(size));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingSink implements Sink {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final boolean fail;
        private boolean closed;

        private RecordingSink(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void accept(Batch batch) throws TransferException {
            if (fail) {
                throw new TransferException("sink failed");
            }
            batchSizes.add(batch.size());
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
