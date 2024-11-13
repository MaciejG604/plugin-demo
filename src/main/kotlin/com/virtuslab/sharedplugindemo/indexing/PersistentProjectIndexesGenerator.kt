package com.virtuslab.sharedplugindemo.indexing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.indexing.shared.generator.DumpSharedIndexCommand
import com.intellij.indexing.shared.generator.IndexChunk
import com.intellij.indexing.shared.ultimate.persistent.rpc.IndexRequest
import com.intellij.indexing.shared.ultimate.persistent.rpc.StartupRequest
import com.intellij.indexing.shared.ultimate.project.ProjectSharedIndexes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.text.StringHash
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.util.ArgsParser
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.progress.sleepCancellable
import com.intellij.warmup.util.ConsoleLog
import com.virtuslab.indexing.IndexingService
import io.grpc.Status
import io.grpc.StatusException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.streams.asSequence

class PersistentProjectArgs(parser: ArgsParser) {
  val port by parser.arg(
    "port",
    "port to listen on"
  ).int { 9000 }

  val domainSocket by parser.arg(
    "socket",
    "unix domain socket to listen on",
  ).stringOrNull()

  val projectDir by parser.arg(
    "project-dir",
    "directory of project to index"
  ).stringOrNull()

  val sources by parser.arg(
    "sources",
    "sources to index"
  ).strings()
}

class ProjectPartialIndexChunk(private val request: IndexRequest) : IndexChunk {
  override val name = request.indexId
  override val kind = ProjectSharedIndexes.KIND

  class IndexRequestOrigin(val request: IndexRequest): IndexableSetOrigin
  class ByRequestFileIterator(private val request: IndexRequest): IndexableFilesIterator {
    val sources: List<VirtualFile> = request.sourcesList
      .map { Paths.get(it).toString() }
      .map { LocalFileSystem.getInstance().findFileByPath(it) }
      .map { it ?: throw StatusException(Status.NOT_FOUND) }

    override fun getDebugName(): String = request.indexDebugName

    override fun getIndexingProgressText() = "Indexing $debugName"

    override fun getRootsScanningProgressText() =  "Scanning $debugName"

    override fun getOrigin(): IndexableSetOrigin =  IndexRequestOrigin(request)

    override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
      sources.forEach(fileIterator::processFile)
      return true
    }

    override fun getRootUrls(project: Project) = setOf<String>()
  }

  override val rootIterators: List<IndexableFilesIterator> = listOf(ByRequestFileIterator(request))

  override fun toString() = "Project Index Chunk for '$name'"
}

internal class PersistentProjectIndexesGenerator: DumpSharedIndexCommand<PersistentProjectArgs> {
  override val commandName: String
    get() = "persistent-project"
  override val commandDescription: String
    get() = "Runs persistent indexes generator for a project"

  override fun parseArgs(parser: ArgsParser): PersistentProjectArgs = PersistentProjectArgs(parser)

//  private fun run(server: Server, description: String) {
//    server.start()
//    ConsoleLog.info("Indexing Server started: $description")
//    server.awaitTermination()
//    ConsoleLog.info("Indexing Server shutdown")
//  }

  fun recursivelyGetSources(path: Path): Sequence<Path> {
    return if (Files.isDirectory(path)) {
      Files.list(path).asSequence().flatMap { recursivelyGetSources(it) }
    } else sequenceOf(path)
  }

