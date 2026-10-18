# The time of a complete register read

Measured on 2026-10-18 under `CHAT-jmnjvupr`, stage 1, task 8. The script is
`read.sh`. Every run used the stage 0 sandbox and the disposable memory core on
6990 and 6991, with a login as Admin. No run connected to a denied port.
Standard error was empty in all 20 processes.

A complete register read is 1 index read and 3 topic reads. That is 4 one-shot
processes in a row.

## The data was the exploratory stand-in

**These numbers are not a representative read.** `add-topic` fails on the core
(`CHAT-siaetqtr`), so `read.sh seed` ended with status 3 and made no data. The
stand-in is 4 `show-topics` reads with no data. It leaves out message loading,
filtering, JSON output, and realistic response sizes.

**The stand-in has no pass or fail against the 10-second target of owner
decision 20.** It cannot select the launch mode.

## Results

| Launcher | Data | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Median | Against 10 s |
|----------|------|-------|-------|-------|-------|-------|--------|--------------|
| `chat-shell-cli` (JVM) | stand-in | 18,514 ms | 15,777 ms | 15,023 ms | 14,983 ms | 15,159 ms | 15,159 ms | none |
| native image | not run | | | | | | | none |
| resident mode | not built | | | | | | | none |

## What the numbers show

- One process takes about 3.8 s in the median run (15,159 ms for 4 processes).
  This agrees with the 3.4 s of the single `whoami` run in task 6 and the 6.3 s
  to 7.4 s of stage 0, which used a launch through Maven.
- Four JVM starts in a row already pass 10 s with no data. A representative
  read adds message loading and JSON output to each process. So the JVM
  launcher is unlikely to meet the target. **This is an inference from the
  stand-in, not a measurement.**
- The first run is slower by about 3 s. The cause is not known.
- The native image does not start a context (`docs/NATIVE-IMAGE.md`, section
  "Shell one-shot"). It has no row.

## What the owner decides

The decision of task 9 needs a representative read. It waits for
`CHAT-siaetqtr`. After that issue is fixed, run `read.sh seed` and
`read.sh measure` again. The owner then chooses one of these:

- A one-shot launcher meets the target. Task 9 moves to its own issue.
- No one-shot launcher meets the target. Task 9 runs.
- The owner names another choice, or another basis.
