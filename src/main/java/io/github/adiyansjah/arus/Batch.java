package io.github.adiyansjah.arus;

record Batch(int size) {
    Batch {
        if (size <= 0) {
            throw new IllegalArgumentException("batch size must be greater than zero");
        }
    }
}
