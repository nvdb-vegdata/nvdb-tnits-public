#!/bin/bash

# Start the application in development mode with hot reload
echo "Starting NVDB TNITS Prototype in development mode with hot reload..."
echo "The application will automatically reload when you make code changes."
echo "Press Ctrl+C to stop the server."
echo ""

./gradlew run -Pdevelopment --continuous --quiet
