#!/bin/bash
# Oracle database initialization script for TN-ITS prototype
# Creates a dedicated user in the FREEPDB1 pluggable database

set +e
createAppUser tnits password FREEPDB1