#!/usr/bin/env python3
"""Probe an Electrum server: TLS/plaintext reachability, version, and which chain
its genesis hash corresponds to.

Usage: python probe_electrum.py electrum.citadelfoss.xyz 50002 --tls
"""
import argparse
import json
import socket
import ssl
import sys

GENESIS = {
    "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f": "mainnet",
    "000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943": "testnet3",
    "00000000da84f2bafbbc53dee25a72ae507ff4914b867c565be350b0da8bf043": "testnet4",
    "00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6": "signet",
    "0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206": "regtest",
}


def call(sock, method, params, req_id):
    payload = json.dumps({"jsonrpc": "2.0", "id": req_id, "method": method, "params": params})
    sock.sendall((payload + "\n").encode())
    buf = b""
    while b"\n" not in buf:
        chunk = sock.recv(8192)
        if not chunk:
            raise RuntimeError(f"server closed connection during {method}")
        buf += chunk
    return json.loads(buf.split(b"\n", 1)[0].decode())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("host")
    ap.add_argument("port", type=int)
    ap.add_argument("--tls", action="store_true")
    ap.add_argument("--timeout", type=float, default=15.0)
    args = ap.parse_args()

    raw = socket.create_connection((args.host, args.port), timeout=args.timeout)
    if args.tls:
        # Many Electrum servers use self-signed certs, so verification is off here.
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        sock = ctx.wrap_socket(raw, server_hostname=args.host)
        print(f"TLS ok: {sock.version()} cipher={sock.cipher()[0]}")
    else:
        sock = raw

    with sock:
        ver = call(sock, "server.version", ["coinswap-probe", "1.4"], 0)
        print("server.version ->", ver.get("result", ver))

        feat = call(sock, "server.features", [], 1)
        print("server.features raw ->", json.dumps(feat)[:600])
        res = feat.get("result") or {}
        genesis = res.get("genesis_hash", "")
        print("genesis_hash   ->", genesis, "=>", GENESIS.get(genesis, "UNKNOWN CHAIN"))
        print("server proto   ->", res.get("protocol_min"), "-", res.get("protocol_max"))
        print("pruning        ->", res.get("pruning"))

        head = call(sock, "blockchain.headers.subscribe", [], 2)
        hres = head.get("result") or {}
        print("tip height     ->", hres.get("height"))

        # Genesis via block 0's header is the fallback when server.features is absent.
        blk = call(sock, "blockchain.block.header", [0], 3)
        hdr = blk.get("result")
        if isinstance(hdr, str):
            import hashlib
            raw_hdr = bytes.fromhex(hdr)
            h = hashlib.sha256(hashlib.sha256(raw_hdr).digest()).digest()
            genesis_from_header = h[::-1].hex()
            print("genesis(hdr)   ->", genesis_from_header, "=>",
                  GENESIS.get(genesis_from_header, "UNKNOWN CHAIN (custom signet?)"))
            globals()["genesis"] = genesis_from_header
        else:
            print("blockchain.block.header(0) ->", json.dumps(blk)[:300])

    return 0 if GENESIS.get(genesis) else 1


if __name__ == "__main__":
    sys.exit(main())
