import http from "k6/http";
import { check, sleep } from "k6";

http.setResponseCallback(http.expectedStatuses(201, 503));

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ALICE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const BOB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
const SHARED_KEY = __ENV.SHARED_IDEMPOTENCY_KEY || "k6-storm-shared-key";

export const options = {
	vus: Number(__ENV.VUS || 40),
	duration: __ENV.DURATION || "30s",
	thresholds: {
		checks: ["rate>0.95"],
		http_req_failed: ["rate<0.01"],
		http_req_duration: ["p(95)<20000"],
	},
};

export default function () {
	const body = JSON.stringify({
		fromAccountId: ALICE,
		toAccountId: BOB,
		amount: 2.0,
	});
	const res = http.post(`${BASE_URL}/transfers`, body, {
		headers: {
			"Content-Type": "application/json",
			"Idempotency-Key": SHARED_KEY,
		},
	});
	check(res, {
		"201 or 503": (r) => r.status === 201 || r.status === 503,
	});
	sleep(0.02);
}
