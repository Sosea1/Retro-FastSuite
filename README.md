# Retro FastSuite

Retro FastSuite speeds up crafting recipe lookup in large Forge 1.12.2 modpacks. It builds an index of the items used by recipes, so Minecraft does not have to scan the entire recipe registry after every crafting-grid change.

This is an unofficial 1.12.2 adaptation of [Shadows-of-Fire/FastSuite](https://github.com/Shadows-of-Fire/FastSuite). It keeps Forge recipe order and always uses `IRecipe.matches()` for the final result. Recipes that cannot be indexed safely stay on the normal ordered fallback path.

## Compatibility

- Minecraft 1.12.2
- Forge or Cleanroom
- MixinBooter is required
- Compatible with Universal Tweaks Crafting Cache
- Compatible with RetroPolymorph-style first-match and all-match lookups

FastSuite complements a crafting cache: the cache handles repeated identical inputs, while FastSuite makes cache misses and changed crafting grids cheaper.

## Benchmark

Measured in a modpack with 24,325 registered recipes. Each cell used 200 warmup runs and 1,000 measured lookups.

| Crafting input | All matches, Retro / vanilla | Speedup | First match, Retro / vanilla | Speedup |
|---|---:|---:|---:|---:|
| Acacia planks | 520.19 / 7,743.59 µs | 14.89x | 11.59 / 48.64 µs | 4.20x |
| Sticks | 567.16 / 8,343.35 µs | 14.71x | 2.43 / 5.04 µs | 2.07x |
| Crafting table | 431.73 / 7,165.15 µs | 16.60x | 4.84 / 21.30 µs | 4.40x |
| Black shulker box | 467.59 / 7,798.17 µs | 16.68x | 1.45 / 1.24 µs | 0.86x |
| No matching recipe | 711.77 / 11,476.92 µs | 16.12x | 533.79 / 12,280.99 µs | 23.01x |

The numbers compare FastSuite with Forge's normal linear recipe scan. One small first-match case was slower; the main benefit is visible on all-match queries and failed lookups in large registries.

The same test run checked 23,966 positive recipes, 58,657 negative cases, 58,271 alternative stacks, 100,000 fuzz cases and 64 ordered-oracle cases with zero mismatches.

## Commands

```text
/fastsuite stats
/fastsuite verify
/fastsuite mode config|indexed|vanilla
/fastsuite rebuild
```

`verify`, `mode` and `rebuild` require permission level 2. Normal gameplay does not need these commands.

## Building

```text
gradlew.bat test reobfJar
```

The release jar is written to `build/libs`. Development-only benchmarks and diagnostics are built separately with `reobfDevJar`.

## License

Retro FastSuite is available under the MIT License. The original FastSuite copyright notice is retained in [LICENSE](LICENSE).
