package com.demo.chat.shell.host

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one line with bash for the `!` command, and prints what it prints.
 *
 * The working directory and the environment are those of the shell process.
 * Standard input is the null device, so a program that reads input ends at
 * once and cannot hold the shell. Standard error joins standard output, so
 * the lines keep their order.
 *
 * **Each line is flushed when it arrives.** A `PrintWriter` can keep output
 * until the command ends, and a long command must show its output as it runs.
 *
 * **Every exit status prints, 0 included.** The issue asks for the output and
 * the exit status. No status ends the shell, so [run] always returns normally.
 *
 * **An interrupt stops the command, and the shell continues.** While the
 * process runs, [interrupts] holds an action that kills bash and every
 * process that bash started. The command then prints `! interrupted` and the
 * exit status. [TerminalInterrupts] sends Ctrl-C to that action. A process
 * that leaves the process tree of bash, such as one started with `setsid`,
 * keeps running. See CHAT-wdokzwnj.
 *
 * Three seams make the failure cases testable. This class finds bash on [path]
 * itself, because `ProcessBuilder` resolves a program with the PATH of the
 * parent process: a child with an empty PATH still started bash on
 * 2026-10-17. [start] starts the process, so a test can make the start fail.
 * [interrupts] receives the stop action, so a test can send an interrupt.
 * See CHAT-jrlctimq.
 */
class HostCommand(
    private val path: String? = System.getenv("PATH"),
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val start: (ProcessBuilder) -> Process = { it.start() },
    private val interrupts: (Runnable) -> AutoCloseable = { AutoCloseable {} },
) {

    fun run(commandLine: String?, out: PrintWriter) {
        if (commandLine.isNullOrEmpty()) {
            out.println("Usage: !<command>. Runs the rest of the line with bash in $workingDirectory.")
            out.flush()
            return
        }
        val bash = findBash()
        if (bash == null) {
            out.println("! needs bash on the PATH. This shell has none.")
            out.flush()
            return
        }
        val process = try {
            start(
                ProcessBuilder(bash.path, "-c", commandLine)
                    .directory(workingDirectory)
                    .redirectInput(ProcessBuilder.Redirect.from(File(NULL_DEVICE)))
                    .redirectErrorStream(true)
            )
        } catch (e: IOException) {
            out.println("! could not start bash: ${e.message}")
            out.flush()
            return
        }
        val interrupted = AtomicBoolean(false)
        val status = interrupts(Runnable { interrupted.set(true); kill(process) }).use {
            try {
                process.inputReader().useLines { lines ->
                    lines.forEach { line ->
                        out.println(line)
                        out.flush()
                    }
                }
            } catch (e: IOException) {
                // Java closes the process streams when it kills the process.
                if (!interrupted.get()) throw e
            }
            process.waitFor()
        }
        if (interrupted.get()) out.println("! interrupted")
        out.println("exit status $status")
        out.flush()
    }

    /**
     * Kills bash first, so it starts no next command. The descendants are read
     * before that kill, because a child of a dead bash leaves its tree.
     */
    private fun kill(process: Process) {
        val descendants = process.descendants().toList()
        process.destroyForcibly()
        descendants.forEach { it.destroyForcibly() }
    }

    /** The first executable file named `bash` in a directory of [path]. */
    private fun findBash(): File? =
        path.orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotEmpty() }
            .map { File(it, "bash") }
            .firstOrNull { it.isFile && it.canExecute() }

    companion object {
        const val NULL_DEVICE = "/dev/null"
    }
}
