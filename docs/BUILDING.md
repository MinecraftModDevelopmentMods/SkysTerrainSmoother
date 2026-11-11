# Building from source

Use the Gradle 9.7.1 wrapper with Java 25 and Temurin 25.0.3+9 for production.
Set `STS_JAVA25_HOME` to the Java 25 installation. This branch targets
Minecraft 26.3 with ForgeGradle 7.0.34.
ForgeGradle's Mavenizer also uses Java 25 to prepare the Forge dependency.
The mod itself compiles and runs on Java 25.

Build the exact Grass Slabs and Building Pieces source commits listed in
`gradle/content-dependencies.properties` with their `assemble` tasks first. The build checks both their source commits
and main/development jar checksums.

The source lock records locally qualified Grass Slabs 1.2.1.2603001 and Building Pieces
0.3.10.2603001.

The default layout uses sibling checkouts. For another layout, pass
`-PgrassSlabsDirectory=<checkout>` and
`-PbuildingPiecesDirectory=<checkout>`.
Then run:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

Development and packaged games use the same jars. The build keeps dependency classes and assets out of the mod jar. CI builds the pinned commits.

A local-only `buildingPiecesCoreVersion` override can test another core
candidate. It requires an explicit core directory and is rejected in CI.
Eclipse's ignored Buildship preferences retain the selected checkouts. A version
override is saved only when explicitly supplied; normal generation clears any
older override. Refresh the Gradle project in Eclipse after generating the runs.

Use `genEclipseRuns eclipse` to prepare the Java 25 IDE setup. Ordinary launches
use the two pinned content jars and exclude unit tests and build-only runtime probes.
Generation prepares those jars in the development mods folder without clearing
its configuration or worlds. See [the release guide](RELEASING.md)
for publication.

## Testing with Mineralogy

Run `gradlew genEclipseRuns eclipse`, then refresh the Eclipse project. Select
`runMineralogyClient` under Java Application in Run Configurations. The matching
`runMineralogyServer` profile is available for a dedicated server. From a terminal,
use `gradlew runMineralogyClient` or `gradlew runMineralogyServer`.

These profiles use a separate `run-mineralogy` folder and load Mineralogy plus
its OreSpawn dependency.
Ordinary client and server profiles keep their existing mod sets and worlds.
Runtime probes and unit tests are excluded from these manual profiles.

The expected jars and their checksums are listed in
`gradle/mineralogy-runtime.properties`. The default paths use sibling checkouts;
for a different layout, pass `-PmineralogyRuntimeJar=<jar>` and
`-PorespawnRuntimeJar=<jar>` when generating the profiles. Missing optional jars
do not prevent an ordinary build; `prepareMineralogyDevelopmentRun` explains
what is needed before the combined profile can run. The jars are copied, not
modified. Development and packaged runs use the same jars.

If a sibling build has moved on, keep the qualified jars under
`.gradle/qualification-dependencies/` with their original filenames.
The profiles prefer those copies, but still require the locked checksums.
An explicit runtime-jar property takes precedence over either location.

New configurations use smoothing level 3. An existing saved level is kept, so
choose level 3 in Mods > Config and restart if that test folder already has a
different setting. With Building Pieces 0.3.8, Mineralogy rocks use the core's
steps at level 2 and corners at level 3, without a separate add-on.

`gradlew integrationMineralogyTest` checks fresh-world and reload behaviour in
a disposable build folder. `integrationMineralogyClientProcess` checks client
resources there. Neither task opens the manual profile's saved worlds.

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
