#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SPRING_IMAGE="cop/spring-books:latest"
MN_IMAGE="cop/micronaut-books:latest"
MN_NATIVE_IMAGE="cop/micronaut-books-native:latest"

# ── Banner ──────────────────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║    Micronaut vs Spring Boot — Docker Image Comparison     ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# ── Pre-flight ───────────────────────────────────────────────────────────────
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH." >&2; exit 1
fi
if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running." >&2; exit 1
fi

# ── Ensure JARs are built ────────────────────────────────────────────────────
SPRING_JAR="$SCRIPT_DIR/spring-app/build/libs/spring-app-0.1.0.jar"
MN_JAR="$SCRIPT_DIR/micronaut-app/build/libs/micronaut-app-0.1.0-all.jar"

if [[ ! -f "$SPRING_JAR" ]] || [[ ! -f "$MN_JAR" ]]; then
    echo "JARs not found — building (run compare.sh first, or build manually)..."
    cd "$SCRIPT_DIR"
    ./gradlew :spring-app:bootJar :micronaut-app:shadowJar -x test -q
    echo "Done."
    echo ""
fi

# ── Build Spring Boot JVM image ───────────────────────────────────────────────
if docker image inspect "$SPRING_IMAGE" &> /dev/null; then
    echo "Image $SPRING_IMAGE already exists — skipping."
    echo "  (docker rmi $SPRING_IMAGE to force rebuild)"
else
    echo "Building $SPRING_IMAGE ..."
    docker build --file "$SCRIPT_DIR/spring-app/Dockerfile" \
                 --tag "$SPRING_IMAGE" "$SCRIPT_DIR/spring-app"
    echo "Done."
fi
echo ""

# ── Build Micronaut JVM image ─────────────────────────────────────────────────
if docker image inspect "$MN_IMAGE" &> /dev/null; then
    echo "Image $MN_IMAGE already exists — skipping."
    echo "  (docker rmi $MN_IMAGE to force rebuild)"
else
    echo "Building $MN_IMAGE ..."
    docker build --file "$SCRIPT_DIR/micronaut-app/Dockerfile" \
                 --tag "$MN_IMAGE" "$SCRIPT_DIR/micronaut-app"
    echo "Done."
fi
echo ""

# ── Build Micronaut native image (requires GraalVM and pre-built binary) ──────
# The Dockerfile.native does a full nativeCompile inside Docker (5+ min).
# For a faster path, pre-build the binary with GraalVM and copy it in.
NATIVE_BIN="$SCRIPT_DIR/micronaut-app/build/native/nativeCompile/micronaut-books"

if docker image inspect "$MN_NATIVE_IMAGE" &> /dev/null; then
    echo "Image $MN_NATIVE_IMAGE already exists — skipping."
    echo "  (docker rmi $MN_NATIVE_IMAGE to force rebuild)"
    HAS_NATIVE=true
elif [[ -f "$NATIVE_BIN" ]]; then
    echo "Native binary found — building $MN_NATIVE_IMAGE ..."
    # Lightweight alternative: copy pre-built binary into distroless (much faster than full Docker build)
    TMP_DOCKERFILE=$(mktemp /tmp/Dockerfile.native-fast.XXXX)
    cat > "$TMP_DOCKERFILE" <<'EOF'
FROM gcr.io/distroless/cc-debian12
COPY micronaut-app/build/native/nativeCompile/micronaut-books /micronaut-books
EXPOSE 8081
ENTRYPOINT ["/micronaut-books"]
EOF
    docker build --file "$TMP_DOCKERFILE" --tag "$MN_NATIVE_IMAGE" "$SCRIPT_DIR"
    rm -f "$TMP_DOCKERFILE"
    echo "Done."
    HAS_NATIVE=true
else
    echo "Native binary not found — skipping native image build."
    echo "  To build it:"
    echo "    1. Install GraalVM CE for Java 25 (SDKMAN: sdk install java 25.0.2-graalce)"
    echo "    2. GRAALVM_HOME=\$HOME/.sdkman/candidates/java/25.0.2-graalce ./gradlew :micronaut-app:nativeCompile -x test"
    echo "    3. Re-run this script"
    HAS_NATIVE=false
fi
echo ""

# ── Artifact sizes ────────────────────────────────────────────────────────────
SPRING_JAR_SIZE=$(du -sh "$SPRING_JAR" 2>/dev/null | cut -f1)
MN_JAR_SIZE=$(du -sh "$MN_JAR" 2>/dev/null | cut -f1)
NATIVE_SIZE=$( [[ -f "$NATIVE_BIN" ]] && du -sh "$NATIVE_BIN" | cut -f1 || echo "—")

echo "── Artifact sizes ───────────────────────────────────────────────────────────────"
printf "  %-40s %s\n" "spring-app-0.1.0.jar"           "$SPRING_JAR_SIZE  (bytecode only — needs JRE ~200 MB to run)"
printf "  %-40s %s\n" "micronaut-app-0.1.0-all.jar"     "$MN_JAR_SIZE  (bytecode only — needs JRE ~200 MB to run)"
printf "  %-40s %s\n" "micronaut-books  (native binary)" "$NATIVE_SIZE  (self-contained — no JRE needed)"
echo ""
echo "  JARs look smaller, but the JRE they depend on adds ~200 MB."
echo "  The native binary ships everything inside the one file."
echo ""

# ── Docker image sizes ────────────────────────────────────────────────────────
echo "── Docker image sizes (artifact + runtime layer) ────────────────────────────────"
docker images --filter "reference=cop/*" \
    --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"
echo ""

# ── Three-tier reference table ────────────────────────────────────────────────
echo "── Cloud-native comparison (three tiers) ────────────────────────────────────────"
echo ""
echo "  Tier                     │ Startup   │ Artifact  │ Docker image │ JVM at runtime?"
echo "  ──────────────────────────┼───────────┼───────────┼──────────────┼────────────────"
echo "  Spring Boot   (JVM)      │ ~2.5 s    │  ~56 MB   │   ~350 MB    │ yes"
echo "  Micronaut     (JVM)      │ ~1.5 s    │  ~37 MB   │   ~250 MB    │ yes"
echo "  Micronaut     (native) * │ <200 ms   │ ~123 MB   │    ~50 MB    │ no — all-in-one"
echo ""
echo "  * Native: build with GraalVM, see micronaut-app/Dockerfile.native"
echo "    No reflection hints needed — Micronaut never used reflection at runtime."
echo ""
echo "  NOTE: this script only builds images — it does not start containers."
echo "  To run a container: stop compare.sh first (ports 8080/8081 are in use), then:"
echo "    docker run --rm -p 8080:8080 $SPRING_IMAGE"
echo "    docker run --rm -p 8081:8081 $MN_IMAGE"
if $HAS_NATIVE; then
    echo "    docker run --rm -p 8081:8081 $MN_NATIVE_IMAGE"
fi
echo ""
