package de.mkammerer.maven2plantuml

import de.mkammerer.maven2plantuml.input.InputParser
import de.mkammerer.maven2plantuml.input.MavenInputParser
import de.mkammerer.maven2plantuml.model.Project
import de.mkammerer.maven2plantuml.output.ConsoleOutputWriter
import de.mkammerer.maven2plantuml.output.OutputWriter
import de.mkammerer.maven2plantuml.output.PlantUmlOutputWriter
import de.mkammerer.maven2plantuml.output.Settings
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
// import org.apache.maven.cli.MavenCli
import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.InvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.apache.maven.shared.invoker.Invoker
import org.apache.maven.shared.invoker.InvocationOutputHandler
import java.io.File
import java.io.InputStream
import java.lang.StringBuilder

private val logger = LoggerFactory.getLogger("de.mkammerer.maven2plantuml.Main")

fun main(args: Array<String>) {
    logger.info("Started")
    try {
        doRun(args)
    } finally {
        logger.info("Stopped")
    }
}

private val sb: StringBuilder = StringBuilder()
// private val mHandler = object : InvocationOutputHandler() {

//     override fun consumeLine(msg: String) {
//         sb.append(msg)
//     }
// }

// @SuppressLint("HandlerLeak")
    // private class MvnOutputHandler(private val sb: StringBuilder) : InvocationOutputHandler {
    //     public override fun consumeLine(msg: String) {
    //         sb.append(msg)
    //     }
    // }

private fun doRun(args: Array<String>) {
    val options = Options()
    options.addOption(null, "help", false, "Prints this help")
    options.addOption("i", "input", true, "Input file")
    options.addOption("o", "output", true, "Output file")
    options.addOption("e", "exclude", true, "Artifact names of modules to exclude. Separated by comma.")
    options.addOption(null, "console-output", false, "Instead of generating a PlantUML file, print the dependency graph to the console")

    val parser = DefaultParser()
    val cli = parser.parse(options, args)

    if (cli.hasOption("help") || !cli.hasOption("input") || !cli.hasOption("output")) {
        printHelp(options)
        return
    }

    if (System.getenv("MAVEN_HOME") == null || System.getenv("MAVEN_HOME").length == 0) {
        System.err.println("MAVEN_HOME env variable must be set when using pom.xml directly");
        return
    }

    // val sb: StringBuilder = StringBuilder()
    // val lambdaName : InvocationOutputHandler = { va: String -> {
    //     sb.append(va)
    // } }

    // val lambdaName: InvocationOutputHandler = InvocationOutputHandler() {
    //     // constructor() {}
    //     override fun consumeLine(msg: String) {
    //         System.out.println(msg)
    //         sb.append(msg)
    //     }
    // }

    // lambdaName.consumeLine("test1")

    val mHandler =  object : InvocationOutputHandler {
        override fun consumeLine(msg: String) {
            System.out.println(msg)
            sb.append(msg).append("\n")
           }

        }

    // val mHandler: MvnOutputHandler = MvnOutputHandler(sb)
    // mHandler.consumeLine("test2")

    /*
    ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
    ByteArrayOutputStream baosErr = new ByteArrayOutputStream();

    PrintStream out = new PrintStream(baosOut, true);
    PrintStream err = new PrintStream(baosErr, true);
     */

    logger.info("Running maven dependency:tree")
    // var mcli: MavenCli = MavenCli() 
    // mcli.doMain(arrayOf("dependency:tree"), cli.getOptionValue("input"), System.out, System.out)
    // mcli.doMain(arrayOf("verify"), cli.getOptionValue("input"), System.out, System.out)
    val request: InvocationRequest = DefaultInvocationRequest()
    request.setOutputHandler(mHandler)
    request.setPomFile( File( cli.getOptionValue("input") ) )
    request.setGoals( listOf( "dependency:tree" ) )
    request.setBatchMode(true)
    // request.setInputStream(InputStream.nullInputStream())
    val invoker: Invoker = DefaultInvoker()
    invoker.setMavenHome( File(System.getenv("MAVEN_HOME")))
    // invoker.setOutputHandler(mHandler)
    // /usr/share/maven-bin-3.6/bin
    invoker.execute( request )

    logger.info("maven output: " + sb.toString())
    
    logger.info("Done maven dependency:tree")

    val excludedModules = (cli.getOptionValue("exclude") ?: "").split(',').map { it.trim() }.toSet()

    val inputParser: InputParser = MavenInputParser
    val outputWriter: OutputWriter = if (cli.hasOption("console-output")) ConsoleOutputWriter else PlantUmlOutputWriter

    
    val outputFile = Paths.get(cli.getOptionValue("output")).toAbsolutePath()
    logger.info("Using output file {}", outputFile)

    logger.debug("Parsing input file")
    val project: Project

    if (cli.getOptionValue("input").endsWith("pom.xml")) {
        logger.info("Using pom maven result file")
        val inputStream: InputStream = sb.toString().byteInputStream()
        project = inputStream.use {
            inputParser.parse(it)
        }
    } else {
        val inputFile = Paths.get(cli.getOptionValue("input")).toAbsolutePath()
        logger.info("Using input file {}", inputFile)
        project = Files.newInputStream(inputFile).use {
            inputParser.parse(it)
        }
    }

    logger.debug("Compiling set of excluded modules")
    val settings = buildSettings(project, excludedModules)
    logger.info("Excluded modules: {}", settings.excludeModules)

    logger.debug("Writing output file")
    Files.newOutputStream(outputFile).use {
        outputWriter.write(project, settings, it)
    }

    logger.info("Success, check {}", outputFile)
}

fun buildSettings(project: Project, excludedModules: Set<String>): Settings {
    return Settings(
            excludeModules = project.modules.filter { excludedModules.contains(it.artifact) }.toSet()
    )
}

private fun printHelp(options: Options) {
    val formatter = HelpFormatter()
    formatter.printHelp("maven-to-plantuml", options)
}