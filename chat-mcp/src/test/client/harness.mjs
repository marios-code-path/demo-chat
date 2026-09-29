#!/usr/bin/env node
/*
 * The pinned MCP client harness for the chat-mcp adapter.
 *
 * The harness spawns the adapter as a child process, speaks the Model Context
 * Protocol over its stdin and stdout, and prints one JSON transcript on its own
 * stdout. The harness asserts nothing. The caller reads the transcript and
 * decides.
 *
 * Usage:
 *
 *   node harness.mjs --read-id <id> --refused-id <id> -- <command> [args...]
 *
 * Everything after `--` is the adapter command. The harness appends nothing to
 * it.
 *
 * Why this file exists, and not `StdioClientTransport`:
 *
 * The shipped transport exposes the child stderr and no raw stdout. The
 * adapter must prove that stdout carries protocol frames alone, so the harness
 * needs the exact bytes the adapter wrote. A teeing transport is the only way
 * to read them.
 */

import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { LATEST_PROTOCOL_VERSION } from "@modelcontextprotocol/sdk/types.js";

const HERE = dirname(fileURLToPath(import.meta.url));

/** The bound on the exit that follows a stdin close, in milliseconds. */
const EXIT_BOUND_MILLIS = 5000;

/** The bound on one protocol call, in milliseconds. */
const CALL_BOUND_MILLIS = 15000;

/**
 * An MCP transport over a child process, which tees every raw stdout line.
 *
 * The line is recorded before the protocol reads it. A line that is not JSON
 * is recorded and reported, and the harness does not drop it.
 */
class TeeingStdioTransport {
  #child = null;
  #buffer = "";

  /** Every non-empty stdout line, in order, exactly as the adapter wrote it. */
  stdoutLines = [];

  /** Every stdout line that did not parse as JSON. */
  stdoutParseFailures = [];

  /** The whole of the child stderr, as text. */
  stderrText = "";

  /** The child exit, once it happens. */
  exit = null;

  /** The protocol version the server selected during initialize. */
  protocolVersion = null;

  #command;
  #args;

  constructor(command, args) {
    this.#command = command;
    this.#args = args;
  }

  async start() {
    this.#child = spawn(this.#command, this.#args, {
      stdio: ["pipe", "pipe", "pipe"],
    });
    this.#child.on("error", (error) => this.onerror?.(error));
    this.#child.on("close", (code, signal) => {
      this.exit = { code, signal };
      this.onclose?.();
    });

    this.#child.stderr.setEncoding("utf8");
    this.#child.stderr.on("data", (chunk) => {
      this.stderrText += chunk;
    });

    this.#child.stdout.setEncoding("utf8");
    this.#child.stdout.on("data", (chunk) => {
      this.#buffer += chunk;
      let index = this.#buffer.indexOf("\n");
      while (index >= 0) {
        const line = this.#buffer.slice(0, index);
        this.#buffer = this.#buffer.slice(index + 1);
        this.#accept(line);
        index = this.#buffer.indexOf("\n");
      }
    });
  }

  #accept(line) {
    if (line.trim().length === 0) {
      return;
    }
    this.stdoutLines.push(line);
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      // A line that is not JSON is a purity breach. It is recorded, and the
      // protocol is not told about it, because a protocol message it is not.
      this.stdoutParseFailures.push(line);
      return;
    }
    this.onmessage?.(message);
  }

  async send(message) {
    if (this.#child === null) {
      throw new Error("the transport is not started");
    }
    this.#child.stdin.write(`${JSON.stringify(message)}\n`);
  }

  async close() {
    if (this.#child !== null && !this.#child.killed) {
      this.#child.stdin.end();
    }
  }

  setProtocolVersion(version) {
    this.protocolVersion = version;
  }

  /** Close stdin if it is open, and wait for the child to exit. */
  awaitExit() {
    if (this.#child === null) {
      return Promise.resolve({ withinBound: false, millis: 0, code: null, signal: null });
    }
    const started = Date.now();
    return new Promise((resolve) => {
      const finish = () => {
        clearTimeout(timer);
        resolve({
          withinBound: true,
          millis: Date.now() - started,
          code: this.exit?.code ?? null,
          signal: this.exit?.signal ?? null,
        });
      };
      const timer = setTimeout(() => {
        this.#child.kill("SIGKILL");
        resolve({
          withinBound: false,
          millis: Date.now() - started,
          code: null,
          signal: null,
        });
      }, EXIT_BOUND_MILLIS);
      if (this.exit !== null) {
        finish();
      } else {
        this.#child.once("close", finish);
      }
    });
  }
}

