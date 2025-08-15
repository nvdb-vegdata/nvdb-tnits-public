#!/bin/bash

# Docker setup script for TN-ITS prototype

set -e

echo "🐳 Setting up Docker environment for TN-ITS prototype..."

# Start PostgreSQL database
echo "Starting PostgreSQL database..."
docker-compose up -d postgres

# Wait for database to be ready
echo "Waiting for database to be ready..."
until docker-compose exec postgres pg_isready -U tnits -d tnits; do
  echo "Database is not ready yet, waiting..."
  sleep 2
done

echo "✅ Database is ready!"

echo "🎉 Docker setup complete!"
echo ""
echo "To start only the database: docker-compose up -d postgres"
echo "To stop: docker-compose down"
