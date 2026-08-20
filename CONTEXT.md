# Arus Transfer

Arus Transfer moves bounded groups of records from one external system to
another. It owns transfer execution while installed connectors own interaction
with those systems.

## Language

**Transfer**:
One execution that moves records from one source to one sink.
_Avoid_: Job, pipeline, workflow

**Connector**:
An installable integration with an external system that can provide a source,
a sink, or both.
_Avoid_: Plugin, provider

**Plugin**:
The deployed artifact that contains one or more connector providers.
_Avoid_: Connector

**Provider**:
The discovered entry point that describes a connector and opens a source or
sink from connector configuration.
_Avoid_: Connector, plugin

**Source**:
An owned reader that transfers batches to Arus until it reaches end-of-stream.
_Avoid_: Producer, input

**Sink**:
An owned writer that borrows each batch while storing its records.
_Avoid_: Consumer, output

**Batch**:
A finite, owned group of records transferred from a source to a sink as one
lifecycle unit.
_Avoid_: Chunk, page

**Connector configuration**:
The immutable named values a provider uses to open a source or sink.
_Avoid_: CLI options, YAML document
