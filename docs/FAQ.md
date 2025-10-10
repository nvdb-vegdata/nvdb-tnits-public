# FAQ

1. What (if anything) happens to the data (checks, cleanup, geometry changes) before we save it into RocksDB?
    - Veglenkesekvenser:
        - We use the ports from the veglenkesekvens to fill out start/sluttposisjon and start/sluttnode for each veglenke
        - We filter out closed veglenker (has sluttdato)
        - We convert geometry to WGS84 (used by OpenLR)
        - We store veglenker sorted by startposisjon and grouped by veglenkesekvensId
    - Vegobjekter:
        - For main vegobjekttyper (e.g. speed limits) we have to fetch the original start date (startdato for first version), because TN-ITS `validFrom` should be from the entire feature's lifetime
        - We ignore children
        - We extract only those attributes that are relevant for the export
        - We map from generic stedfestinger to linjestedfestinger (throwing error if vegobjekt has a different type)
        - If an update deletes a vegobjekt, we mark it as deleted (soft delete) so that we still can export an entry for it
2. How do we know the backfill is finished, and how does a restart continue after a crash without starting over?
    - We store both progress and finishing timestamp in a key value store (RocksDb):
        - `veglenkesekvenser_backfill_started` - set first time upon initial start, used to determine which additional events should be loaded
        - `veglenkesekvenser_backfill_completed` - timestamp for when the backfill successfully completed (only set once), used to determine if backfill should be started, resumed or skipped
        - During backfill: `veglenkesekvenser_backfill_last_id` - ID of latest veglenkesekvens that was backfilled (updated atomically with storing data). Used to store data in batches, and resume upon crash.
        - Vegobjekter: Exact same pattern, only difference is keys are stored with typeId as well: `vegobjekter_${typeId}_backfill_completed` etc.
3. When should we(if ever) run a new full backfill instead of just processing incremental changes?
    - Ideally, never.
    - If a systemic bug has been discovered, and we need to re-publish a complete snapshot (and reset updates)
        - In that case, manually delete backup of DB (maybe needs implementation as endpoint in tnits-katalog webapp)
4. What makes a veglenkesekvens or vegobjekt get marked "dirty," what chain reactions does that cause, and when do we clear those marks?
    - Vegobjekter: Marked dirty if it is an exported feature and receives an update
    - Veglenkesekvenser:
        - Marked dirty if veglenkesekvens is updated
        - NOTE: Skipped when updating if no snapshot has been performed yet
        - When a supporting vegobjekt is updated, its stedfestede veglenkesekvenser are marked dirty
    - When an update is exported for a specific type (e.g. speed limits), we find:
        - Speed limits marked as dirty directly
        - Speed limits stedfested on dirty veglenkesekvenser
    - All these features are then processed into `TnitsFeature` objects and compared with previously exported features
        - If there are any changes in the newly processed objects, the feature is included in the export
    - As part of every export (both snapshot and update) we save the exported features to `EXPORTED_FEATURES` column family for comparison on the next update
    - After every successful update export, we clear all dirty marks and old vegobjekt versions (closed or deleted)
5. How do we spot a damaged RocksDB or bad backup, and what are the steps to restore it if possible?
    - Since the RocksDB database itself only lives as long as the cronjob, every run starts with one of three possibilities:
        - There is no previosuly stored backup in S3; we start everything from scratch
        - There is a previously stored backup in S3, but restore fails; we start everything from scratch
        - There is a previously stored backup in S3, restore is successful; we continue where we left off
    - The RocksDB database is exported to S3 after a successful run of the job
    - If there is a successful restore, but the job fails during, the next run will restart from the same restore as the previous run
6. How to add a new exported feature type?
    - Bit too complex for a FAQ; best to compare how the various `ExportedFeatureType` types are handled, and find the relevant codelists and mappings in official TN-ITS docs.
