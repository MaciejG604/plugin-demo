Example repo for experimenting with different ways to generate shared indexes for IntelliJ IDEA.

There are two classes added that can generate index:
- `PersistentProjectIndexesGenerator` run with `persistent-project` command, copied from `bazel-rules` example
will read json file from path passed in `plugin-demo-source-list-path` property to the idea launcher,
extract fields `module-list` and `root-list` and get all files from those that should be indexed,
request indexes creation from IndexingService (custom class callingIJ API `IndexesExporter`)
- `ExperimentalProjectChunkGenerator` run with `experimental` command,
uses the same process as IJ's `project` command, to get all sources contributing to the project index,
put a breakpoint after line 60 and inspect `projectChunk.rootIterators` it contains all the contributors like modules, jars, etc.


# Testing the plugin
- run sbt task `buildPlugin`
- open your IJ and install the plugin from local sources
- modify `<IJ package>/Contents/bin/idea.vmoptions`, add lines:
`-Dlocal.project.shared.index.json.path=..../indexes.json` - this is for loading the indexes into IJ (IJ's mechanism, not custom)
`-Dplugin-demo-source-list-path=..../modules.json` - for passing modules for index generation

Example of what those files look like:
`indexes.json`
```
{ "shared-indexes": [
 "~/indexes/shared-index-project-IndexId-33baa81e07f3be4c.ijx",
 "~/indexes/shared-index-project-IndexId-ab90404aecfa23e5.ijx",
 "~/indexes/shared-index-project-IndexId-377df55a44aa65f3.ijx",
 "~/indexes/shared-index-project-IndexId-4765e914cd6df5b8.ijx",
 "~/indexes/shared-index-project-IndexId-dfadfd10c85db27c.ijx",
 "~/indexes/shared-index-project-IndexId-865b8711e86aebab.ijx",
 "~/indexes/shared-index-jdk-22.0.2-14b105b52a5ccf19.ijx",
 "~/indexes/shared-index-project-full_project-8dea1a18d7c6c5f6.ijx",
 "~/indexes/shared-index-project-IndexId-a0ce6a500c3b1490.ijx",
 "~/indexes/shared-index-project-IndexId-d999537cfd5a5d05.ijx",
 "~/indexes/shared-index-project-IndexId-47d2fdffa92b7776.ijx",
 "~/indexes/shared-index-project-IndexId-973fb665e5cce1ec.ijx",
 "~/indexes/shared-index-jars-a4f84562-95af8b59143e8de4.ijx",
 "~/indexes/shared-index-project-IndexId-6ef5cb5ea4013759.ijx",
 "~/indexes/shared-index-project-IndexId-e480948298301629.ijx",
 "~/indexes/shared-index-project-IndexId-10ed526360a7c73d.ijx",
 "~/indexes/shared-index-project-IndexId-d6ca5517324a2559.ijx",
 "~/indexes/shared-index-project-IndexId-660f38263c623dde.ijx",
 "~/indexes/shared-index-project-IndexId-6c818ba081ac30a2.ijx"
  ]
}
```

`modules.json`
```
{
  "module-list": [],
  "temp": [
    "~/ij-indexing-tools/examples/full-project/subproject_1",
    "~/ij-indexing-tools/examples/full-project/subproject_2",
    "~/ij-indexing-tools/examples/full-project/subproject_3",
    "~/ij-indexing-tools/examples/full-project/subproject_4",
    "~/ij-indexing-tools/examples/full-project/subproject_5",
    "~/ij-indexing-tools/examples/full-project/subproject_6",
    "~/ij-indexing-tools/examples/full-project/subproject_7",
    "~/ij-indexing-tools/examples/full-project/subproject_8",
    "~/ij-indexing-tools/examples/full-project/subproject_9",
    "~/ij-indexing-tools/examples/full-project/subproject_10",
    "~/ij-indexing-tools/examples/full-project/subproject_11",
    "~/ij-indexing-tools/examples/full-project/subproject_12",
    "~/ij-indexing-tools/examples/full-project/subproject_13",
    "~/ij-indexing-tools/examples/full-project/subproject_14",
    "~/ij-indexing-tools/examples/full-project/subproject_15"
  ],
  "root-list": [
    "~/ij-indexing-tools/examples/full-project/project",
    "~/ij-indexing-tools/examples/full-project/intellij.yaml",
    "~/ij-indexing-tools/examples/full-project/build.sbt",
    "~/ij-indexing-tools/examples/full-project/.bsp",
    "~/ij-indexing-tools/examples/full-project/.idea"
  ]
}
```

# Debugging the plugin and IJ itself:
- modify `<IJ package>/Contents/bin/idea.vmoptions`, add line:
`-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:8000,suspend=y,onuncaught=n`
- In IJ create debug configuration for remote JVM, use `Listen to remote JVM` with host localhost and port 8000 
- Run the debug configuration and launch IJ with some command like `dump-shared-index` or just plain run the IDE
- After debugging remove the added line from `<IJ package>/Contents/bin/idea.vmoptions`, so your IJ can run normally