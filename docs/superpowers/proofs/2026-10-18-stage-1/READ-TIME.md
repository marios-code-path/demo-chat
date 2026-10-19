# The time of a complete register read

Measured on 2026-10-18 under `CHAT-jmnjvupr`, stage 1, task 8. The script is
`read.sh`. Every run used the stage 0 sandbox and the disposable memory core on
6990 and 6991, with a login as Admin. No run connected to a denied port.

A complete register read is 1 index read and 3 topic reads. That is 4 one-shot
processes in a row.

## The representative read

`read.sh seed` made the data on the core: 4 topics, 20 pointer lines in
`register-index`, and 5 entries of about 2 KB in each of 3 topics. It then read
every topic back and checked the record counts, and only then it wrote its
marker. The seed took 2 min 40 s.

`read.sh measure` ran the 4 reads 5 times: `messages register-index --format
records`, and `messages <topic> --format records --limit 1` for each topic. It
checked every response: exit status 0, empty standard error, and the expected
number of records of the expected topic. No check failed. The response sizes were
3,331 bytes for the index and about 2,500 bytes for a topic.

| Launcher | Data | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Median | Against 10 s | Against 30 s |
|----------|------|-------|-------|-------|-------|-------|--------|--------------|--------------|
| `chat-shell-cli` (JVM) | representative | 14,927 ms | 15,067 ms | 14,986 ms | 14,895 ms | 14,935 ms | **14,935 ms** | **over** | under |
| native image | not run | | | | | | | none | none |
| resident mode | not built | | | | | | | none | none |

The 10-second target and the 30-second hard limit are from owner decision 20.
**The JVM launcher exceeds the 10-second target** in all 5 runs. It is under the
30-second limit. A read above the target blocks stage 4 until the query cost is
solved.

## What the numbers show

- One process takes about 3.7 s. A topic read does not cost more than `whoami`
  (3.1 to 4.0 s), so the time is the JVM and Spring start, not the read. Four
  starts in a row therefore cost about 15 s whatever the data.
- The exploratory stand-in of the earlier pass (4 `show-topics` reads, no data)
  gave a median of 15,159 ms. The representative read is within 1.5 percent of it.
- A one-shot launcher cannot meet the target with four starts at this cost. A
  native image would need a start under about 2.4 s. It does not start a context
  (`docs/NATIVE-IMAGE.md`, section "Shell one-shot").
- Resident mode pays the start once. It is not built, so it has no row.

## The session-start command in one process

Sigma recommended on 2026-10-18: keep the index read and the three topic
reads, and run them in one JVM process. Measure from the launch to the last
output line. Build resident mode only if that command misses 10 seconds.
Enforce the 30-second limit. Sigma recommended this command. Mario confirmed
on 2026-10-18 that `AGENTS.md` will direct agents to run it (recorded in commit
`93010a3c`). This decision does not authorize a merge.

The command is `register-read`. `read.sh register` ran it 5 times on the same
kind of data (20 pointers in the form `<fp-id> | <topic-name> | <kind> |
<title>`, and 5 entries of about 2 KB in each of 3 topics). It checked every run:
exit status 0, empty standard error, 20 index records, and the newest entry of
each of the 3 topics. No check failed. The output was 11,714 bytes.

| Launcher | Command | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Median | Against 10 s | Against 30 s |
|----------|---------|-------|-------|-------|-------|-------|--------|--------------|--------------|
| `chat-shell-cli` (JVM) | `register-read` | 4,649 ms | 4,476 ms | 4,465 ms | 4,471 ms | 4,497 ms | **4,476 ms** | **under** | under |
| `chat-shell-cli` (JVM), with the launch stamp, a second run | `register-read` | 4,661 ms | 4,382 ms | 4,408 ms | 4,479 ms | 4,555 ms | **4,479 ms** | **under** | under |
| `chat-shell-cli` (JVM), with the supervisor, a third run | `register-read` | 4,520 ms | 4,367 ms | 4,350 ms | 4,323 ms | 4,380 ms | **4,367 ms** | **under** | under |

