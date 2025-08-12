#!/bin/bash

# Script to generate TN-ITS Java classes from XSD schemas using xjc

set -e

# Configuration
XSD_DIR="src/main/resources/xsd"
OUTPUT_DIR="build/generated-sources/jaxb"
PACKAGE_NAME="no.vegvesen.nvdb.tnits.model"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Generate classes from the main TNITS.xsd (this should import the other schemas)
echo "Generating TN-ITS classes from XSD schemas..."
xjc -d "$OUTPUT_DIR" \
    -p "$PACKAGE_NAME" \
    -extension \
    -Xequals \
    -XhashCode \
    -XtoString \
    "$XSD_DIR/TNITS.xsd"

echo "Classes generated successfully in $OUTPUT_DIR"
echo "Package: $PACKAGE_NAME"