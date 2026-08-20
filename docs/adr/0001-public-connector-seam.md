# Keep the public connector seam small and lifecycle-first

Arus 0.1.0 will publish separate source and sink provider interfaces under
`io.github.adiyansjah.arus.spi`, discovered with Java `ServiceLoader`. The seam
uses an Arus-owned descriptor, read-only connector configuration, owned source
and sink lifecycles, and an owned batch whose data representation is decided
before release by the Arrow prototype. This keeps CLI, configuration parsing,
plugin loading, logging, and engine policy internal while giving connector
authors only the concepts they must implement.

## Considered options

- Raw `Map<String, String>` configuration was smaller but would expose storage
  choices and make later configuration evolution harder.
- A shared provider parent and a read-request record added flexibility but no
  current leverage.
- A single connector provider made source-only and sink-only connectors learn
  unsupported operations.

## Consequences

Public SPI types remain source- and binary-compatible throughout `0.1.x`.
Breaking SPI changes require `0.2.0` and migration notes. Engine, CLI, and other
internal types carry no compatibility promise. The SPI is not released until a
second, independently built adapter and the Arrow ownership model prove the
seam.
