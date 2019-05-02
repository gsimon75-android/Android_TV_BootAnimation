ADBD_SRC=/mnt/sda1/custom_upgrade/adbd.hacked
ADBD_DST=/data/local/tmp/adbd.hacked

cp $ADBD_SRC $ADBD_DST
chmod 755 $ADBD_DST

mount -o bind $ADBD_DST /sbin/adbd
setprop service.adb.tcp.port 5555
setprop ctl.start adbd
