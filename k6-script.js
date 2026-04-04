import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Custom Metrics
const createDealDuration = new Trend('create_deal_duration');
const batchDealDuration = new Trend('batch_duration');
const getDealDuration = new Trend('get_deal_duration');
const failRate = new Rate('failed_requests');

// Load Scenarios Options
export const options = {
    // Medium Load Test scenario (default)
    stages: [
        { duration: '30s', target: 20 }, // Ramp up from 0 to 20 VUs over 30 seconds
        { duration: '2m', target: 20 },  // Stay at 20 VUs for 2 minutes
        { duration: '10s', target: 0 },  // Ramp down to 0 VUs over 10 seconds
    ],
    /* 
    // Smoke test (quick sanity) - Uncomment to use instead of stages, or configure via k6 run --vus 1 --iterations 10
    vus: 1,
    iterations: 10,
    */
    /*
    // Stress Test - Uncomment and comment out the other stages to use
    stages: [
      { duration: '1m', target: 100 },
      { duration: '1m', target: 100 },
      { duration: '30s', target: 0 },
    ],
    */
    thresholds: {
        // Less than 1% of requests fail
        'http_req_failed': ['rate<0.01'],
        // 95% of requests must complete below 500ms
        'http_req_duration': ['p(95)<500'],
        // 95% of create deal operations must complete below 400ms
        'create_deal_duration': ['p(95)<400'],
    },
};

// Environment Variables
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Currency choices
const currencies = ["USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD"];

/**
 * Returns a random currency code.
 */
function randomCurrency() {
    return currencies[Math.floor(Math.random() * currencies.length)];
}

/**
 * Returns a random decimal between 10.00 and 100000.00
 */
function randomAmount() {
    const min = 10.00;
    const max = 100000.00;
    const random = Math.random() * (max - min) + min;
    return parseFloat(random.toFixed(2));
}

/**
 * Generates a payload for a single deal.
 */
function generateDealRequest() {
    // Use uuid for complete uniqueness
    const uniqueId = uuidv4();

    return {
        dealUniqueId: uniqueId,
        fromCurrencyIsoCode: randomCurrency(),
        toCurrencyIsoCode: randomCurrency(),
        dealTimestamp: new Date().toISOString(),
        dealAmount: randomAmount(),
    };
}

export default function () {
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 1. Create a single deal
    const singleDealPayload = JSON.stringify(generateDealRequest());
    const createDealRes = http.post(`${BASE_URL}/api/deals`, singleDealPayload, params);

    // Track metrics and checks for the Create Deal endpoint
    createDealDuration.add(createDealRes.timings.duration);
    const successCreate = check(createDealRes, {
        'Create deal status is 201 or 409': (r) => r.status === 201 || r.status === 409,
        'Create deal response time < 500ms': (r) => r.timings.duration < 500,
    });
    failRate.add(!successCreate);

    // Parse ID (only if 201/created, though random uuid should usually be unique)
    let savedDealId = null;
    if (createDealRes.status === 201) {
        try {
            const responseBody = JSON.parse(createDealRes.body);
            savedDealId = responseBody.dealUniqueId || JSON.parse(singleDealPayload).dealUniqueId;
        } catch (e) {
            savedDealId = JSON.parse(singleDealPayload).dealUniqueId;
        }
    }

    // Brief think time before next request to mimic real user behavior
    sleep(0.1);

    // 2. Get deal by ID (if created successfully)
    if (savedDealId) {
        const getDealRes = http.get(`${BASE_URL}/api/deals/${savedDealId}`, params);

        getDealDuration.add(getDealRes.timings.duration);
        const successGet = check(getDealRes, {
            'Get deal status is 200': (r) => r.status === 200,
            'Get deal returned correct ID': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.dealUniqueId === savedDealId;
                } catch (e) {
                    return false;
                }
            },
        });
        failRate.add(!successGet);
    }

    sleep(0.1);

    // 3. Batch import (5 deals)
    const batchRequests = Array.from({ length: 5 }, () => generateDealRequest());
    const batchPayload = JSON.stringify(batchRequests);
    const batchRes = http.post(`${BASE_URL}/api/deals/batch`, batchPayload, params);

    // Track metrics and checks for the Batch endpoint
    batchDealDuration.add(batchRes.timings.duration);
    const successBatch = check(batchRes, {
        'Batch status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    });
    failRate.add(!successBatch);

    // Think time at the end of the iteration
    sleep(1);
}
