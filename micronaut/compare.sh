#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null)

SPRING_PORT=8080
MN_PORT=8082      # JVM Micronaut on 8082 (MICRONAUT_SERVER_PORT override works for JVM)
NATIVE_PORT=8081  # native binary has port baked in from application.yml

SPRING_JAR="$SCRIPT_DIR/spring-app/build/libs/spring-app-0.1.0.jar"
MN_JAR="$SCRIPT_DIR/micronaut-app/build/libs/micronaut-app-0.1.0-all.jar"
NATIVE_BIN="$SCRIPT_DIR/micronaut-app/build/native/nativeCompile/micronaut-books"

SPRING_PID="" MN_PID="" NATIVE_PID=""
SPRING_LOG="" MN_LOG="" NATIVE_LOG=""

cleanup() {
    [[ -n "$SPRING_PID" ]]  && kill "$SPRING_PID"  2>/dev/null || true
    [[ -n "$MN_PID" ]]      && kill "$MN_PID"      2>/dev/null || true
    [[ -n "$NATIVE_PID" ]]  && kill "$NATIVE_PID"  2>/dev/null || true
    [[ -n "$SPRING_LOG" ]]  && rm -f "$SPRING_LOG"
    [[ -n "$MN_LOG" ]]      && rm -f "$MN_LOG"
    [[ -n "$NATIVE_LOG" ]]  && rm -f "$NATIVE_LOG"
}
trap cleanup EXIT

wait_for() {
    local url=$1 name=$2 attempts=0
    until curl -sf "$url" > /dev/null 2>&1; do
        sleep 0.2
        attempts=$((attempts + 1))
        if [[ $attempts -gt 150 ]]; then
            echo "ERROR: $name did not respond at $url within 30s" >&2
            exit 1
        fi
    done
}

rss_mb() {
    ps -o rss= -p "$1" 2>/dev/null | awk '{printf "%.0f", $1/1024}'
}

# ── Banner ──────────────────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║       Micronaut vs Spring Boot — Three-Tier Comparison    ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# ── Build JARs if missing (measure time) ────────────────────────────────────
BUILD_TIME_SPRING="~30s"
BUILD_TIME_MN="~35s"
BUILD_TIME_NATIVE="~12 min"

cd "$SCRIPT_DIR"
if [[ ! -f "$SPRING_JAR" ]]; then
    echo "Building Spring Boot JAR..."
    T=$SECONDS
    ./gradlew :spring-app:bootJar -x test -q
    BUILD_TIME_SPRING="$((SECONDS - T))s"
fi
if [[ ! -f "$MN_JAR" ]]; then
    echo "Building Micronaut JAR..."
    T=$SECONDS
    ./gradlew :micronaut-app:shadowJar -x test -q
    BUILD_TIME_MN="$((SECONDS - T))s"
fi

# ── Start Micronaut native binary first (port 8081, baked in) ────────────────
HAS_NATIVE=false
if [[ -f "$NATIVE_BIN" ]]; then
    echo "Starting Micronaut (native) on :$NATIVE_PORT ..."
    NATIVE_LOG=$(mktemp)
    "$NATIVE_BIN" > "$NATIVE_LOG" 2>&1 &
    NATIVE_PID=$!
    wait_for "http://localhost:$NATIVE_PORT/health" "Micronaut native"
    HAS_NATIVE=true
fi

# ── Start Spring Boot ────────────────────────────────────────────────────────
echo "Starting Spring Boot      on :$SPRING_PORT ..."
SPRING_LOG=$(mktemp)
java -jar "$SPRING_JAR" > "$SPRING_LOG" 2>&1 &
SPRING_PID=$!
wait_for "http://localhost:$SPRING_PORT/actuator/health" "Spring Boot"

# ── Start Micronaut JVM (port override via env var — works for JVM) ──────────
echo "Starting Micronaut (JVM)  on :$MN_PORT ..."
MN_LOG=$(mktemp)
MICRONAUT_SERVER_PORT=$MN_PORT java -jar "$MN_JAR" > "$MN_LOG" 2>&1 &
MN_PID=$!
wait_for "http://localhost:$MN_PORT/health" "Micronaut JVM"

