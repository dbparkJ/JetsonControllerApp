from __future__ import annotations

import argparse
import ipaddress
import socket

import miniupnpc


MAPPING_DESCRIPTION = "Jetson upload receiver HTTPS"


def sslip_hostname(address: str) -> str:
    parsed = ipaddress.ip_address(address)
    if parsed.version != 4 or not parsed.is_global:
        raise RuntimeError("The router did not report a public IPv4 address")
    return f"{'-'.join(address.split('.'))}.sslip.io"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Ensure the public HTTPS port reaches this upload receiver"
    )
    parser.add_argument("--expected-domain")
    parser.add_argument("--external-port", type=int, default=443)
    parser.add_argument("--internal-port", type=int, default=443)
    args = parser.parse_args()
    for value in (args.external_port, args.internal_port):
        if value < 1 or value > 65535:
            raise SystemExit("Ports must be between 1 and 65535")

    gateway = miniupnpc.UPnP()
    gateway.discoverdelay = 500
    if gateway.discover() < 1:
        raise SystemExit("No UPnP internet gateway was found")
    gateway.selectigd()
    external_address = gateway.externalipaddress()
    expected_hostname = sslip_hostname(external_address)
    if args.expected_domain:
        addresses = {
            item[4][0]
            for item in socket.getaddrinfo(
                args.expected_domain, args.external_port, type=socket.SOCK_STREAM
            )
        }
        if external_address not in addresses:
            raise SystemExit(
                "The configured upload domain no longer resolves to the router's public IP; "
                f"expected {external_address} ({expected_hostname})"
            )

    existing = gateway.getspecificportmapping(args.external_port, "TCP")
    if existing:
        existing_address = str(existing[0])
        existing_port = int(existing[1])
        if existing_address != gateway.lanaddr or existing_port != args.internal_port:
            existing_description = str(existing[2]) if len(existing) > 2 else ""
            if existing_description != MAPPING_DESCRIPTION:
                raise SystemExit(
                    f"TCP {args.external_port} is already forwarded to "
                    f"{existing_address}:{existing_port}"
                )
            if not gateway.deleteportmapping(args.external_port, "TCP"):
                raise SystemExit("Could not replace the stale TCP port mapping")
            existing = None
    if not existing:
        created = gateway.addportmapping(
            args.external_port,
            "TCP",
            gateway.lanaddr,
            args.internal_port,
            MAPPING_DESCRIPTION,
            "",
        )
        if not created:
            raise SystemExit("The router rejected the TCP port mapping")

    print(
        f"TCP {args.external_port} -> {gateway.lanaddr}:{args.internal_port}; "
        f"public hostname: {expected_hostname}"
    )


if __name__ == "__main__":
    main()
