#!/usr/bin/env python3
"""Steady synthetic traffic against the SentinelOps API gateway.

Generates a realistic baseline (mostly successful orders, a few declines, some reads)
so failures injected later stand out in metrics. Standard library only.

    python load-generator/load_generator.py --rps 5 --duration 300
"""

from __future__ import annotations

import argparse
import json
import random
import statistics
import threading
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

SKUS = [("SKU-KEYBOARD", 49.99), ("SKU-MOUSE", 19.99), ("SKU-MONITOR", 229.00), ("SKU-CABLE", 7.50)]
DECLINE_RATE = 0.05  # share of orders deliberately above payment-service's limit


class Stats:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.reset()

    def reset(self) -> None:
        self.statuses: Counter[str] = Counter()
        self.latencies: list[float] = []

    def record(self, status: str, latency: float) -> None:
        with self._lock:
            self.statuses[status] += 1
            self.latencies.append(latency)

    def snapshot_and_reset(self) -> tuple[Counter[str], list[float]]:
        with self._lock:
            snapshot = (self.statuses, self.latencies)
            self.reset()
            return snapshot


def request(method: str, url: str, body: dict | None, timeout: float) -> tuple[str, dict | None]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return str(response.status), json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        return str(error.code), None
    except (urllib.error.URLError, TimeoutError, ConnectionError):
        return "conn_error", None


def random_order() -> dict:
    items = [
        {"sku": sku, "quantity": random.randint(1, 3), "unitPrice": price}
        for sku, price in random.sample(SKUS, k=random.randint(1, 3))
    ]
    if random.random() < DECLINE_RATE:
        items = [{"sku": "SKU-SERVER-RACK", "quantity": 1, "unitPrice": 9999.00}]
    return {"customerId": f"cust-{random.randint(1, 200)}", "currency": "USD", "items": items}


def one_iteration(base_url: str, timeout: float, stats: Stats) -> None:
    started = time.perf_counter()
    status, body = request("POST", f"{base_url}/api/orders", random_order(), timeout)
    stats.record(f"POST {status}", time.perf_counter() - started)
    # Read back roughly a third of created orders, like a client polling for status.
    if body and random.random() < 0.33:
        started = time.perf_counter()
        status, _ = request("GET", f"{base_url}/api/orders/{body['id']}", None, timeout)
        stats.record(f"GET {status}", time.perf_counter() - started)


def report(stats: Stats, interval: float, stop: threading.Event) -> None:
    while not stop.wait(interval):
        statuses, latencies = stats.snapshot_and_reset()
        if not latencies:
            print("no completed requests", flush=True)
            continue
        latencies.sort()
        p50 = statistics.median(latencies) * 1000
        p95 = latencies[int(len(latencies) * 0.95) - 1 if len(latencies) > 1 else 0] * 1000
        errors = sum(n for s, n in statuses.items() if not s.split()[1].startswith("2"))
        summary = ", ".join(f"{s}={n}" for s, n in sorted(statuses.items()))
        print(
            f"{time.strftime('%H:%M:%S')} req={len(latencies)} err={errors / len(latencies):.1%} "
            f"p50={p50:.0f}ms p95={p95:.0f}ms | {summary}",
            flush=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--rps", type=float, default=5, help="orders per second (default 5)")
    parser.add_argument("--duration", type=float, default=0, help="seconds to run; 0 = until Ctrl+C")
    parser.add_argument("--timeout", type=float, default=10, help="per-request timeout in seconds")
    parser.add_argument("--report-every", type=float, default=5)
    args = parser.parse_args()

    stats, stop = Stats(), threading.Event()
    threading.Thread(target=report, args=(stats, args.report_every, stop), daemon=True).start()
    deadline = time.monotonic() + args.duration if args.duration else float("inf")
    print(f"Sending ~{args.rps} orders/s to {args.base_url} (Ctrl+C to stop)", flush=True)

    with ThreadPoolExecutor(max_workers=max(4, int(args.rps * args.timeout))) as pool:
        next_at = time.monotonic()
        try:
            while time.monotonic() < deadline:
                pool.submit(one_iteration, args.base_url, args.timeout, stats)
                next_at += 1 / args.rps
                time.sleep(max(0.0, next_at - time.monotonic()))
        except KeyboardInterrupt:
            pass
        finally:
            stop.set()


if __name__ == "__main__":
    main()