# ── Parse startup times ───────────────────────────────────────────────────────
SPRING_START=$(grep -o 'Started.*in [0-9.]* seconds' "$SPRING_LOG" 2>/dev/null | head -1 || echo "unknown")
MN_START=$(grep -o 'Startup completed in [0-9]*ms' "$MN_LOG" 2>/dev/null | head -1 || echo "unknown")
SPRING_MEM=$(rss_mb "$SPRING_PID")
MN_MEM=$(rss_mb "$MN_PID")

if $HAS_NATIVE; then
    NATIVE_START=$(grep -o 'Startup completed in [0-9]*ms' "$NATIVE_LOG" 2>/dev/null | head -1 || echo "unknown")
    NATIVE_MEM="$(rss_mb "$NATIVE_PID") MB"
else
    NATIVE_START="(not built)"
    NATIVE_MEM="—"
fi

# ── Results table ─────────────────────────────────────────────────────────────
echo ""
echo "┌──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐"
echo "│ Metric           │ Spring Boot (JVM)    │ Micronaut (JVM)      │ Micronaut (native)   │"
echo "├──────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤"
printf "│ %-16s │ %-20s │ %-20s │ %-20s │\n" "Startup" "$SPRING_START" "$MN_START" "$NATIVE_START"
printf "│ %-16s │ %-20s │ %-20s │ %-20s │\n" "Memory" "${SPRING_MEM} MB" "${MN_MEM} MB" "$NATIVE_MEM"
printf "│ %-16s │ %-20s │ %-20s │ %-20s │\n" "Build time" "$BUILD_TIME_SPRING" "$BUILD_TIME_MN" "$BUILD_TIME_NATIVE"
echo "└──────────────────┴──────────────────────┴──────────────────────┴──────────────────────┘"

if ! $HAS_NATIVE; then
    echo ""
    echo "  Build native binary to unlock the third column:"
    echo "    GRAALVM_HOME=\$HOME/.sdkman/candidates/java/25.0.2-graalce \\"
    echo "      ./gradlew :micronaut-app:nativeCompile -x test"
fi

# ── Request benchmarks (100 sequential requests) ──────────────────────────────
bench() {
    local url=$1 failures=0 times=""
    for _ in $(seq 1 100); do
        result=$(curl -s -o /dev/null -w "%{http_code}:%{time_total}" "$url")
        code="${result%%:*}"
        t="${result##*:}"
        [[ "$code" != "200" ]] && failures=$((failures + 1))
        times="$times $t"
    done
    if [[ $failures -gt 0 ]]; then
        printf "FAIL (%d/100 non-200)\n" "$failures"
        return
    fi
    printf "%s\n" $times | awk '
        BEGIN { min=999; max=0; sum=0; n=0 }
        { if ($1+0 > 0) { if ($1<min) min=$1; if ($1>max) max=$1; sum+=$1; n++ } }
        END { printf "avg %4dms  min %3dms  max %4dms\n", sum/n*1000, min*1000, max*1000 }'
}

echo ""
echo "── GET /books  (100 requests) ───────────────────────────────────────────────────"
printf "  Spring Boot     : "; bench "http://localhost:$SPRING_PORT/books"
printf "  Micronaut JVM   : "; bench "http://localhost:$MN_PORT/books"
if $HAS_NATIVE; then
    printf "  Micronaut native: "; bench "http://localhost:$NATIVE_PORT/books"
fi

echo ""
echo "── GET /books/search?author=Martin  (100 requests) ─────────────────────────────"
printf "  Spring Boot     : "; bench "http://localhost:$SPRING_PORT/books/search?author=Martin"
printf "  Micronaut JVM   : "; bench "http://localhost:$MN_PORT/books/search?author=Martin"
if $HAS_NATIVE; then
    printf "  Micronaut native: "; bench "http://localhost:$NATIVE_PORT/books/search?author=Martin"
fi



echo "All apps are running. Press ENTER to stop them."
read -r
