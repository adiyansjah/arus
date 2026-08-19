package io.github.adiyansjah.arus;

interface SourceProvider {
    String id();

    Source open(long records) throws TransferException;
}
