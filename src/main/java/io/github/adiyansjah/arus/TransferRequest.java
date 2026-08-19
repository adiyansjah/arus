package io.github.adiyansjah.arus;

record TransferRequest(long records, int batchSize) {
    TransferRequest {
        if (records < 0) {
            throw new IllegalArgumentException("--records must not be negative");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("--batch-size must be greater than zero");
        }
    }
}
