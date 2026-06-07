import http from "k6/http";
import { check, sleep } from "k6";

http.setResponseCallback(http.expectedStatuses(201, 409));

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ALICE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const BOB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

function idemKey() {
	return `k6-c-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

export const options = {
	stages: [
		{ duration: "20s", target: Number(__ENV.RAMP_TARGET || 25) },
		{ duration: __ENV.PEAK_DURATION || "1m", target: Number(__ENV.RAMP_TARGET || 25) },
		{ duration: "20s", target: 0 },
	],
	thresholds: {
		checks: ["rate>0.95"],
		http_req_failed: ["rate<0.01"],
		http_req_duration: ["p(95)<15000"],
	},
};

export default function () {
	const body = JSON.stringify({
		fromAccountId: ALICE,
		toAccountId: BOB,
		amount: Number(__ENV.TRANSFER_AMOUNT || 0.1),
	});
	const res = http.post(`${BASE_URL}/transfers`, body, {
		headers: {
			"Content-Type": "application/json",
			"Idempotency-Key": idemKey(),
		},
	});
	check(res, {
		"201 or 409": (r) => r.status === 201 || r.status === 409,
	});
	sleep(0.05);
}
