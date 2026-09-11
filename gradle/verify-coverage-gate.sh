#!/usr/bin/env bash
# Exercise the actual Gradle gate, including failures that used to pass silently.
set -euo pipefail
cd "$(dirname "$0")/.."
probe_dir="$PWD/build/coverage-gate-regression"
mkdir -p "$probe_dir"
cat > "$probe_dir/empty.init.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.named('jacocoTestReport').get().classDirectories.setFrom([])
}
GRADLE
cat > "$probe_dir/threshold.init.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.named('jacocoTestCoverageVerification').get().violationRules.rules.each { rule ->
        rule.limits.each { limit -> limit.minimum = new BigDecimal('1.00') }
    }
}
GRADLE
./gradlew jacocoTestCoverageVerification > "$probe_dir/baseline.log" 2>&1
if ./gradlew jacocoTestCoverageVerification -I "$probe_dir/empty.init.gradle" > "$probe_dir/empty.log" 2>&1; then
    echo 'ERROR: empty coverage selection passed' >&2
    exit 1
fi
if ! grep -q 'Coverage selection is missing expected classes' "$probe_dir/empty.log"; then
    cat "$probe_dir/empty.log" >&2
    exit 1
fi
if ./gradlew jacocoTestCoverageVerification -I "$probe_dir/threshold.init.gradle" > "$probe_dir/threshold.log" 2>&1; then
    echo 'ERROR: below-threshold coverage passed' >&2
    exit 1
fi
if ! grep -q 'Rule violated for' "$probe_dir/threshold.log"; then
    cat "$probe_dir/threshold.log" >&2
    exit 1
fi
# Restore a report generated under the production configuration.
./gradlew jacocoTestCoverageVerification > "$probe_dir/restored.log" 2>&1
echo "Coverage gate rejects empty scope and below-threshold results; logs: $probe_dir"
