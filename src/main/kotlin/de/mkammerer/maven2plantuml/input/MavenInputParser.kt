package de.mkammerer.maven2plantuml.input

import de.mkammerer.maven2plantuml.model.Dependency
import de.mkammerer.maven2plantuml.model.Module
import de.mkammerer.maven2plantuml.model.Project
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.ArrayDeque

object MavenInputParser : InputParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun parse(input: InputStream): Project {
        val modules = mutableSetOf<Module>()

        var currentModule: Module? = null
        // var currentModuleDependencies = mutableListOf<Dependency>()
        // var currentLevel = 0
        var stack = ArrayDeque<Dependency>()

        input.bufferedReader().useLines { lines ->
            for (line in lines) {
                val module = parseModuleLine(line)
                if (module != null) {
                    // We found a module, so lets add the current module (if any) to the list of modules
                    // The modules dependencies are the dependencies we accumulated to far
                    // Then we reset the list of dependencies
                    // currentModule?.let {
                    //     modules.add(it.copy(dependencies = currentModuleDependencies))
                    //     currentModuleDependencies = mutableListOf()
                    // }
                    modules.add(module)

                    // The new current module is the module we just found
                    currentModule = module
                    // currentLevel = 0
                } else {
                    val dependency = parseDependencyLine(line)
                    if (dependency != null) {
                        // We found a dependency. If we have no current module, something is wrong. Otherwise
                        // we just add the dependency to the dependency list
                        if (currentModule == null) throw IllegalStateException("Dependency without module found")
                        // currentModuleDependencies.add(dependency)
                        if (dependency.depLevel == 0) {
                            currentModule?.dependencies?.add(dependency)
                            logger.info("Adding {} to module", dependency)
                            // if (stack.size > 0) {
                            //     stack.pop()
                            // }
                            // stack.push(dependency)
                        } else {
                            // if (currentLevel == dependency.depLevel) {
                            //     stack.pop()
                            // }
                            val depParent = findLastMatchingParent(stack, dependency.depLevel)
                            depParent?.dependencies?.add(dependency)
                            logger.info("Adding {} to dependency {}", dependency, depParent)
                        }
                        // if (currentLevel <= dependency.depLevel) {
                            stack.push(dependency)
                        // }
                        // currentLevel = dependency.depLevel
                    }
                }
            }
        }

        // Maybe we have a remaining module. Do the same steps we would do if we had found a new module
        // currentModule?.let {
        //     modules.add(it.copy(dependencies = currentModuleDependencies))
        // }
        for (module in modules) {
            logger.info("Found {} direct dependencies of module {}", module.dependencies.size, module)
        }
        return Project(modules)
    }

    // Matches a valid Maven identifier
    private const val identifier = """[a-zA-Z_0-9.\-]+"""

    // [INFO] group:artifact:type:version
    private val modulePattern = """\[INFO] ($identifier):($identifier):($identifier):($identifier)""".toRegex()
    // [INFO] +- group:artifact:type:version:scope
    // [INFO] \- group:artifact:type:version:scope
    private val directDependencyPattern = """\[INFO] [+\\]- ($identifier):($identifier):($identifier):($identifier):($identifier)""".toRegex()
    // [INFO] |  +- group:artifact:type:version:scope
    private val transitiveDependencyPattern = """\[INFO] (\|.*?)($identifier):($identifier):($identifier):($identifier):($identifier)""".toRegex()

    private fun parseModuleLine(line: String): Module? {
        val result = modulePattern.matchEntire(line) ?: return null
        logger.trace("Found module line '{}'", line)

        val group = result.groupValues[1]
        val artifact = result.groupValues[2]

        return Module(group, artifact).also {
            logger.debug("Found module {}", it)
        }
    }

    private fun findLastMatchingParent(stack: ArrayDeque<Dependency>, lvl: Int): Dependency? {
        var m = stack.size
        // logger.info("Stack size {}", m)
        // for (i in m .. 1) {
            // for (i in stack.indices.reversed()) {
        for (i in stack.indices) {
            // logger.info("Element {}", i)
            // logger.info("Checking dep {} {}", stack.elementAt(i), i)
            if (stack.elementAt(i).depLevel == lvl -1) {
                return stack.elementAt((i))
            }
        }
        return null
    }

    private fun parseDependencyLine(line: String): Dependency? {
        val result = directDependencyPattern.matchEntire(line) ?: return parseTransitiveDependencyLine(line)
        logger.trace("Found direct dependency line '{}'", line)

        val group = result.groupValues[1]
        val artifact = result.groupValues[2]
        val scope = result.groupValues[5]

        return Dependency(group, artifact, scope, false, 0).also {
            logger.debug("Found direct dependency {}", it)
        }
    }

    private fun parseTransitiveDependencyLine(line: String): Dependency? {
        val result = transitiveDependencyPattern.matchEntire(line) ?: return null
        logger.trace("Found transitive dependency line '{}'", line)

        val depDeep = countOccurrences(result.groupValues[1], '|');

        val group = result.groupValues[2]
        val artifact = result.groupValues[3]
        val scope = result.groupValues[6]

        return Dependency(group, artifact, scope, true, depDeep).also {
            logger.debug("Found transitive dependency {}", it)
        }
    }

    private fun countOccurrences(s: String, ch: Char): Int {
        return s.filter { it == ch }.count()
    }
}