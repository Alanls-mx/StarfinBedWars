"""Read-only Minecraft 1.8 status probe for the isolated loopback proxy."""
import argparse
import json
import socket


def varint(value):
    result = bytearray()
    while True:
        part = value & 0x7f
        value >>= 7
        result.append(part | (0x80 if value else 0))
        if not value:
            return bytes(result)


def read_varint(stream):
    value = 0
    for index in range(5):
        part = stream.recv(1)
        if not part:
            raise EOFError("proxy closed status connection")
        value |= (part[0] & 0x7f) << (7 * index)
        if not part[0] & 0x80:
            return value
    raise ValueError("oversized VarInt")


def read_exact(stream, length):
    data = bytearray()
    while len(data) < length:
        part = stream.recv(length - len(data))
        if not part:
            raise EOFError("incomplete status response")
        data.extend(part)
    return bytes(data)


parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=45577)
args = parser.parse_args()
host = "127.0.0.1"
with socket.create_connection((host, args.port), timeout=5) as stream:
    stream.settimeout(5)
    address = host.encode("utf-8")
    handshake = varint(0) + varint(47) + varint(len(address)) + address + args.port.to_bytes(2, "big") + varint(1)
    stream.sendall(varint(len(handshake)) + handshake + b"\x01\x00")
    length = read_varint(stream)
    packet = read_exact(stream, length)
    if packet[0] != 0:
        raise RuntimeError("unexpected status packet")
    cursor = 1
    shift = 0
    size = 0
    while True:
        part = packet[cursor]
        cursor += 1
        size |= (part & 0x7f) << shift
        if not part & 0x80:
            break
        shift += 7
    status = json.loads(packet[cursor:cursor + size].decode("utf-8"))
    print(json.dumps({"host": host, "port": args.port, "version": status.get("version"),
                      "players": status.get("players"), "description": status.get("description")},
                     ensure_ascii=False))
