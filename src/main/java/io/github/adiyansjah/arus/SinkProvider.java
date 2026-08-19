package io.github.adiyansjah.arus;

interface SinkProvider {
    String id();

    Sink open() throws TransferException;
}
