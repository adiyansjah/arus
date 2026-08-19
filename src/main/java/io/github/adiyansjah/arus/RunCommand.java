package io.github.adiyansjah.arus;

import java.io.PrintWriter;
import java.util.Objects;

final class RunCommand {
    private final Transfer transfer;
    private final PrintWriter stdout;
    private final PrintWriter stderr;

    RunCommand(Transfer transfer, PrintWriter stdout, PrintWriter stderr) {
        this.transfer = Objects.requireNonNull(transfer);
        this.stdout = Objects.requireNonNull(stdout);
        this.stderr = Objects.requireNonNull(stderr);
    }

    int execute(String[] args) {
        TransferRequest request;
        try {
            request = parse(args);
        } catch (IllegalArgumentException exception) {
            stderr.println("error: " + exception.getMessage());
            stderr.flush();
            return 2;
        }

        try {
            var result = transfer.run(request);
            stdout.printf(
                    "Transferred %d records in %d batches.%n",
                    result.records(),
                    result.batches());
            stdout.flush();
            return 0;
        } catch (TransferException exception) {
            stderr.println("error: " + exception.getMessage());
            stderr.flush();
            return 1;
        }
    }

    private static TransferRequest parse(String[] args) {
        Objects.requireNonNull(args);
        if (args.length != 5 || !args[0].equals("run")) {
            throw new IllegalArgumentException(
                    "usage: run --records <count> --batch-size <size>");
        }

        long records = 0;
        int batchSize = 0;
        boolean recordsSeen = false;
        boolean batchSizeSeen = false;

        try {
            for (int index = 1; index < args.length; index += 2) {
                switch (args[index]) {
                    case "--records" -> {
                        if (recordsSeen) {
                            throw new IllegalArgumentException("duplicate option: --records");
                        }
                        records = Long.parseLong(args[index + 1]);
                        recordsSeen = true;
                    }
                    case "--batch-size" -> {
                        if (batchSizeSeen) {
                            throw new IllegalArgumentException("duplicate option: --batch-size");
                        }
                        batchSize = Integer.parseInt(args[index + 1]);
                        batchSizeSeen = true;
                    }
                    default -> throw new IllegalArgumentException(
                            "unknown option: " + args[index]);
                }
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("record and batch counts must be whole numbers");
        }

        if (!recordsSeen || !batchSizeSeen) {
            throw new IllegalArgumentException(
                    "usage: run --records <count> --batch-size <size>");
        }
        return new TransferRequest(records, batchSize);
    }
}
