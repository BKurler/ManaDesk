#!/bin/bash

# --- Resolve script directory (handles symlinks) ---
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
SCRIPTPATH=$(cd "$(dirname "$PRG")" && pwd)

cd "$SCRIPTPATH"

# --- WebKitGTK stability flags ---
export WEBKIT_DISABLE_DMABUF_RENDERER=1
export WEBKIT_DISABLE_COMPOSITING_MODE=1
export WEBKIT_FORCE_SANDBOX=0

# Optional: force X11 instead of Wayland
#export GDK_BACKEND=x11

# Optional: force light theme
#export GTK_THEME=Adwaita:light

# Optional: disable hardware acceleration
#export WEBKIT_DISABLE_ACCELERATED_2D_CANVAS=1

# --- Launch ManaDesk ---
exec "$SCRIPTPATH/manadesk" "$@"

