#!/usr/bin/env bash
# Build the React frontend, bundle it into the Spring Boot jar, and run it.
set -e
cd "$(dirname "$0")"
(cd frontend && npm install && npm run build)
rm -rf backend/src/main/resources/static/*
cp -r frontend/dist/* backend/src/main/resources/static/
cd backend && mvn spring-boot:run
