#!/usr/bin/env bash

docker exec -i nvdb-oracledb bash < ./scripts/01_init.sh
docker cp ./scripts/02_init.sql nvdb-oracledb:/nvdb-tnits.sql
docker exec nvdb-oracledb bash -c "sqlplus / as sysdba @/nvdb-tnits.sql"
