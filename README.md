# Diorama — Forge 1.20.1 port

Unofficial port of [Diorama](https://github.com/SunkenInTime/diorama) by Dara Adedeji (MIT) from
Fabric/NeoForge 1.21.1 to **Minecraft 1.20.1, Forge 47.3.0+, Java 21**. Client-side only.

Diorama draws your world as an angled, ray-traced voxel map: a minimap and a fullscreen map
(`M`), waypoints (`B`, `U`), radar, cave slices, overlays and a saved map per world.

## Differences from the original

- **GPU baking** (an experiment, off by default upstream) is not available: it needs the GPU API of
  Minecraft 1.21.5+. Every tile is traced on the CPU, as upstream does by default.
- Mobs that only exist from 1.21 (Bogged, Breeze) are not in the radar's face list.
- Settings open from the mod list's **Config** button (Forge) or the button on the fullscreen map.
- Screens use 1.20.1's GUI art (no sprites or blurred backgrounds existed then).

## Building

GitHub Actions builds the jar on every push (`.github/workflows/build.yml`): open the run on the
**Actions** tab and download the `diorama-forge-1.20.1` artifact.

Locally: JDK 21 and `gradle build` (Gradle 8.8) or `./gradlew build`. The jar is in `build/libs/`.

The class files target Java 17 by default (`java_release` in `gradle.properties`), because the
Mixin version Forge 1.20.1 ships doesn't know newer compatibility levels. The mod runs on Java 21.

## License

MIT, as the original: see `LICENSE`.
