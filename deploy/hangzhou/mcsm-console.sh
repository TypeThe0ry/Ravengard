#!/usr/bin/env bash
# MCSManager console for the Ravengard ("adventure") stack. MCSManager runs this as the instance's
# process: starting it starts any stopped container (running ones are left alone) and follows the
# game logs; typing "stop" stops the whole stack gracefully. Killing this process only ends the
# console, the stack keeps running.
cd "$(dirname "$0")/server" || exit 1

compose() {
    docker compose "$@"
}

echo "[adventure] starting any stopped Ravengard container..."
compose start
compose ps --format '{{.Name}}  {{.Status}}'
echo "[adventure] console ready: status | players | stop"

compose logs -f --tail 30 proxy ravengard_lobby ravengard_dungeon &
logs=$!
trap 'kill "$logs" 2>/dev/null' EXIT

while IFS= read -r line; do
    case "${line//$'\r'/}" in
        stop)
            echo "[adventure] stopping the Ravengard stack..."
            compose stop
            echo "[adventure] stopped"
            exit 0
            ;;
        status|ps)
            compose ps --format '{{.Name}}  {{.Status}}'
            ;;
        players|list)
            echo "[adventure] players connected: $(ss -Htn state established '( dport = :25571 )' | wc -l)"
            ;;
        "")
            ;;
        *)
            echo "[adventure] commands: status | players | stop"
            ;;
    esac
done
