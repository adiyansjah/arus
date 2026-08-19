---
name: java-25-implementor
description: Use when writing or reviewing Java code targeting Java 25 and choosing between stable JDK capabilities, existing project code, dependencies, or custom code. Java 25-specific features are optional; this skill is framework-neutral.
---

# Java 25 Implementor

Choose the smallest clear and compatible solution. Before editing, inspect the
relevant code, callers, tests, toolchain, and preview-feature settings.

## Capability Choice

- Follow existing project conventions when they preserve the required behavior.
- Prefer suitable stable Java SE APIs over custom utilities or new dependencies.
- Use existing dependencies when they fit better than the JDK API.
- Add a dependency only when the JDK and existing dependencies are insufficient.
- Use Java 25 capabilities for semantic benefit, not novelty.
- Verify uncertain API behavior or feature status against official Java SE 25
  or OpenJDK documentation.

## Language and Data

- Use a `record` for a transparent data carrier whose component references are
  final; defensively copy mutable components when deeper immutability is needed.
- Use a class when identity, lifecycle, encapsulated state, or inheritance matters.
- Use a sealed hierarchy only for intentionally closed domain variants.
- Do not seal public extension points or plugin SPIs.
- Use pattern matching and switch expressions when they clarify exhaustive
  handling; an ordinary conditional or loop is often clearer.
- Treat collection factory and `copyOf` results as unmodifiable shallow
  snapshots, not guarantees that contained objects are immutable.
- Do not introduce preview or incubator features unless explicitly approved and
  supported by the build.

## Concurrency

- Consider virtual threads for many concurrent tasks that spend most of their
  time waiting or performing blocking I/O.
- Do not pool virtual threads or use them to increase CPU parallelism.
- A virtual-thread-per-task executor is not backpressure: bound admission,
  queued work, connections, remote requests, and other scarce resources
  separately.
- Use a platform-thread executor sized for CPU parallelism, with explicit queue
  and rejection or backpressure behavior.
- Give network and blocking operations explicit timeout, cancellation, and
  partial-failure behavior.
- Preserve interruption; never convert interruption or cancellation into success.

## Resource Ownership

- Use try-with-resources for resources owned by the current scope.
- Do not close borrowed resources.
- Make ownership transfer and lifecycle explicit.
- A lazy result backed by an open resource must expose or retain a clear owner
  that can close the complete resource chain.
- Preserve the original cause when translating failures.

## Verification

Use the repository's existing formatter, compiler, tests, and quality gate.
Add the smallest behavior-focused test needed for the change. Report skipped
checks, new dependencies, preview use, and unresolved risks.
