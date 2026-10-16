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

# Run through XWayland: under native Wayland SWT view toolbars are only
# painted on hover, and GNOME cannot match the window to ManaDesk.desktop
export GDK_BACKEND=${GDK_BACKEND:-x11}

# Linux desktops default to an 11pt UI font (Windows uses 9pt), which makes
# ManaDesk's views overflow. Scale text only (not icons or the display scale,
# so HiDPI/4K still work) to ~9pt; export GDK_DPI_SCALE=1 to keep full size.
export GDK_DPI_SCALE=${GDK_DPI_SCALE:-0.82}

# Optional: force light theme
#export GTK_THEME=Adwaita:light

# Optional: disable hardware acceleration
#export WEBKIT_DISABLE_ACCELERATED_2D_CANVAS=1

# --- Launch ManaDesk ---
# GTK logs harmless warnings whenever SWT lays out a widget at its transient
# ~1px size before the first real layout pass; drop only those from stderr.
# Set MANADESK_GTK_NOISE=1 to see everything.
if [ -n "$MANADESK_GTK_NOISE" ]; then
  exec "$SCRIPTPATH/manadesk" "$@"
fi
exec "$SCRIPTPATH/manadesk" "$@" 2> >(grep --line-buffered -v -E \
  -e 'Gtk-WARNING \*\*: .*Negative content (width|height)' \
  -e 'gtk_widget_size_allocate\(\): attempt to allocate widget with width' \
  -e "gtk_box_gadget_distribute: assertion 'size >= 0' failed" \
  -e '^\*\*\* BUG \*\*\*$' \
  -e 'pixman_region32_init_rect: Invalid rectangle passed' \
  -e "Set a breakpoint on '_pixman_log_error' to debug" \
  -e '^$' >&2)

