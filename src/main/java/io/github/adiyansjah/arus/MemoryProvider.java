package io.github.adiyansjah.arus;

import java.util.Optional;

public final class MemoryProvider implements SourceProvider, SinkProvider {
    public MemoryProvider() {}

    @Override
    public String id() {
        return "memory";
    }

    @Override
    public Source open(long records) {
        return new MemorySource(records);
    }

    @Override
    public Sink open() {
        return new MemorySink();
    }

    private static final class MemorySource implements Source {
        private long remaining;

        private MemorySource(long records) {
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
        public void close() {}
    }

    private static final class MemorySink implements Sink {
        @Override
        public void accept(Batch batch) {}

        @Override
        public void close() {}
    }
}
