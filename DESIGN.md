# Arus Transfer — High-Level Design

## 1. Overview

**Arus Transfer** is a lightweight and extensible data transfer engine for moving data between different systems and storage formats.

The main idea is simple:

```text
Source
   │
   ▼
Arus Transfer
   │
   ▼
Sink
```

Examples:

```text
PostgreSQL  →  S3 Parquet
S3 CSV      →  PostgreSQL
MySQL       →  PostgreSQL
Local JSON  →  S3
S3 Parquet  →  Local CSV
```

Arus Transfer focuses only on **executing data transfers**.

Scheduling and workflow orchestration are intentionally handled by external systems such as:

- Airflow
- Kubernetes
- Argo Workflows
- Dagster
- Temporal
- Jenkins
- Cron
- other orchestration systems

The goal is to keep Arus small, composable, and easy to integrate.

---

## 2. Design Goals

Arus should aim to provide:

- simple data movement between different systems
- extensible source and sink connectors
- extensible data format support
- streaming/batch-oriented processing
- bounded memory usage
- good throughput
- clear error reporting
- support for large datasets
- easy integration with external schedulers
- minimal coupling between the core engine and plugins

The project should prefer **simple and understandable abstractions** over overly complicated frameworks.

---

## 3. Non-Goals

Arus does not need to become a complete data orchestration platform.

The following are outside the main responsibility of the project:

```text
Scheduling
Cron management
Workflow DAGs
Job dependency management
Alert management
Cluster scheduling
Business workflow orchestration
```

External systems handle these responsibilities.

For example:

```text
Airflow
   │
   │ execute
   ▼
Arus Transfer
   │
   ▼
PostgreSQL → S3
```

Arus only needs to report whether the transfer succeeded or failed.

---

# 4. Technology Direction

The initial implementation can use:

```text
Language             Java
Java Version         Java 25 LTS
Runtime              JVM
Build System         Gradle
Plugin Discovery     Java ServiceLoader
Data Representation  Apache Arrow
Database Access      JDBC
Configuration        YAML
```

GraalVM Native Image may be explored later as an optional distribution, but the normal JVM should be treated as the primary runtime.

The design should not depend heavily on GraalVM compatibility.

---

# 5. General Architecture

A simplified architecture:

```text
                  ┌───────────────────┐
                  │   Configuration   │
                  │   transfer.yaml   │
                  └─────────┬─────────┘
                            │
                            ▼
                  ┌───────────────────┐
                  │   Arus Transfer   │
                  │      Engine       │
                  └─────────┬─────────┘
                            │
              ┌─────────────┼─────────────┐
              │                           │
              ▼                           ▼
       Source Connector             Sink Connector
              │                           │
              ▼                           ▼
           Source                       Sink
```

For file/object-based transfers, formats are added between the connector and internal data representation.

For example:

```text
S3
 │
 ▼
S3 Connector
 │
 ▼
Parquet Reader
 │
 ▼
Arrow RecordBatch
 │
 ▼
JDBC Sink
 │
 ▼
PostgreSQL
```

Another example:

```text
PostgreSQL
     │
     ▼
 JDBC Source
     │
     ▼
Arrow RecordBatch
     │
     ▼
 CSV Writer
     │
     ▼
Filesystem Connector
     │
     ▼
 customers.csv
```

---

# 6. Connector and Format Separation

A connector describes **where data comes from or where data goes**.

Examples:

```text
JDBC
S3
GCS
Azure Blob
Filesystem
Kafka
SFTP
HTTP
```

A format describes **how data is represented**.

Examples:

```text
CSV
JSON
JSONL
Parquet
Avro
ORC
Arrow IPC
```

These concepts should remain separate.

For example:

```text
S3 + CSV
S3 + JSON
S3 + Parquet
```

should use:

```text
connector-s3
format-csv
format-json
format-parquet
```

rather than creating:

```text
connector-s3-csv
connector-s3-json
connector-s3-parquet
```

This keeps the architecture manageable as more connectors and formats are added.

---

# 7. Internal Data Representation

Apache Arrow can be used as the common internal representation.

Conceptually:

```text
                   CSV
                    │
                 Parquet
                    │
PostgreSQL ─────── Arrow ─────── S3
                    │
                  JSON
                    │
                  Kafka
```

Most structured input eventually becomes:

```text
Arrow Schema
+
Arrow RecordBatch
```

The engine can then operate mostly independently of the original source.

A batch may contain something like:

```text
1,000 rows
10,000 rows
65,536 rows
```

depending on connector and configuration.

The exact batch size should be configurable rather than permanently fixed.

---

