import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 10 },   // Warm-up phase
        { duration: '20s', target: 150 },  // Rapid spike load
        { duration: '30s', target: 150 },  // Sustained high concurrency (triggers HPA scale-up)
        { duration: '15s', target: 0 },    // Cool-down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],    // Strict SLA: <1% failures (validates zero 502s)
        http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // 1. Query unrecognized locations (computes JSON and accesses in-memory cache)
    const resRegions = http.get(`${BASE_URL}/api/v1/regions/unrecognized`);
    check(resRegions, {
        'status is 200': (r) => r.status === 200,
    });

    // 2. Query health probes
    const resHealth = http.get(`${BASE_URL}/actuator/health/readiness`);
    check(resHealth, {
        'readiness is 200': (r) => r.status === 200,
    });

    sleep(0.1);
}
