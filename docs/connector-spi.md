# Connector SPI contract for 0.1.0

This document defines the connector seam targeted by Arus 0.1.0. It is a
contract preview: production SPI types are implemented only after the Arrow
ownership prototype decides the batch payload representation.

## Public surface

Only types in `io.github.adiyansjah.arus.spi` are public to connector authors.
The representative interface is:

```java
public record ConnectorDescriptor(String id, String version) {}

public interface ConnectorConfiguration {
    Optional<String> value(String key);
}

public interface SourceProvider {
    ConnectorDescriptor descriptor();

    Source openSource(ConnectorConfiguration configuration)
            throws ConnectorConfigurationException,
                    ConnectorExecutionException;
}

public interface SinkProvider {
    ConnectorDescriptor descriptor();

    Sink openSink(ConnectorConfiguration configuration)
            throws ConnectorConfigurationException,
                    ConnectorExecutionException;
}

public interface Source extends AutoCloseable {
    Optional<Batch> read(int maximumBatchSize)
            throws ConnectorExecutionException;

    @Override
    void close() throws ConnectorExecutionException;
}

public interface Sink extends AutoCloseable {
    void write(Batch batch) throws ConnectorExecutionException;

    @Override
    void close() throws ConnectorExecutionException;
}

public interface Batch extends AutoCloseable {
    int size();

    @Override
    void close() throws ConnectorExecutionException;
}
```

`ConnectorConfigurationException` reports missing or invalid connector
configuration. `ConnectorExecutionException` reports failures while opening,
reading, writing, or closing connector resources. Both preserve their original
cause. Unexpected programming exceptions are not translated into either type.

The final `Batch` interface will add the minimum data access selected by the
Arrow prototype. Its ownership and bounded-size rules below are already fixed.

## Provider rules

- A provider implementation is public and has a public no-argument constructor
  so `ServiceLoader` can instantiate it.
- One class may implement both provider interfaces, using `openSource` and
  `openSink` as distinct methods.
- A descriptor is deterministic and side-effect free.
- An ID matches `[a-z][a-z0-9-]*` and is stable across connector patch releases.
- An ID is unique per provider role in one Arus installation.
- The version is the connector implementation version, not the Arus SPI
  version.
- Public SPI packages are null-marked; nullable values are explicit.

Providers are registered independently by role:

```text
META-INF/services/io.github.adiyansjah.arus.spi.SourceProvider
META-INF/services/io.github.adiyansjah.arus.spi.SinkProvider
```

Each file contains one fully qualified provider implementation name per line.

## Configuration rules

- Configuration is immutable from the provider's perspective.
- Values are strings; each provider parses and validates its own values.
- A missing value is represented by `Optional.empty()`, never `null`.
- Error messages name invalid keys but never include secret values.
- CLI flags, YAML nodes, environment lookup, and secret resolution stay outside
  the SPI.

## Lifecycle and ownership

The engine owns this sequence:

```text
resolve and validate providers
open source
open sink
repeat:
    source reads at most the configured maximum
    source transfers batch ownership to engine
    sink borrows batch during write
    engine closes batch
close sink
close source
```

The following invariants apply:

- `maximumBatchSize` is greater than zero.
- `Source.read` returns `Optional.empty()` only for end-of-stream.
- A returned batch has `1 <= size <= maximumBatchSize`.
- The engine closes each returned batch exactly once, including after a failed
  sink write.
- A sink neither retains nor closes a borrowed batch.
- The engine closes sink before source and closes source when sink opening
  fails.
- Source, sink, and batch are synchronous and not thread-safe.
- The engine calls no lifecycle method after closing its owner.
- A primary failure remains primary; later close failures are suppressed.

## Compatibility

- Public SPI types remain source- and binary-compatible throughout `0.1.x`.
- Breaking public changes require `0.2.0` and migration notes.
- Engine, CLI, provider catalog, configuration parsers, plugin class loaders,
  and logging are internal and may change without notice.
- The public seam is proven before release by the memory connector and a second
  connector built outside the main Gradle project.

## Deferred from 0.1.0

- YAML-specific configuration types
- format plugins
- plugin dependency isolation and hot reload
- concurrency, retry, cancellation, metrics, and checkpoints
- logging and dependency-injection frameworks
