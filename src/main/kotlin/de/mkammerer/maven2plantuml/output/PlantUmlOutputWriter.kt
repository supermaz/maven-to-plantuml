package de.mkammerer.maven2plantuml.output

import de.mkammerer.maven2plantuml.model.Project
import de.mkammerer.maven2plantuml.model.Dependency
import java.io.BufferedWriter
import java.io.OutputStream

object PlantUmlOutputWriter : OutputWriter {
    override fun write(project: Project, settings: Settings, outputStream: OutputStream) {
        outputStream.bufferedWriter().use {
            writeHeader(it)

            writeClassCommands(project, settings, it)
            it.newLine()
            writeNormalModuleDependencies(project, settings, it)

            writeFooter(it)
        }
    }

    private fun writeNormalModuleDependencies(project: Project, settings: Settings, it: BufferedWriter) {
        for (module in project.modules) {
            if (settings.excludeModules.contains(module)) continue

            // val dependencies = module.findModuleDependencies(project.modules - settings.excludeModules, false)
            val dependencies = module.dependencies
            for (dependency in dependencies) {
                it.write("\"${module.artifact}\" ..> \"${dependency.artifact}\"")
                it.newLine()
                writeNormalDependencies(dependency, it)
            }
        }
    }

    private fun writeNormalDependencies(dep: Dependency, it: BufferedWriter) {
        
            // val dependencies = module.findModuleDependencies(project.modules - settings.excludeModules, false)
            val dependencies = dep.dependencies
            for (dependency in dependencies) {
                it.write("\"${dep.artifact}\" ..> \"${dependency.artifact}\"")
                it.newLine()
            }
    }

    private fun writeModuleDependencies(project: Project, settings: Settings, it: BufferedWriter) {
        for (module in project.modules) {
            if (settings.excludeModules.contains(module)) continue

            val dependencies = module.findModuleDependencies(project.modules - settings.excludeModules, false)
            for (dependency in dependencies) {
                it.write("\"${module.artifact}\" ..> \"${dependency.artifact}\"")
                it.newLine()
            }
        }
    }

    private fun writeClassCommands(project: Project, settings: Settings, it: BufferedWriter) {
        for (module in project.modules) {
            if (settings.excludeModules.contains(module)) continue

            it.write("class \"${module.artifact}\"")
            it.newLine()
        }
    }

    private fun writeFooter(it: BufferedWriter) {
        readFooterFromResources().forEach { line ->
            it.write(line)
            it.newLine()
        }
        it.newLine()
    }

    private fun writeHeader(it: BufferedWriter) {
        readHeaderFromResources().forEach { line ->
            it.write(line)
            it.newLine()
        }
        it.newLine()
    }

    private fun readHeaderFromResources(): List<String> {
        return javaClass.getResourceAsStream("/plantuml/header.puml").reader().use {
            it.readLines()
        }
    }

    private fun readFooterFromResources(): List<String> {
        return javaClass.getResourceAsStream("/plantuml/footer.puml").reader().use {
            it.readLines()
        }
    }
}