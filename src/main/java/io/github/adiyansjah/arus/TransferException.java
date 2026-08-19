package io.github.adiyansjah.arus;

import java.io.Serial;

final class TransferException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    TransferException(String message) {
        super(message);
    }

    TransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
