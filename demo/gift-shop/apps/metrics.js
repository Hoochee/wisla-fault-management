export function createMetrics(serviceName, chaos) {
    let lastCpu = process.cpuUsage();
    let lastAt = Date.now();
    let errorCount = 0;

    function sampleCpu() {
        const now = Date.now();
        const delta = process.cpuUsage(lastCpu);
        const elapsedUs = Math.max(1, (now - lastAt) * 1000);
        lastCpu = process.cpuUsage();
        lastAt = now;
        const ratio = Math.min(1, (delta.user + delta.system) / elapsedUs);
        return Math.max(ratio, chaos.snapshot().cpu);
    }

    function recordError() {
        errorCount += 1;
    }

    function render() {
        const snap = chaos.snapshot();
        const up = snap.down ? 0 : 1;
        const cpu = snap.down ? 0 : sampleCpu();
        const disk = snap.down ? 0 : Math.min(1, 0.12 + snap.disk);
        const latency = snap.latencyMs / 1000;
        const mem = process.memoryUsage().rss;
        return [
            `# HELP up 1 if ${serviceName} is serving, 0 if chaos/down is active`,
            `# TYPE up gauge`,
            `up ${up}`,
            `# HELP process_cpu_usage Process CPU usage ratio (0-1)`,
            `# TYPE process_cpu_usage gauge`,
            `process_cpu_usage ${cpu.toFixed(4)}`,
            `# HELP process_disk_usage Synthetic disk usage ratio (0-1)`,
            `# TYPE process_disk_usage gauge`,
            `process_disk_usage ${disk.toFixed(4)}`,
            `# HELP process_resident_memory_bytes Resident set size`,
            `# TYPE process_resident_memory_bytes gauge`,
            `process_resident_memory_bytes ${mem}`,
            `# HELP http_request_duration_seconds Injected request latency`,
            `# TYPE http_request_duration_seconds gauge`,
            `http_request_duration_seconds ${latency.toFixed(3)}`,
            `# HELP http_request_errors_total Count of chaos-induced API errors`,
            `# TYPE http_request_errors_total counter`,
            `http_request_errors_total ${errorCount}`,
            "",
        ].join("\n");
    }

    return { render, recordError };
}
