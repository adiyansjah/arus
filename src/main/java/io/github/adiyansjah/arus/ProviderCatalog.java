package io.github.adiyansjah.arus;

import java.util.Collection;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class ProviderCatalog {
    private final Iterable<SourceProvider> sourceProviders;
    private final Iterable<SinkProvider> sinkProviders;

    private ProviderCatalog(
            ServiceLoader<SourceProvider> sourceProviders,
            ServiceLoader<SinkProvider> sinkProviders) {
        this.sourceProviders = sourceProviders;
        this.sinkProviders = sinkProviders;
    }

    ProviderCatalog(
            Collection<SourceProvider> sourceProviders,
            Collection<SinkProvider> sinkProviders) {
        this.sourceProviders = List.copyOf(sourceProviders);
        this.sinkProviders = List.copyOf(sinkProviders);
    }

    static ProviderCatalog load() {
        return new ProviderCatalog(
                ServiceLoader.load(SourceProvider.class),
                ServiceLoader.load(SinkProvider.class));
    }

    SourceProvider source(String id) throws TransferException {
        SourceProvider match = null;
        try {
            for (var provider : sourceProviders) {
                if (!provider.id().equals(id)) {
                    continue;
                }
                if (match != null) {
                    throw new TransferException("duplicate source provider: " + id);
                }
                match = provider;
            }
        } catch (ServiceConfigurationError error) {
            throw new TransferException("cannot load source providers", error);
        }
        if (match == null) {
            throw new TransferException("missing source provider: " + id);
        }
        return match;
    }

    SinkProvider sink(String id) throws TransferException {
        SinkProvider match = null;
        try {
            for (var provider : sinkProviders) {
                if (!provider.id().equals(id)) {
                    continue;
                }
                if (match != null) {
                    throw new TransferException("duplicate sink provider: " + id);
                }
                match = provider;
            }
        } catch (ServiceConfigurationError error) {
            throw new TransferException("cannot load sink providers", error);
        }
        if (match == null) {
            throw new TransferException("missing sink provider: " + id);
        }
        return match;
    }
}