# 8. Batch-Oriented Processing

Arus should generally process data in batches rather than loading the complete dataset into memory.

Avoid:

```text
Source
   │
   ▼
Load 500 GB into memory
   │
   ▼
Sink
```

Prefer:

```text
Source
   │
   ▼
Batch
   │
   ▼
Batch
   │
   ▼
Batch
   │
   ▼
Sink
```

Conceptually:

```text
Source
   │
   ▼
┌─────────────┐
│ RecordBatch │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ RecordBatch │
└──────┬──────┘
       │
       ▼
     Sink
```

This should allow Arus to transfer datasets much larger than available memory.

---

# 9. Backpressure

The transfer pipeline should avoid unlimited buffering.

A simple bounded queue can initially be enough:

```text
Source
   │
   ▼
┌─────────────────┐
│ Bounded Queue   │
│                 │
│ Batch 01        │
│ Batch 02        │
│ Batch 03        │
└────────┬────────┘
         │
         ▼
        Sink
```

If the sink becomes slower:

```text
Source
   │
   ▼

Queue Full

   │
   ▼
Sink
```

the source can temporarily wait.

This prevents excessive memory usage.

The first implementation does not need an overly complicated reactive streaming architecture.

Standard Java concurrency primitives may be sufficient.

---

# 10. Concurrency

Java virtual threads are suitable for many connector workloads because most connectors perform blocking I/O.

Examples:

```text
Database queries
HTTP requests
S3 requests
Filesystem operations
SFTP
```

Virtual threads can therefore be used where convenient.

CPU-heavy work should still have bounded parallelism.

Examples:

```text
Parquet encoding
compression
JSON parsing
data conversion
```

A rough model could be:

```text
I/O
    Virtual Threads

CPU
    Bounded Executor

Pipeline Buffers
    Bounded Queues
```

This does not need to become rigid; connectors may have different requirements.

---

# 11. Module Structure

A possible repository structure:

```text
arus-transfer/
│
├── arus-spi/
│
├── arus-model/
│
├── arus-engine/
│
├── arus-arrow/
│
├── arus-cli/
│
├── arus-testkit/
│
├── connectors/
│   ├── connector-filesystem/
│   ├── connector-jdbc/
│   ├── connector-s3/
│   ├── connector-kafka/
│   └── ...
│
└── formats/
    ├── format-csv/
    ├── format-json/
    ├── format-parquet/
    └── ...
```

This structure can evolve as the project grows.

It should not be considered mandatory if simpler module boundaries become more practical.

---

# 12. Arus SPI

The SPI defines contracts between the core and plugins.

The SPI should remain relatively small.

Possible abstractions:

```text
ConnectorProvider
Source
Sink

FormatProvider
FormatReader
FormatWriter

ConnectorDescriptor
PluginDescriptor
```

Conceptually:

```java
public interface SourceProvider {

    ConnectorDescriptor descriptor();

    Source create(
        ConnectorConfiguration configuration
    );
}
```

and:

```java
public interface SinkProvider {

    ConnectorDescriptor descriptor();

    Sink create(
        ConnectorConfiguration configuration
    );
}
```

The SPI should avoid unnecessary dependencies on large frameworks.

Prefer mostly:

```text
interfaces
records
enums
small domain objects
exceptions
```

The API can evolve based on practical experience from the first few connectors.

---

# 13. Plugin Architecture

Connectors and formats should normally be distributed separately from the engine.

Example installation:

```text
arus/
├── bin/
│   └── arus-transfer
│
├── lib/
│   ├── arus-engine.jar
│   ├── arus-spi.jar
│   └── ...
│
└── plugins/
    ├── jdbc/
    │   ├── connector-jdbc.jar
    │   └── lib/
    │
    ├── s3/
    │   ├── connector-s3.jar
    │   └── lib/
    │
    └── parquet/
        ├── format-parquet.jar
        └── lib/
```

Keeping plugins in individual directories may help isolate their dependencies.

The initial implementation can use a simpler class-loading mechanism.

More advanced plugin isolation can be introduced if dependency conflicts become a practical problem.

---

# 14. Plugin Discovery

Java `ServiceLoader` can be used initially.

Conceptually:

```java
ServiceLoader<SourceProvider> loader =
        ServiceLoader.load(SourceProvider.class);
```

Providers are registered into something like:

```text
ConnectorRegistry
```

Example:

```text
ConnectorRegistry

jdbc
s3
filesystem
kafka
```

And:

```text
FormatRegistry

csv
json
parquet
```

When configuration contains:

```yaml
source:
  connector: jdbc
```

the engine resolves:

