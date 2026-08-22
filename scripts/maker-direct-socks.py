#!/usr/bin/env python3
"""SOCKS5 on :19050 that sends known maker onions to local TCP, not Tor.

After the funding wait the taker drops the maker socket and opens a new one
for ProofOfFunding. That new socket is closed by route_heartbeat before the
message lands. Keep the maker TCP for a few seconds and reuse it so PoF
arrives on the connection the maker still has in the wait state.
"""
from __future__ import annotations

import select
import socket
import struct
import subprocess
import threading
import time

LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = 19050
TOR_SOCKS = ("127.0.0.1", 9050)
STICKY_SECS = 20.0
# Only park sockets that lived through an idle confirm wait. Negotiate
# reconnects in <1s; reusing that half-closed TCP makes contract exchange EOF.

MAKERS = {
    "dmgfn254q2onz655em4kla2nncvnp4pqmh3fdgvh35km7zkh7yscz4id.onion": ("127.0.0.1", 26102),
    "2uepeirzgblpshvyglvrfevxzyqyw6unbvns6cj3wmk7dbcceb4zp4id.onion": ("127.0.0.1", 26103),
    # previous generation, kept so stale offerbook entries still map
    "cqx23tpuwbe5g2m6mzoeh3k6ujq4bi27z5gpxemeguogpvx2lbk5l6yd.onion": ("127.0.0.1", 26102),
    "2hbgwujldua7jjhzb56yjhrrn5nqnurp7cbmdiw536cg46wb2mx4h6qd.onion": ("127.0.0.1", 26103),
}

_park_lock = threading.Lock()
_parked: dict[str, tuple[socket.socket, float]] = {}


def refresh_onions() -> None:
    mapping = (
        ("coinswap-makerd1", 26102),
        ("coinswap-makerd2", 26103),
    )
    for container, port in mapping:
        try:
            onion = subprocess.check_output(
                [
                    "docker",
                    "exec",
                    container,
                    "cat",
                    "/home/coinswap/.coinswap/maker/tor/hostname",
                ],
                text=True,
                timeout=5,
            ).strip()
        except Exception:
            continue
        onion = onion.lstrip().removeprefix("x>").strip()
        if onion:
            MAKERS[onion] = ("127.0.0.1", port)


def recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("closed")
        buf += chunk
    return buf


def _close(sock: socket.socket | None) -> None:
    if sock is None:
        return
    try:
        sock.shutdown(socket.SHUT_RDWR)
    except OSError:
        pass
    try:
        sock.close()
    except OSError:
        pass


def _alive(sock: socket.socket) -> bool:
    try:
        sock.setblocking(False)
        try:
            data = sock.recv(1, socket.MSG_PEEK)
            return bool(data)
        except BlockingIOError:
            return True
        finally:
            sock.setblocking(True)
    except OSError:
        return False


def take_parked(dest: str) -> socket.socket | None:
    with _park_lock:
        item = _parked.pop(dest, None)
    if item is None:
        return None
    sock, expiry = item
    if time.time() <= expiry and _alive(sock):
        print(f"REUSE parked {dest}", flush=True)
        return sock
    _close(sock)
    return None


def park(dest: str, sock: socket.socket) -> None:
    with _park_lock:
        old = _parked.pop(dest, None)
        _parked[dest] = (sock, time.time() + STICKY_SECS)
    if old:
        _close(old[0])
    print(f"PARK {dest} for {STICKY_SECS:.0f}s", flush=True)


