#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ ! -f .env ]]; then
    echo 'Thiếu .env chứa cấu hình MySQL và ứng dụng.' >&2
    exit 1
fi
set -a
source .env
set +a
exec bash ./mvnw spring-boot:run "$@"
