#!/system/bin/sh
probe_dir="$1"
sleep 90
if [ ! -f "$probe_dir/keep-enabled" ]; then
  sh "$probe_dir/rollback.sh"
fi
