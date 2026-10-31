#!/usr/bin/env python3
import json
import subprocess

def run(*args):
    return subprocess.check_output(args, text=True).strip()

def main():
    container = run("docker", "compose", "--profile", "ai", "ps", "-q", "ollama")
    if not container:
        raise SystemExit("Ollama is not running. Start the ai profile first.")
    details = json.loads(run("docker", "inspect", container))[0]
    networks = details["NetworkSettings"]["Networks"]
    assert networks, "Ollama has no backend communication network"
    for network in networks:
        internal = run("docker", "network", "inspect", "--format", "{{.Internal}}", network)
        assert internal == "true", f"Unexpected external network: {network}"
    assert not details["HostConfig"].get("PortBindings"), "Ollama must not publish host ports"
    assert "OLLAMA_NO_CLOUD=1" in details["Config"]["Env"], "Cloud features must be disabled"
    for mount in details.get("Mounts", []):
        assert mount["Type"] == "volume", "Ollama should use a named model volume, not host bind mounts"
    for destination in ("1.1.1.1", "registry.ollama.ai"):
        probe = subprocess.run(["docker", "compose", "exec", "-T", "ollama", "timeout", "4", "bash", "-c",
                                'exec 3<>/dev/tcp/"$1"/443', "probe", destination], capture_output=True)
        assert probe.returncode != 0, f"Unexpected outgoing connection to {destination}"
        assert probe.returncode in (1, 124), f"Probe could not execute: return code {probe.returncode}"
        print(f"Outbound TCP blocked: {destination}:443")
    models = json.loads(run("docker", "compose", "exec", "-T", "app", "curl", "--fail", "--silent",
                           "--max-time", "5", "http://ollama:11434/api/tags"))
    assert models.get("models"), "Backend can reach Ollama but no model is installed"
    print("Verified: internal networks only, no published ports, cloud disabled, external TCP blocked, backend API reachable.")

if __name__ == "__main__":
    main()
