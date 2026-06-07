import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
	vus: Number(__ENV.VUS || 30),
	duration: __ENV.DURATION || "1m",
	thresholds: {
		http_req_failed: ["rate<0.01"],
		http_req_duration: ["p(95)<3000"],
	},
};

export default function () {
	const res = http.get(`${BASE_URL}/accounts?page=0&size=20`);
	check(res, { "status 200": (r) => r.status === 200 });
	sleep(0.05);
}