```text
jdbc
 ↓
ConnectorRegistry
 ↓
JdbcSourceProvider
```

---

# 15. Connector Bundle

A connector bundle may contain:

```text
connector-s3/
├── plugin.yaml
├── connector-s3.jar
└── lib/
    ├── dependency-a.jar
    ├── dependency-b.jar
    └── ...
```

Possible metadata:

```yaml
id: s3
name: Amazon S3
version: 1.0.0

arus:
  api-version: 1

capabilities:
  source: true
  sink: true
```

The exact metadata format can remain flexible initially.

`ServiceLoader` may still be responsible for locating Java implementations.

The descriptor mainly helps with:

```text
version checking
plugin listing
documentation
compatibility
diagnostics
```

---

# 16. Connector Responsibilities

A connector owns system-specific behavior.

For example, an S3 connector may handle:

```text
AWS authentication
bucket access
object listing
object reading
object writing
multipart upload
S3 errors
```

It should not normally handle:

```text
Parquet serialization
CSV parsing
global scheduling
generic pipeline execution
```

A JDBC connector may handle:

```text
connections
queries
ResultSet reading
batch inserts
transactions
JDBC type conversion
database-specific behavior
```

---

# 17. Format Responsibilities

Formats handle serialization and deserialization.

For example:

```text
format-csv

CSV reader
CSV writer
CSV options
schema inference
```

```text
format-parquet

Parquet reader
Parquet writer
compression configuration
Arrow conversion
```

The engine should be able to combine formats and connectors dynamically.

---

# 18. Transfer Definition

Transfers can be described using YAML.

Example:

```yaml
version: 1

source:
  connector: jdbc

  properties:
    url: jdbc:postgresql://localhost:5432/app
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  dataset:
    table: public.customers

sink:
  connector: s3

  properties:
    bucket: warehouse
    region: ap-southeast-1

  dataset:
    path: customers/

  format:
    type: parquet

    options:
      compression: zstd

transfer:
  batch-size: 65536
  parallelism: 4
```

The exact configuration schema should remain somewhat flexible during early development.

It is better to learn from actual connector implementations before making the configuration contract too rigid.

---

# 19. Configuration Variables

Environment variables can be supported:

```yaml
username: ${DB_USER}
password: ${DB_PASSWORD}
```

Parameterized transfers may also be useful:

```yaml
source:
  dataset:
    query: |
      SELECT *
      FROM events
      WHERE event_date = '${var.date}'
```

Then:

```bash
arus-transfer run events.yaml \
    --var date=2026-08-19
```

This is useful when Arus is called by an external scheduler.

The scheduler determines the execution date.

Arus only receives it as an input parameter.

---

# 20. CLI

The initial CLI can remain small.

Possible commands:

```text
arus-transfer run
arus-transfer validate
arus-transfer plugins
arus-transfer inspect
arus-transfer version
```

The essential commands for V1 are probably only:

```bash
arus-transfer plugins list

arus-transfer validate transfer.yaml

arus-transfer run transfer.yaml
```

Other commands can be added when there is a clear need.

---

# 21. Example User Flow

Install Arus:

```text
/opt/arus
```

Install plugins:

```text
plugins/
├── jdbc/
├── s3/
└── parquet/
```

Check plugins:

```bash
arus-transfer plugins list
```

Validate configuration:

```bash
arus-transfer validate customer-export.yaml
```

Run:

```bash
arus-transfer run customer-export.yaml
```

Arus performs:

```text
PostgreSQL
    │
    ▼
JDBC Source
    │
    ▼
Arrow RecordBatch
    │
    ▼
Parquet Writer
    │
    ▼
S3 Sink
```

Then exits.

---

# 22. External Scheduling

Arus should not schedule itself.

For example:

```text
             Airflow
                │
                ▼
       arus-transfer run
                │
                ▼
         Transfer Engine
                │
                ▼
       PostgreSQL → S3
```

or:

```text
Kubernetes CronJob
        │
        ▼
Arus Transfer Container
        │
        ▼
transfer
        │
        ▼
exit
```

This keeps Arus independent of orchestration systems.

---

# 23. Exit Status

The CLI should return meaningful process exit codes.

At minimum:

```text
0   success
non-zero   failure
```

More specific codes may be introduced later if useful.

Examples of possible failure categories:

```text
configuration failure
plugin failure
source failure
sink failure
data conversion failure
cancelled
retry exhausted
```

The exact numbering does not need to be decided early.

---

# 24. Machine-Readable Output

External tools may need structured results.

Example:

```bash
arus-transfer run transfer.yaml \
    --output json
```

Possible response:

