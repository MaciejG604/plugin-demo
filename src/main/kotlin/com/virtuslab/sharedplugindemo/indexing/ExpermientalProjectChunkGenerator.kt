// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.virtuslab.sharedplugindemo.indexing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.indexing.shared.generator.DumpSharedIndexCommand
import com.intellij.indexing.shared.generator.IndexChunk
import com.intellij.indexing.shared.generator.IndexesExporter
import com.intellij.indexing.shared.generator.MainGenerateArgs
import com.intellij.indexing.shared.metadata.SharedIndexMetadataInfo
import com.intellij.indexing.shared.ultimate.project.ProjectIndexChunk
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.util.ArgsParser
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.ModuleIndexableFilesIterator
import com.intellij.util.progress.sleepCancellable
import com.intellij.warmup.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import kotlin.io.path.name

open class DumpProjectArgs(parser: ArgsParser) : MainGenerateArgs(parser), OpenProjectArgs by OpenProjectArgsImpl(parser) {
  private val vcsCommitId by parser.arg("commit", "source Version Control revision").stringOrNull()
  private val projectId by parser.arg("project-id", "project identifier").stringOrNull()

  fun indexesExporterRequest(chunk: ExperimentalIndexChunk) = createIndexesExporterRequest(
    chunk = chunk,
    addon = SharedIndexMetadataInfo(projectId = projectId, projectVcsCommitId = vcsCommitId)
  )
}

class ExperimentalIndexChunk(projectIndexChunk: ProjectIndexChunk, filterFun: (IndexableFilesIterator) -> (Boolean)): IndexChunk {
  override val kind: String = projectIndexChunk.kind
  override val name: String = projectIndexChunk.name
  override val rootIterators: List<IndexableFilesIterator> = projectIndexChunk.rootIterators.filter(filterFun)
}

private class ExperimentalProjectChunkGenerator : DumpSharedIndexCommand<DumpProjectArgs> {
  override val commandName: String = "experimental"
  override val commandDescription: String = "Generates shared index for a given project"

  override fun parseArgs(parser: ArgsParser) = DumpProjectArgs(parser)

  override suspend fun executeCommand(args: DumpProjectArgs, indicator: ProgressIndicator) {

    val jsonPathId = "plugin-demo-source-list-path"
    val projectIndexJsonLocation: String = System.getProperty(jsonPathId) ?: System.getenv(jsonPathId)
    val node = ObjectMapper().readTree(Paths.get(projectIndexJsonLocation).toFile())
    val moduleList = node.get("module-list") as ArrayNode
    val modules = moduleList.map { Paths.get(it.asText()) }
    val moduleNames = modules.map { it.name }

    // IJ code
    val project = importOrOpenProjectAsync(args)
    ConsoleLog.info("Collecting files to index...")
    val projectChunk = runAndCatchNotNull("Collecting Project Sources") {
      ProjectIndexChunk(project)
    }

    for (module in moduleNames) {
      val filterFun = { ifi: IndexableFilesIterator ->
        if (ifi is ModuleIndexableFilesIterator)
          module == (ifi as ModuleIndexableFilesIterator).origin.module.name
        else false
      }
      val chunk = ExperimentalIndexChunk(projectChunk, filterFun)

      ConsoleLog.info("Indexing $module...")
      ConsoleLog.info("Root size: ${chunk.rootIterators.size}")
      ConsoleLog.info("Root example: ${chunk.rootIterators.first()}")

      withContext(Dispatchers.IO) {
        val response = IndexesExporter.exportIndexesChunk(
          project = project,
          indicator = indicator,
          request = args.indexesExporterRequest(chunk)
        )

        ConsoleLog.info("##################################################")
        ConsoleLog.info("Index Res:")
        ConsoleLog.info("ijx: ${response.files.indexPath}")
        ConsoleLog.info("sha256: ${response.files.sha256Path}")
        ConsoleLog.info("metadata: ${response.files.metadataPath}")
        ConsoleLog.info("##################################################")
        sleepCancellable(1000)
      }
    }

    val filterFun = { ifi: IndexableFilesIterator ->
      if (ifi is LibraryIndexableFilesIteratorImpl)
        (ifi as LibraryIndexableFilesIteratorImpl).getDebugName().contains("sbt: sbt-1.10.1")
      else false
    }
    val chunk = ExperimentalIndexChunk(projectChunk, filterFun)

    ConsoleLog.info("Indexing sbt...")
    ConsoleLog.info("Root size: ${chunk.rootIterators.size}")
    ConsoleLog.info("Root example: ${chunk.rootIterators.first()}")

    withContext(Dispatchers.IO) {
      val response = IndexesExporter.exportIndexesChunk(
        project = project,
        indicator = indicator,
        request = args.indexesExporterRequest(chunk)
      )

      ConsoleLog.info("##################################################")
      ConsoleLog.info("Index Res:")
      ConsoleLog.info("ijx: ${response.files.indexPath}")
      ConsoleLog.info("sha256: ${response.files.sha256Path}")
      ConsoleLog.info("metadata: ${response.files.metadataPath}")
      ConsoleLog.info("##################################################")
      sleepCancellable(1000)
    }
  }
}
