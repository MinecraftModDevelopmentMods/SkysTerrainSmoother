# Building from source

Use the Gradle 9.6.1 wrapper with Java 17 and Temurin 17.0.1+12 for production.
Set `STS_JAVA17_HOME` to the Java 17 installation. This branch targets
Minecraft 1.20.1 with ForgeGradle 7.0.34.
ForgeGradle's Mavenizer also uses Java 25 to prepare the Forge dependency.
The mod itself compiles and runs on Java 17.

Build the exact Grass Slabs and Building Pieces source commits listed in
`gradle/content-dependencies.properties` with their `assemble` tasks first. The build checks both their source commits
and main/development jar checksums.

The source lock records locally qualified Grass Slabs 1.2.0.120012 and Building Pieces
0.3.8.120012.

The default layout uses sibling checkouts. For another layout, pass
`-PgrassSlabsDirectory=<checkout>` and
`-PbuildingPiecesDirectory=<checkout>`.
Then run:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

Development dependencies use deobfuscated jars. Packaged games use the normal
main jars; dependency classes and assets are not bundled. CI builds the exact
pinned commits rather than moving branches.

A local-only `buildingPiecesCoreVersion` override can test another core
candidate. It requires an explicit core directory and is rejected in CI.
Eclipse's ignored Buildship preferences retain the selected checkouts. A version
override is saved only when explicitly supplied; normal generation clears any
older override. Refresh the Gradle project in Eclipse after generating the runs.

Use `genEclipseRuns eclipse` to prepare the Java 17 IDE setup. Ordinary launches
use the two pinned content jars and exclude unit tests and build-only runtime probes.
Generation prepares those jars in the development mods folder without clearing
its configuration or worlds. See [the release guide](RELEASING.md)
for publication.

## Mineralogy

The available Forge 47.4.10 Mineralogy and OreSpawn jars cannot run on this
early NeoForge loader. Ordinary development runs leave them out. Use the
Forge version of this mod for Minecraft 1.20.1 worlds that need those jars.

## Recipe-book resources

The build generates unlock advancements from `src/main/recipe-book/recipes.json`.
The runtime checks compare those entries with the registered recipes and exercise
inventory discovery, crafting-table discovery and recipe-book persistence.
All four sand recipes retain their original IDs.

Recipe unlocks have no shared advancement parent. This avoids a long recursive
visibility chain when loading completed progress. Inventory and crafting-table
discovery keep the same recipe-book behaviour.


`integrationForwardTest` upgrades a sealed 1.12.2 save and reloads the result.
It checks placed pieces, chest stacks, dropped items, an item frame and the saved
player's main, ender and nested inventories. The source archive stays unchanged.
Archive a previous disposable run before starting that task again; its logs and
world are kept for inspection.

The packaged checks also use a native 1.14.4 save in
`src/test/resources/fixtures/forward-1.14.4-family`. It retains the same building
and inventory coverage, with both sand colours, both slab halves and waterlogged
sand states saved by the preceding version. Its source artifacts and files are
checksum locked; checks always run on extracted copies.

The packaged upgrade checks also cover a saved and reloaded 1.15.2 world in
`src/test/resources/fixtures/forward-1.15.2-family`. Its fixture records the exact
preceding jars and preserves all eight sand colour, half and water states.