  override suspend fun executeCommand(args: PersistentProjectArgs, indicator: ProgressIndicator) {
    val indexingService = IndexingService(indicator)

    val jsonPathId = "plugin-demo-source-list-path"
    val projectIndexJsonLocation: String = System.getProperty(jsonPathId) ?: System.getenv(jsonPathId)
    val node = ObjectMapper().readTree(Paths.get(projectIndexJsonLocation).toFile())
    val moduleList = node.get("module-list") as ArrayNode
    val modules = moduleList.map {Paths.get(it.asText())}
    val rootList = node.get("root-list") as ArrayNode
    val roots = rootList.map {Paths.get(it.asText())}

    val projectDir = "/Users/mgajek/Projects/business/ij-indexing-tools/examples/full-project"
    val projectId = StringHash.calc(projectDir)
    println("projectId: $projectId")

    ConsoleLog.info("Got args:")
    ConsoleLog.info("projectDir: $projectDir")

    val startReq = StartupRequest.newBuilder()
      .setProjectDir(projectDir)
      .build()

    indexingService.start(startReq)
    ConsoleLog.info("Indexing Server started with $projectDir")

    modules.forEach { module ->
      val moduleName = module.name
      ConsoleLog.info("Collecting deps from $moduleName")

//      val command = listOf("sbt", "show $moduleName/dependencyClasspathFiles")
//      val process = ProcessBuilder(command)
//        .directory(Paths.get(projectDir).toFile())
//        .start()
//
//      val reader = BufferedReader(InputStreamReader(process.inputStream))
//      val depPathPrefix = "[info] *"
//
//      val deps = reader.useLines { lines ->
//        lines.filter { it.startsWith(depPathPrefix) }
//          .map { it.removePrefix(depPathPrefix).trim() }
//          .toList()
//      }

      val sources = recursivelyGetSources(module).map { it.toString() }.toList()

      ConsoleLog.info("Module: $moduleName")
      ConsoleLog.info("sources size: ${sources.size}")
      ConsoleLog.info("sources example: ${sources.first()}")
//      ConsoleLog.info("deps size: ${deps.size}")
//      ConsoleLog.info("deps example: ${deps.first()}")

      val indexReq = IndexRequest.newBuilder()
        .setProjectId(projectId)
        .setIndexDebugName("IndexDebugName")
        .setIndexId("IndexId")
        .addAllSources(sources)
        .setProjectRoot(System.getProperty("user.dir"))
        .build()

      val indexRes = indexingService.index(indexReq)

      ConsoleLog.info("##################################################")
      ConsoleLog.info("Index Res:")
      ConsoleLog.info("ijx: ${indexRes.ijxPath}")
      ConsoleLog.info("sha256: ${indexRes.ijxSha256Path}")
      ConsoleLog.info("metadata: ${indexRes.ijxMetadataPath}")
      ConsoleLog.info("##################################################")
      sleepCancellable(1000)
    }

//      val command = listOf("sbt", "show $moduleName/dependencyClasspathFiles")
//      val process = ProcessBuilder(command)
//        .directory(Paths.get(projectDir).toFile())
//        .start()
//
//      val reader = BufferedReader(InputStreamReader(process.inputStream))
//      val depPathPrefix = "[info] *"
//
//      val deps = reader.useLines { lines ->
//        lines.filter { it.startsWith(depPathPrefix) }
//          .map { it.removePrefix(depPathPrefix).trim() }
//          .toList()
//      }

    val sources = roots.flatMap { r -> recursivelyGetSources(r).map { it.toString() }.toList() }

    ConsoleLog.info("Module: root")
    ConsoleLog.info("sources size: ${sources.size}")
    ConsoleLog.info("sources example: $sources")
//      ConsoleLog.info("deps size: ${deps.size}")
//      ConsoleLog.info("deps example: ${deps.first()}")

    val indexReq = IndexRequest.newBuilder()
      .setProjectId(projectId)
      .setIndexDebugName("IndexDebugName")
      .setIndexId("IndexId")
      .addAllSources(sources)
      .setProjectRoot(System.getProperty("user.dir"))
      .build()

    val indexRes = indexingService.index(indexReq)

    ConsoleLog.info("##################################################")
    ConsoleLog.info("Index Res:")
    ConsoleLog.info("ijx: ${indexRes.ijxPath}")
    ConsoleLog.info("sha256: ${indexRes.ijxSha256Path}")
    ConsoleLog.info("metadata: ${indexRes.ijxMetadataPath}")
    ConsoleLog.info("##################################################")
    sleepCancellable(1000)
  }
}