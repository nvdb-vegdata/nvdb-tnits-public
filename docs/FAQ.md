# FAQ

1. What (if anything) happens to the data (checks, cleanup, geometry changes) before we save it into RocksDB?
2. How do we know the backfill is finished, and how does a restart continue after a crash without starting over?
3. When should we(if ever) run a new full backfill instead of just processing incremental changes?
4. What makes a veglenkesekvens or vegobjekt get marked "dirty," what chain reactions does that cause, and when do we clear those marks?
5. How do we spot a damaged RocksDB or bad backup, and what are the steps to restore it if possible?
