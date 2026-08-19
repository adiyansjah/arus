# Arus Transfer

Arus Transfer is a small data-transfer engine. See [DESIGN.md](DESIGN.md) for
the accepted direction.

The repository currently contains only the development bootstrap. The first
executable transfer slice will be implemented separately.

## Development

Requirements:

- [asdf](https://asdf-vm.com/)
- Git

Install the pinned Java 25 runtime and run the complete local check:

```bash
asdf install
java -version
./gradlew check
```

No separate Gradle installation is required. CI runs the same check on Ubuntu.
