#!/usr/bin/env bash
# Start the spike server created by setup-server.sh. Usage: ./start-server.sh <server-dir>
# On Windows the NeoForge run.sh fails (unix ':' classpath); use the win_args file instead.
set -euo pipefail
cd "${1:?server dir}"
ARGS=libraries/net/neoforged/neoforge/21.1.265
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) exec java @user_jvm_args.txt @$ARGS/win_args.txt nogui ;;
  *) exec java @user_jvm_args.txt @$ARGS/unix_args.txt nogui ;;
esac
