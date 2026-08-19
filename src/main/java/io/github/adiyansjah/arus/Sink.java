package io.github.adiyansjah.arus;

interface Sink extends AutoCloseable {
    void accept(Batch batch) throws TransferException;

    @Override
    void close() throws TransferException;
}