**The time is from the launch to the exit of the process.** The last output line
comes a few milliseconds before the exit. One start (about 3.7 s) plus the reads
(about 0.8 s) gives 4.5 s.

**The command meets the 10-second target, so resident mode (task 9) is not
built.** The four-process version above took 14,935 ms, because it paid four
JVM starts.

**The 30-second limit is a hard limit from the launch.** Sigma found on PR #259
that the first version started its timer when the command ran. A stall in the
startup could pass the limit, and a budget that startup had used up still allowed
a success. The JVM side has three parts, and `LaunchDeadline` holds them.

- `main` arms a watchdog thread before the application starts. At the limit it
  writes one line on standard error and halts the process with status 124.
- The command refuses to start its read when the budget is spent.
- The command prints its output under a lock. If the limit passed during the read,
  it prints nothing and gives 124. If the output is printed, the watchdog does
  not halt the process afterwards.

Sigma found two more gaps on the next review. The first was that the launcher
script can stall before Java starts, where no JVM watchdog exists. `chat-shell-cli`
now supervises `register-read` from its own start: it runs itself as a child in its
own process group, ends the whole group at the limit, and exits with 124. It holds
the standard error of the child, so a timeout leaves one line. The second was that
the output was printed under a lock, so a blocked write kept the watchdog from
halting the process. The lock is gone. The watchdog now halts the process with 124
at the limit even while the output is blocked, and the line is written by a helper
thread, so a blocked standard error cannot stop the halt. In that one case the
standard output can hold part of the text, and the contract says to discard it.
`shell-scripts/test-chat-shell-cli-deadline.sh` covers the launcher, and
`LaunchDeadlineTests` covers the blocked output and the blocked standard error.

Sigma found three more paths on the next review, and all three are fixed:
a child that ignores TERM survived the timeout and the bash job notice made a
second stderr line, a stalled `perl` before the timer had no deadline, and an
output that finished after the watchdog marked the command expired could report
success. The launcher now starts its timer first, runs every external command as
a job in its own process group, sends TERM and then KILL to the groups, sends KILL
to the group of the child when it ends, and keeps bash notices off standard error.
It uses no `perl`, and it reads the clock in a job with a 2-second bound that fails
closed. In the JVM, `finish` and `requireBudget` end the command through one path,
`LaunchDeadline.expire`, with the watchdog, so the line is written once and a late
output is never a success. A fourth review found one more path: an output that ended after the 30 s mark but
before the watchdog ran still counted as a success, because the state was still
"printing". `finish` now checks the time after the output and before the
transition, and `LaunchDeadlineTests` covers it with the clock seam.
The tests find processes by the exact PID that a stub
wrote and use `kill -0`, and they fail if no PID was written, so they work where a
process list is not readable.

The launch time is the earlier of the stamp `CHAT_SHELL_LAUNCH_EPOCH_MS`, which
`chat-shell-cli` sets, and the JVM start. A missing or broken stamp falls back to
the JVM start, and never to the current time.

Live proof, cases A14 and A15 of `RECORD.md`: with a launch stamp 28 s in the past,
the process ended after 2.2 s with status 124, one line on standard error, and
empty standard output. With a stamp 31 s in the past it ended after 0.2 s, with
the same result. `LaunchDeadlineTests` and `PubSubRegisterReadTests` cover the
watchdog, the spent budget, a budget that runs out during the read, and the
launch time.

## What this does not show

- The data has 3 recent topics. A register with more recent topics reads more
  topics in the same process. The reads run one after another, so the time grows
  by the read time of a topic. Stage 3 measures the real register.
- The later duplicate pointers of one fp ID, and a warning line for them, are
  stage 3 work.
- A cold JVM on another host can start slower. The measurement is one host.