```json
{
  "status": "SUCCESS",
  "recordsRead": 12543219,
  "recordsWritten": 12543219,
  "bytesRead": 4201381273,
  "bytesWritten": 1102834712,
  "durationMs": 24122
}
```

Logs can still be written separately for humans and debugging.

---

# 25. Error Handling

The engine should distinguish useful failure categories rather than exposing arbitrary low-level exceptions everywhere.

Possible categories:

```text
AUTHENTICATION
CONNECTION
TIMEOUT
RATE_LIMIT
INVALID_CONFIGURATION
UNSUPPORTED_SCHEMA
DATA_ERROR
IO
UNKNOWN
```

Connectors translate provider-specific errors into a smaller set understood by the engine.

For example:

```text
AWS SDK exception
      │
      ▼
S3 connector
      │
      ▼
ConnectorFailure.CONNECTION
```

This helps the engine decide whether an operation may be retried.

---

# 26. Retry Responsibility

Arus may retry short-lived operational failures.

For example:

```text
S3 request
   │
timeout
   │
retry
   │
success
```

But whole-transfer retries should normally remain an external orchestration concern.

Conceptually:

```text
Arus
────────────────
network retry
API retry
temporary failure handling


Airflow / Kubernetes / etc.
───────────────────────────
rerun complete transfer
schedule retry
workflow retry
```

The boundary does not have to be completely rigid; connectors may need different retry behavior.

---

# 27. Checkpoints and Resume

Checkpointing may eventually be useful for large transfers.

For example:

```text
Transfer

0% ─────────── 63%

failure
```

A future execution could resume:

```text
63% ─────────── 100%
```

Checkpointing belongs closer to Arus than to the scheduler because Arus understands connector-specific progress.

Examples:

```text
database key range
Kafka offset
file partition
S3 object
partition directory
```

However, checkpoint/resume does not need to block the earliest V1 if it significantly complicates development.

It can be introduced incrementally once transfer semantics are better understood.

---

# 28. Transfer Identity

It may eventually be useful to distinguish:

```text
transfer-id
```

from:

```text
execution-id
```

Example:

```text
transfer-id:
customers-postgres-to-s3

execution-id:
airflow-run-2026-08-19
```

The external scheduler can provide the execution ID.

Arus may use it for:

```text
logging
metrics
checkpointing
tracing
diagnostics
```

Again, this can remain optional in early versions.

---

# 29. Observability

Arus should expose basic operational information.

Useful metrics include:

```text
records read
records written
bytes read
bytes written
transfer duration
throughput
failed records
retry count
queue utilization
```

Logs should identify:

```text
transfer
connector
source
sink
execution
```

without exposing secrets.

More advanced observability such as OpenTelemetry can be added later without making it a core dependency of the SPI.

---

# 30. Secrets

Configuration should not encourage storing plain-text credentials.

Prefer:

```yaml
password: ${DB_PASSWORD}
```

Initial secret handling can simply rely on environment variables.

Later integrations may support:

```text
AWS Secrets Manager
Vault
Kubernetes Secrets
cloud secret managers
```

but these do not need to be part of the first implementation.

---

# 31. Testing Strategy

Testing should happen at several levels.

## Unit tests

Useful for:

```text
schema mapping
type mapping
configuration validation
retry decisions
partition planning
```

## Connector integration tests

Examples:

```text
PostgreSQL container
MinIO/S3-compatible container
local filesystem
Kafka container
```

## Format tests

Verify:

```text
CSV → Arrow
Arrow → CSV

Parquet → Arrow
Arrow → Parquet
```

## End-to-end tests

Examples:

```text
PostgreSQL → S3 Parquet
S3 CSV → PostgreSQL
PostgreSQL → PostgreSQL
Local JSONL → S3
```

---

# 32. Connector Test Kit

A reusable test kit may eventually help enforce basic connector behavior.

Something like:

```text
arus-connector-testkit
```

could validate:

```text
connector initialization
configuration validation
resource closing
schema reading
batch reading
error translation
cancellation
```

Third-party connector developers could run the same tests.

This would be useful once the SPI begins stabilizing.

---

# 33. Java Code Style

The implementation should use modern Java without trying to be overly clever.

Good defaults:

```text
records for data objects
final classes where practical
constructor injection
composition over inheritance
interfaces for actual boundaries
try-with-resources
small domain-oriented packages
typed configuration
```

Avoid unnecessary patterns such as:

```text
SomethingService
SomethingServiceImpl

AbstractBaseSomethingManager

GlobalSomethingSingleton

Utils
CommonUtils
Helper
```

unless they represent something meaningful.

---

# 34. Framework Usage

