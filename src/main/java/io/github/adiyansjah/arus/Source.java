package io.github.adiyansjah.arus;

import java.util.Optional;

interface Source extends AutoCloseable {
    Optional<Batch> next(int maximumSize) throws TransferException;

    @Override
    void close() throws TransferException;
}