/** Read one flag value from the harness arguments. */
function flag(name, args) {
  const index = args.indexOf(name);
  if (index < 0 || index + 1 >= args.length) {
    throw new Error(`the harness requires ${name}`);
  }
  return args[index + 1];
}

/** The version of the installed SDK, read from its own manifest. */
function sdkVersion() {
  const manifest = join(HERE, "node_modules", "@modelcontextprotocol", "sdk", "package.json");
  return JSON.parse(readFileSync(manifest, "utf8")).version;
}

/** Split stderr into non-empty lines. */
function linesOf(text) {
  return text
    .split("\n")
    .map((line) => line.trimEnd())
    .filter((line) => line.length > 0);
}

async function main() {
  const separator = process.argv.indexOf("--", 2);
  if (separator < 0) {
    throw new Error("usage: node harness.mjs [flags] -- <command> [args...]");
  }
  const own = process.argv.slice(2, separator);
  const command = process.argv[separator + 1];
  const commandArgs = process.argv.slice(separator + 2);
  if (command === undefined) {
    throw new Error("the harness requires an adapter command after --");
  }
  const readId = flag("--read-id", own);
  const refusedId = flag("--refused-id", own);

  const transport = new TeeingStdioTransport(command, commandArgs);
  const client = new Client(
    { name: "chat-mcp-harness", version: "0.0.1" },
    { capabilities: {} },
  );

  const calls = [];
  let tools = [];
  let serverVersion = null;
  let serverCapabilities = null;
  let connectError = null;

  try {
    await client.connect(transport);
    serverVersion = client.getServerVersion() ?? null;
    serverCapabilities = client.getServerCapabilities() ?? null;

    const discovered = await client.listTools({}, { timeout: CALL_BOUND_MILLIS });
    tools = discovered.tools.map((tool) => ({
      name: tool.name,
      title: tool.title ?? null,
      inputSchema: tool.inputSchema,
      outputSchema: tool.outputSchema ?? null,
      annotations: tool.annotations ?? null,
    }));

    const script = [
      { name: "chat_list_topics", arguments: {} },
      { name: "chat_get_topic", arguments: { topicId: readId } },
      { name: "chat_get_topic", arguments: { topicId: refusedId } },
    ];
    for (const step of script) {
      const answer = await client.callTool(step, undefined, { timeout: CALL_BOUND_MILLIS });
      calls.push({
        name: step.name,
        arguments: step.arguments,
        isError: answer.isError === true,
        structuredContent: answer.structuredContent ?? null,
        // The application error data of a failed call. The MCP wire name is
        // `_meta`, and a client reads it there.
        meta: answer._meta ?? null,
        text: (answer.content ?? [])
          .filter((part) => part.type === "text")
          .map((part) => part.text)
          .join(""),
      });
    }
  } catch (failure) {
    connectError = `${failure}`;
  }

  // Close the client, which closes the transport, which closes stdin. Then read
  // the exit. A protocol close and a stdin close are the same event here.
  try {
    await client.close();
  } catch {
    // A close that reports a failure still closed stdin. The exit is the proof.
  }
  const exit = await transport.awaitExit();

  const transcript = {
    nodeVersion: process.version,
    sdkVersion: sdkVersion(),
    sdkLatestProtocolVersion: LATEST_PROTOCOL_VERSION,
    protocolVersion: transport.protocolVersion,
    serverVersion,
    serverCapabilities,
    tools,
    calls,
    stdoutLines: transport.stdoutLines,
    stdoutParseFailures: transport.stdoutParseFailures,
    stderrLines: linesOf(transport.stderrText),
    exit,
    connectError,
  };
  process.stdout.write(`${JSON.stringify(transcript, null, 2)}\n`);
}

main().catch((failure) => {
  process.stderr.write(`chat-mcp-harness: ${failure?.stack ?? failure}\n`);
  process.exit(1);
});
