package io.github.adiyansjah.arus;

import java.util.Objects;

final class Transfer {
    private static final String MEMORY_PROVIDER = "memory";

    private final ProviderCatalog providers;

    Transfer(ProviderCatalog providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    TransferResult run(TransferRequest request) throws TransferException {
        Objects.requireNonNull(request);

        var sourceProvider = providers.source(MEMORY_PROVIDER);
        var sinkProvider = providers.sink(MEMORY_PROVIDER);

        try (var source = sourceProvider.open(request.records());
                var sink = sinkProvider.open()) {
            long records = 0;
            long batches = 0;

            for (var next = source.next(request.batchSize());
                    next.isPresent();
                    next = source.next(request.batchSize())) {
                var batch = next.orElseThrow();
                if (batch.size() > request.batchSize()) {
                    throw new TransferException("source returned an oversized batch");
                }
                sink.accept(batch);
                records += batch.size();
                batches++;
            }

            return new TransferResult(records, batches);
        }
    }
}
