const DEFAULT_DURATION_SECONDS = 60;

export function createChaos() {
    const state = {
        cpu: 0,
        latencyMs: 0,
        errorRate: 0,
        disk: 0,
        down: false,
        downUntil: 0,
    };
    const timers = {};

    function clearTimer(kind) {
        if (timers[kind]) {
            clearTimeout(timers[kind]);
            delete timers[kind];
        }
    }

    function resetKind(kind) {
        clearTimer(kind);
        switch (kind) {
            case "cpu":
                state.cpu = 0;
                break;
            case "latency":
                state.latencyMs = 0;
                break;
            case "errors":
                state.errorRate = 0;
                break;
            case "disk":
                state.disk = 0;
                break;
            case "down":
                state.down = false;
                state.downUntil = 0;
                break;
            default:
                break;
        }
    }

    function scheduleExpiry(kind, durationSeconds) {
        clearTimer(kind);
        const ms = Math.max(0, Number(durationSeconds) || 0) * 1000;
        if (ms <= 0) {
            return;
        }
        timers[kind] = setTimeout(() => resetKind(kind), ms);
    }

    function apply(kind, payload) {
        const body = payload && typeof payload === "object" ? payload : {};
        const durationSeconds = body.durationSeconds ?? body.duration ?? DEFAULT_DURATION_SECONDS;
        switch (kind) {
            case "cpu":
                state.cpu = clamp01(body.value ?? 0.92);
                scheduleExpiry("cpu", durationSeconds);
                break;
            case "latency":
                state.latencyMs = Math.max(0, Number(body.latencyMs ?? body.value ?? 2000));
                scheduleExpiry("latency", durationSeconds);
                break;
            case "errors":
                state.errorRate = clamp01(body.value ?? 1);
                scheduleExpiry("errors", durationSeconds);
                break;
            case "disk":
                state.disk = clamp01(body.value ?? 0.96);
                scheduleExpiry("disk", durationSeconds);
                break;
            case "down":
                state.down = true;
                state.downUntil = Date.now() + Math.max(0, Number(durationSeconds) || 0) * 1000;
                scheduleExpiry("down", durationSeconds);
                break;
            case "reset":
                ["cpu", "latency", "errors", "disk", "down"].forEach(resetKind);
                break;
            default:
                return false;
        }
        return true;
    }

    function isDown() {
        if (state.down && state.downUntil > 0 && Date.now() >= state.downUntil) {
            resetKind("down");
        }
        return state.down;
    }

    function snapshot() {
        return {
            cpu: state.cpu,
            latencyMs: state.latencyMs,
            errorRate: state.errorRate,
            disk: state.disk,
            down: isDown(),
        };
    }

    return { apply, isDown, snapshot, resetKind };
}

function clamp01(value) {
    const n = Number(value);
    if (Number.isNaN(n)) {
        return 0;
    }
    return Math.min(1, Math.max(0, n));
}
