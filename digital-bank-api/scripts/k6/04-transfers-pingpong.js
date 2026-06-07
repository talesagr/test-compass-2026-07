import http from "k6/http";
import { check, sleep } from "k6";

http.setResponseCallback(http.expectedStatuses(201, 409));

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ALICE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const BOB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

function idem(prefix) {
	return `k6-pp-${prefix}-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

export const options = {
	vus: Number(__ENV.VUS || 20),
	duration: __ENV.DURATION || "1m",
	thresholds: {
		checks: ["rate>0.95"],
		http_req_failed: ["rate<0.01"],
		http_req_duration: ["p(95)<15000"],
	},
};

function postTransfer(fromId, toId, key) {
	const body = JSON.stringify({
		fromAccountId: fromId,
		toAccountId: toId,
		amount: Number(__ENV.TRANSFER_AMOUNT || 0.5),
	});
	return http.post(`${BASE_URL}/transfers`, body, {
		headers: {
			"Content-Type": "application/json",
			"Idempotency-Key": key,
		},
	});
}

export default function () {
	const a = idem("ab");
	const r1 = postTransfer(ALICE, BOB, a);
	check(r1, {
		"ab 201 or 409": (r) => r.status === 201 || r.status === 409,
	});
	const b = idem("ba");
	const r2 = postTransfer(BOB, ALICE, b);
	check(r2, {
		"ba 201 or 409": (r) => r.status === 201 || r.status === 409,
	});
	sleep(0.08);
}