def pipe(client: socket.socket, remote: socket.socket, dest: str | None) -> None:
    parked = False
    started = time.time()
    tag = (dest or "?")[:12]
    up = down = 0
    try:
        while True:
            r, _, _ = select.select([client, remote], [], [], 120)
            if not r:
                break
            for src in r:
                data = src.recv(65536)
                if data:
                    if src is client:
                        up += len(data)
                    else:
                        down += len(data)
                if not data:
                    # Which side hung up is the whole diagnosis when a swap phase
                    # aborts: a maker EOF with bytes sent up means the maker
                    # rejected the message, not that the relay dropped it.
                    print(
                        f"[{tag}] EOF from {'taker' if src is client else 'maker'}"
                        f" after {time.time() - started:.2f}s up={up} down={down}",
                        flush=True,
                    )
                    # After the funding confirm wait the taker drops SOCKS and
                    # reconnects for ProofOfFunding. Park only those long-lived
                    # sockets; a negotiate close is too short and the maker TCP
                    # is already done.
                    if dest and src is client and (time.time() - started) >= 5.0:
                        park(dest, remote)
                        parked = True
                        remote = None
                    return
                dst = remote if src is client else client
                dst.sendall(data)
    except OSError:
        pass
    finally:
        _close(client)
        if not parked:
            _close(remote)


def socks5_connect_tor(dest_host: str, dest_port: int) -> socket.socket:
    upstream = socket.create_connection(TOR_SOCKS, timeout=20)
    upstream.sendall(b"\x05\x01\x00")
    if recv_exact(upstream, 2) != b"\x05\x00":
        raise ConnectionError("tor socks auth rejected")
    host_b = dest_host.encode()
    req = b"\x05\x01\x00\x03" + bytes([len(host_b)]) + host_b + struct.pack("!H", dest_port)
    upstream.sendall(req)
    hdr = recv_exact(upstream, 4)
    if hdr[1] != 0:
        raise ConnectionError(f"tor socks connect failed: {hdr[1]}")
    atyp = hdr[3]
    if atyp == 1:
        recv_exact(upstream, 4 + 2)
    elif atyp == 3:
        ln = recv_exact(upstream, 1)[0]
        recv_exact(upstream, ln + 2)
    elif atyp == 4:
        recv_exact(upstream, 16 + 2)
    else:
        raise ConnectionError("bad tor atyp")
    return upstream


def handle(client: socket.socket) -> None:
    dest_host = ""
    try:
        hello = recv_exact(client, 2)
        nmethods = hello[1]
        recv_exact(client, nmethods)
        client.sendall(b"\x05\x00")

        req = recv_exact(client, 4)
        atyp = req[3]
        if atyp == 1:
            dest_host = socket.inet_ntoa(recv_exact(client, 4))
        elif atyp == 3:
            ln = recv_exact(client, 1)[0]
            dest_host = recv_exact(client, ln).decode()
        elif atyp == 4:
            dest_host = socket.inet_ntop(socket.AF_INET6, recv_exact(client, 16))
        else:
            client.sendall(b"\x05\x08\x00\x01\x00\x00\x00\x00\x00\x00")
            return
        dest_port = struct.unpack("!H", recv_exact(client, 2))[0]

        direct = MAKERS.get(dest_host)
        sticky_key = dest_host if direct else None
        remote = take_parked(dest_host) if sticky_key else None
        if remote is None:
            if direct:
                remote = socket.create_connection(direct, timeout=10)
                via = f"direct {direct[0]}:{direct[1]}"
            else:
                remote = socks5_connect_tor(dest_host, dest_port)
                via = f"tor {dest_host}:{dest_port}"
            print(f"CONNECT {dest_host}:{dest_port} -> {via}", flush=True)
        client.sendall(b"\x05\x00\x00\x01\x00\x00\x00\x00\x00\x00")
        pipe(client, remote, sticky_key)
    except Exception as exc:
        print(f"error: {exc}", flush=True)
        try:
            client.close()
        except OSError:
            pass


def main() -> None:
    refresh_onions()
    print("maker map:", MAKERS, flush=True)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((LISTEN_HOST, LISTEN_PORT))
    server.listen(32)
    print(f"SOCKS5 {LISTEN_HOST}:{LISTEN_PORT}", flush=True)
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
