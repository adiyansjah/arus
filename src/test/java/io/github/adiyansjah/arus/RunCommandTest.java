package io.github.adiyansjah.arus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RunCommandTest {
    @Test
    void transfersRecordsInConfiguredBatches() {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        var command = new RunCommand(
                new Transfer(ProviderCatalog.load()),
                new PrintWriter(stdout),
                new PrintWriter(stderr));

        int exitCode = command.execute(new String[] {
            "run", "--records", "10", "--batch-size", "4"
        });

        assertEquals(0, exitCode);
        assertEquals("Transferred 10 records in 3 batches.\n", stdout.toString());
        assertEquals("", stderr.toString());
    }

    @Test
    void acceptsOptionsInEitherOrder() {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        var command = new RunCommand(
                new Transfer(ProviderCatalog.load()),
                new PrintWriter(stdout),
                new PrintWriter(stderr));

        int exitCode = command.execute(new String[] {
            "run", "--batch-size", "4", "--records", "10"
        });

        assertEquals(0, exitCode);
        assertEquals("Transferred 10 records in 3 batches.\n", stdout.toString());
        assertEquals("", stderr.toString());
    }

    @Test
    void transfersZeroRecordsWithoutBatches() {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        var command = new RunCommand(
                new Transfer(ProviderCatalog.load()),
                new PrintWriter(stdout),
                new PrintWriter(stderr));

        int exitCode = command.execute(new String[] {
            "run", "--records", "0", "--batch-size", "4"
        });

        assertEquals(0, exitCode);
        assertEquals("Transferred 0 records in 0 batches.\n", stdout.toString());
        assertEquals("", stderr.toString());
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void rejectsInvalidArguments(String[] args, String message) {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        var command = new RunCommand(
                new Transfer(new ProviderCatalog(List.of(), List.of())),
                new PrintWriter(stdout),
                new PrintWriter(stderr));

        int exitCode = command.execute(args);

        assertEquals(2, exitCode);
        assertEquals("", stdout.toString());
        assertEquals("error: " + message + "\n", stderr.toString());
    }

    @Test
    void reportsExpectedTransferFailure() {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        SourceProvider failingSource = new SourceProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Source open(long records) throws TransferException {
                throw new TransferException("source failed");
            }
        };
        SinkProvider sink = new SinkProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Sink open() {
                throw new AssertionError("sink must not open");
            }
        };
        var command = new RunCommand(
                new Transfer(new ProviderCatalog(List.of(failingSource), List.of(sink))),
                new PrintWriter(stdout),
                new PrintWriter(stderr));

        int exitCode = command.execute(new String[] {
            "run", "--records", "10", "--batch-size", "4"
        });

        assertEquals(1, exitCode);
        assertEquals("", stdout.toString());
        assertEquals("error: source failed\n", stderr.toString());
    }

    @Test
    void doesNotDisguiseProgrammingErrorsAsUsageErrors() {
        SourceProvider brokenSource = new SourceProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Source open(long records) {
                throw new IllegalArgumentException("provider bug");
            }
        };
        SinkProvider sink = new SinkProvider() {
            @Override
            public String id() {
                return "memory";
            }

            @Override
            public Sink open() {
                throw new AssertionError("sink must not open");
            }
        };
        var command = new RunCommand(
                new Transfer(new ProviderCatalog(List.of(brokenSource), List.of(sink))),
                new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()));

        assertThrows(
                IllegalArgumentException.class,
                () -> command.execute(new String[] {
                    "run", "--records", "10", "--batch-size", "4"
                }));
    }

    private static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of(
                        new String[] {},
                        "usage: run --records <count> --batch-size <size>"),
                Arguments.of(
                        new String[] {"run", "--records", "-1", "--batch-size", "4"},
                        "--records must not be negative"),
                Arguments.of(
                        new String[] {"run", "--records", "10", "--batch-size", "0"},
                        "--batch-size must be greater than zero"),
                Arguments.of(
                        new String[] {"run", "--records", "10", "--unknown", "4"},
                        "unknown option: --unknown"),
                Arguments.of(
                        new String[] {"run", "--records", "10", "--records", "4"},
                        "duplicate option: --records"),
                Arguments.of(
                        new String[] {"run", "--records=10", "--batch-size", "4", "extra"},
                        "unknown option: --records=10"),
                Arguments.of(
                        new String[] {"run", "--records", "ten", "--batch-size", "4"},
                        "record and batch counts must be whole numbers"));
    }
}
