#!/system/bin/sh

usbpath="/mnt"
for path in $usbpath/*
do
    f=$path/custom_upgrade/runme.inc
    if [ -e $f ]
    then
        mv $f $f.done
        . $f.done
    fi
done
                                                                                                                                                                                                                                                                                                                                                  