The core engine should preferably remain plain Java.

For example:

```text
arus-engine
arus-spi
arus-model

    no Spring requirement
```

If a future HTTP service is introduced:

```text
arus-server
```

it may use:

```text
Spring Boot
Micronaut
Quarkus
```

without forcing those frameworks into connector implementations.

---

# 35. Initial V1 Scope

A reasonable first version could support:

## Connectors

```text
Filesystem
JDBC
S3
```

## Formats

```text
CSV
JSONL
Parquet
```

## Execution

```text
batch transfer
basic backpressure
basic retry
environment variables
CLI
plugin discovery
structured result
```

This allows useful transfers immediately:

```text
CSV → PostgreSQL
PostgreSQL → CSV

PostgreSQL → Parquet
Parquet → PostgreSQL

PostgreSQL → S3 Parquet
S3 CSV → PostgreSQL

MySQL → PostgreSQL

Local JSONL → S3
```

---

# 36. Possible Development Phases

## Phase 1 — Core

Build:

```text
arus-spi
arus-engine
plugin registry
transfer configuration
CLI
```

Use simple fake/in-memory connectors initially.

---

## Phase 2 — Data Representation

Introduce:

```text
Apache Arrow
Schema
RecordBatch
batch processing
```

Establish basic source-to-sink flow.

---

## Phase 3 — First Real Connectors

Implement:

```text
Filesystem
JDBC
```

This validates the connector abstraction.

---

## Phase 4 — Formats

Implement:

```text
CSV
JSONL
Parquet
```

This validates the connector/format separation.

---

## Phase 5 — Object Storage

Implement:

```text
S3
```

Then validate combinations such as:

```text
PostgreSQL → S3 Parquet
S3 CSV → PostgreSQL
```

---

## Phase 6 — Reliability

Add more mature support for:

```text
retry
cancellation
progress reporting
metrics
checkpointing where useful
```

---

## Phase 7 — Plugin Ecosystem

Improve:

```text
plugin metadata
dependency isolation
connector test kit
compatibility checking
documentation
```

---

# 37. Architectural Principles

The project can follow a few general principles without treating them as rigid rules.

### Keep the engine independent

```text
arus-engine
```

should not know directly about:

```text
PostgreSQL
AWS
Kafka
Parquet
```

Plugins provide those capabilities.

### Prefer composition

Build behavior from small components rather than deep class inheritance.

### Keep the SPI small

The less exposed by the SPI, the easier it will be to evolve Arus.

### Optimize after measurement

Start with simple Java implementations.

Do not introduce native Rust components, complicated reactive systems, or aggressive low-level optimizations until profiling shows a reason.

### Allow architecture to evolve

The first JDBC, filesystem, S3, CSV, and Parquet implementations will probably reveal weaknesses in the original abstraction.

It is acceptable to adjust the design during early versions rather than freezing an overly strict API too early.

---

# 38. Overall Mental Model

Arus Transfer should remain understandable as:

```text
                 External Scheduler
                        │
                        ▼
                transfer.yaml
                        │
                        ▼
                ┌─────────────┐
                │    Arus     │
                │  Transfer   │
                └──────┬──────┘
                       │
             resolve plugins
                       │
          ┌────────────┴────────────┐
          │                         │
          ▼                         ▼
       Source                      Sink
          │                         ▲
          │                         │
          └──── Arrow batches ──────┘
```

When formats are involved:

```text
Source System
      │
      ▼
Connector
      │
      ▼
Format Reader
      │
      ▼
Arrow
      │
      ▼
Format Writer
      │
      ▼
Connector
      │
      ▼
Sink System
```

Some transfers skip format layers entirely:

```text
PostgreSQL
     │
     ▼
JDBC Source
     │
     ▼
Arrow
     │
     ▼
JDBC Sink
     │
     ▼
MySQL
```

---

# 39. Summary

The initial direction for Arus Transfer is:

```text
Java 25 LTS
JVM-first

Plain Java core

External scheduling

Plugin-based connectors

Plugin-based formats

Apache Arrow internal representation

Batch-oriented streaming

Virtual threads where useful

Bounded memory/backpressure

JDBC as initial database foundation

CLI-first execution model

Separate plugin bundles

Simple YAML transfer definitions
```

The design should remain intentionally flexible during early development.

The purpose of the architecture is not to define every implementation detail upfront. It provides a direction:

> **Arus Transfer should be a small, reliable and extensible engine that knows how to move data, while leaving scheduling and orchestration to other systems.**

As practical experience grows from implementing real connectors and formats, the APIs and module boundaries can evolve while keeping this core principle intact.
