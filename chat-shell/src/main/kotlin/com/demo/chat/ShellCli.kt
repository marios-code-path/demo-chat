package com.demo.chat

import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import java.io.OutputStreamWriter
import java.io.PrintWriter
import kotlin.system.exitProcess

/**
 * The entry point of the one-shot shell, in the class `com.demo.chat.ShellCliKt`.
 *
 * It starts the same application as [ChatApp] with the banner off. With
 * `chat.shell.mode=one-shot`, the application runs the program arguments as
 * one command and ends. This function then closes the context and exits with
 * the status of the exit table. `chat-shell-cli` sets the mode and the quiet
 * logging. See `docs/superpowers/specs/2026-10-17-topic-register-design.md`.
 *
 * **The file lives in `chat-shell`, not in `chat-deploy`.** The default
 * `chat-deploy` build has no `chat-shell` on its classpath, and `chat-shell`
 * depends on `chat-deploy` already. The entry point is top level for the same
 * reason as `ChatAppKt`: a native image reads the class of this frame.
 */
fun main(args: Array<String>) {
    val status = statusOfRun(PrintWriter(OutputStreamWriter(System.err, Charsets.UTF_8))) {
        SpringApplication(ChatApp::class.java).apply { setBannerMode(Banner.Mode.OFF) }.run(*args).close()
    }
    exitProcess(status.code)
}

/**
 * The exit status of [run]. A failure writes its line on [err] unless a
 * [ShellExit] wrote it already.
 *
 * **An [SpringApplication.AbandonedRunException] passes through.** The AOT
 * processor of Spring Boot runs `main` and ends the run with that exception on
 * purpose, to read the context it captured. A native build fails if `main`
 * takes it for an error and exits.
 */
fun statusOfRun(err: PrintWriter, run: () -> Unit): ShellExitStatus =
    try {
        run()
        ShellExitStatus.OK
    } catch (e: SpringApplication.AbandonedRunException) {
        throw e
    } catch (e: Throwable) {
        reportFailure(e, err)
    }

/**
 * The exit status of a failure that ended the run.
 *
 * A [ShellExit] has its line on standard error already, so this adds none.
 * Any other failure gets one line. A core that is not available at start is
 * such a failure, and it gives 69 (record F2).
 */
fun reportFailure(error: Throwable, err: PrintWriter): ShellExitStatus {
    ShellExitStatus.causes(error).filterIsInstance<ShellExit>().firstOrNull()?.let { return it.status }
    err.println("chat-shell: ${ShellExitStatus.messageOf(error)}")
    err.flush()
    return ShellExitStatus.of(error)
}
