package io.github.adiyansjah.arus;

import java.io.PrintWriter;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        var command = new RunCommand(
                new Transfer(ProviderCatalog.load()),
                new PrintWriter(System.out, true),
                new PrintWriter(System.err, true));
        System.exit(command.execute(args));
    }
}
