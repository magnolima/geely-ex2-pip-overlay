#!/system/bin/sh
cmd overlay disable --user 0 com.geely.ex2.mica.pipoverlayprobe || exit 1
sysui_pid=$(pidof com.android.systemui)
if [ -n "$sysui_pid" ]; then
  kill $sysui_pid
fi
