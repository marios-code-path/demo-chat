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

## What the owner decides

No one-shot launcher meets the target. Under the plan, task 9 (resident mode)
then runs. The owner chooses one of these:

- Build resident mode (task 9).
- Reduce the number of reads in a session-start read, for example one process
  that reads the index and the topics. The design allows one command.
- Name another choice, or another basis.
