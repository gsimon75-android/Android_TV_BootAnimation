# Getting unrestricted root access on the TV.

We'll need to make changes that we're not (yet...) authorised to, so this usually means utilising some
privilege escalation exploit.

Take a look at [Metasploit](https://www.metasploit.com/) if you're interested, here I found a more direct
vulnerability.


## The delivery method

```
$ ls -l /dev/block/mmcblk0                         
brw-rw-rw- root     root     179,   0 2019-04-30 20:40 mmcblk0
```

Yes, the flash as a whole device is world-writeable. That's how we're going to
modify whatever we want.

As the first step, let's attach some nice big (>= 8GB) USB stick and dump the flash, so we won't have to do all the work on the device.
And having a backup is such a cozy feeling, even if we couldn't restore it if we'd stick in a boot loop (again)...

`dd if=/dev/mmcblk0 of=/mnt/sda1/mmcblk0.img bs=16777216`

By dumping `/proc/partitions` and listing `/dev/block/platform/hi_mci.1/by-name/` we may know the partitions' start and size (expressed in index of 512-byte sectors), as well as their purpose:

| dev        | start   |  size   | by-name      |
| ---------- | ------- | ------- | -------------|
| mmcblk0    |         | 7733248 |              |
| mmcblk0p1  |       0 |    2048 | fastboot     |
| mmcblk0p2  |    2048 |    2048 | bootargs     |
| mmcblk0p3  |    4096 |   20480 | recovery     |
| mmcblk0p4  |   24576 |    4096 | deviceinfo   |
| mmcblk0p5  |   28672 |   16384 | baseparam    |
| mmcblk0p6  |   45056 |   16384 | panelparam   |
| mmcblk0p7  |   61440 |   20480 | logo         |
| mmcblk0p8  |   81920 |   81920 | kernel       |
| mmcblk0p9  |  163840 |   40960 | misc         |
| mmcblk0p10 |  204800 | 1572864 | system       |
| mmcblk0p11 | 1777664 | 3735552 | userdata     |
| mmcblk0p12 | 5513216 |   81920 | atv          |
| mmcblk0p13 | 5595136 |   20480 | factorydata  |
| mmcblk0p14 | 5615616 | 1433600 | cache        |
| mmcblk0p15 | 7049216 |   20480 | bootmusic    |
| mmcblk0p16 | 7069696 |   20480 | bootmusicsec |

We actually don't need this information now, but it was useful for
examining the contents of the partitions, so if you want to mount some
of them on your desktop, then:

```
losetup -r -o $((512 * 204800)) --sizelimit $((512 * 1572864)) /dev/loop0 mmcblk0.img
mount -o ro /dev/loop0 /mnt/hack/system

... # look around in it

umount /mnt/hack/system
losetup -d /dev/loop0
```

The next step is to choose what to overwrite and with what content.


## The point of attack

Similar to that `burn-bootanim` there are several other update mechanisms, of which
I found `burn-logo.sh` the least important, so I chose that one to replace.


## File systems and whatnot

A partition image contains a lot of information in addition to the file contents as well:
the folder hierarchy, the locations of the content chunks, and the metadata (name, size,
datetimes, owner/group/permissions, security labels, etc.) of the files, just to name the
most common ones.

The way this additional information is arranged is determined by the *file system* the
partition is formatted with, in our case, `ext4`.

### Naive approach 1: Modify the whole disk image

**Don't do this, it's here just for the explanation!**

If we are to modify a file, all its meta-information should also kept consistent with
it, so the absolutely correct way would be:

1. Set up the loop device and mount the `p10` partition of `mmcblk0.img` on our desktop for read-write
2. Do our modifications
3. Umount it and detach the loop device
4. `dd` back that `mmcblk0.img` to the flash

However, overwriting the whole flash under a living, mounted system is way too dangerous.
If all goes well, it would do it, but if *anything* goes wrong, then *everything* is wrecked.


### Naive approach 2: Modify the partition image

The second approach would be to tweak only the `/system` partition, `p10`:

**Don't do this either, it's also here just for the explanation!**

1. `dd` only that partition (`dd if=/dev/block/mmcblk0 bs=512 skip=204800 count=1572864 of=/mnt/sda1/mmcblk0p10_system.img`)
2. Mount that (`mount -o loop /wherever/mmcblk0p10_system.img /mnt/hacked/system`)
3. Do our modifications
4. Umount it
5. `dd` back only that partition (`dd if=/mnt/sda1/mmcblk0p10_system.img of=/dev/block/mmcblk0 bs=512 seek=204800 count=1572864 conv=notrunc`)

The risk is less, but the impact is the same: if anything is wrong, the device is bricked.
There are a lot of things that *could* have been modified, and **each un-verified reboot-persistent change is a risk of bricking**.

If I had to do heavy changes, I'd choose this, but not until there is any simpler way, and fortunately there is one.


### The smallest possible change: Modify one sector only

The data unit of the storage itself (below the fs-layer) is the 512-byte sector, this is the smallest unit that
can be accessed, so no matter how a filesystem stores the information, the data within one sector is still atomic:
we can modify it **in place** and all the allocation metadata may stay the same.

Unless the filesystem maintains checksums of the files, or stores them compressed, of course, but ext4 doesn't do that.

So we'll find where that `burn-logo.sh` is on the disk, I mean *in which sector*, and overwrite only that one.

`-rwxrwxrwx  1 root   wheel  370 Nov 30  2017 system/bin/burn-logo.sh`

The script is 370 bytes long, so it fits in one sector, and a characteristic part of it is the line
`if [ -e $path/custom_upgrade/logo.img ]`, so we'll search for that.

```
$ fgrep -ab 'path/custom_upgrade/logo.img' mmcblk0.img 
239054907:    if [ -e $path/custom_upgrade/logo.img ]
...
```

There it is, the byte address is 239054907, so the sector address is 239054907 / 512 = 466904.

So, first dump the original sector:
`dd if=/dev/block/mmcblk0 bs=512 skip=466904 count=1 of=burn-logo.orig.bin`

Please **DO CHECK IT**, it should contain the same as the original `burn-logo.sh`,
padded with arbitrary junk from bytes 370..511.

If it isn't, then **DON'T CONTINUE**, and I really mean this!

We are going to replace it with this (`burn-logo.hacked.sh`):

```
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
```

That renaming business is there to prevent unwanted repeated execution in case
the device reboots with the USB stick still attached.

We're wielding a chainsaw with all safeties removed, so I want to be as sure
as possible that only those things will be executed as root that we explicitely
want.

I know it's uncomfortable to do the "Remove the stick, name it back to runme.inc,
plug the stick back" rain dance, but believe me, it's still far more comfortable 
than buying another TV because "I left the stick plugged, it appended something
twice and now it doesn't boot".

OK, back to business.

As the filesystem knows the file length to be 370 bytes, it'll return exactly
that amount of this sector when someone tries to read/execute it.

Now we've used less than that, so all our script will be returned, but now
with some leftover junk (up to byte 369), which can cause trouble.

So, pad this script up to 512 bytes with spaces, in a line below that `done`.


### The Point of No Return

Up to now we've only read things from the device, now we'll actually modify
one sector.

Only one that belongs to a non-essential file, but if we overwrite a **wrong** sector, it can cause any kind of trouble.

So, if you choose to proceed, you're doing it at your own risk, **I don't take
any responsibility whatsoever**.


So, write that hacked script back to the device:
`dd if=burn-logo.hacked.sh bs=512 count=1 seek=466904 conv=notrunc of=/dev/block/mmcblk0`

Each and every option of `dd` is important, and must be correct.
The `seek=...` tells which sector to overwrite, so if you found the original script
at a different offset (you've dumped the original, checked it and backed it up, right?!),
then you should change this address as well.

The `conv=notrunc` means that the output file is **not** to be truncated right
after the written data. Once I've corrupted an image by just forgetting to add that,
so be meticulous and check **everything** twice before you hit Enter.

If all went well, the file should have been changed: `cat /system/bin/burn-logo.sh`.
(If you've accessed that file since the last reboot, it might have been cached
and then you may see the cached old content.)


## Make the script triggered

As usual, create a `custom_upgrade` folder, place an appropriate `.json` in it that requests
`"burn-logo": 1`, put a `logo.img` there also (may be of 0 length, but must exist),
and a `runme.inc` that will be executed by our hacked `burn-logo.sh`.

As a first step, just make it start `adbd`:

```
setprop service.adb.tcp.port 5555
setprop ctl.start adbd
```

Insert the USB stick, (no reboot needed,) and in a few seconds a toast shall appear about upgrading the logo,
and then about the upgrade having completed.

Now you shall be able to connect via adb: `adb connect 192.168.70.64:5555`

## `adbd` as a plain user

This `adbd` is however running only as a `shell` user.

It does so because it is compiled without the `ALLOW_ADBD_ROOT` define,
so even with `ro.secure=0` and `ro.debuggable=1` it still reverts to a plain user.

If you're thinking about the "Rage Against The Cage" exploit, it won't work, because this version
of `adbd` is patched to check the return value of `setuid()`.

So we're going to do it the hard way: patching out this whole dropping-root thing
from a copy of `adbd`, and get that copy running.


## Modify `adbd` to keep the capabilities and uid/gid

[C source](https://android.googlesource.com/platform/system/core/+/refs/tags/android-4.4.2_r1.0.1/adb/adb.c)

I could've recompiled it with `ALLOW_ADBD_ROOT`, but I'm too lazy to set up the
whole android toolchain and rebuild the dependencies, and I've already have the
`objdump` and a hex editor at hand :D.

You know, there is the machine code and there are all those fancy languages
for the sissies who can't cope with machine code... (Just kidding :D !)

First of all, make a backup of `/sbin/adbd`. ("In God we trust, of the rest we make backups.")

Disassemble it: `arm-none-eabi-objdump -d -M force-thumb,reg-names-std adbd.orig | less`

I've identified and commented the relevant parts, see [`adbd.orig.list`](adbd.orig.list).

Two block of code must be zapped:


### Dropping of the caps

```
    for (i = 0; prctl(PR_CAPBSET_READ, i, 0, 0, 0) >= 0; i++) {
    ...
        int err = prctl(PR_CAPBSET_DROP, i, 0, 0, 0);
    ...
    }
```

This is here:
```
    91cc:	2400      	movs	r4, #0
    91ce:	4625      	mov	r5, r4
    91d0:	2200      	movs	r2, #0
...; prctl(PR_CAPBSET_READ, i, 0, 0, 0) >= 0; ...
    91d2:	2017      	movs	r0, #23	; PR_CAPBSET_READ
    ...
exit(1);
    9208:	2001      	movs	r0, #1
    920a:	f013 f8c7 	bl	0x1c39c
```

To jump over it, replace the first halfword:
```
    91cc:       e01f            b.n     0x920e
```

In the file the offset is 0x11cc, and remember, we're on little endian arch, so
that 0xe01f will be `1f e0`.


### Changing the uid/gid

```
        if (setgid(AID_SHELL) != 0) {
            exit(1);
        }
        if (setuid(AID_SHELL) != 0) {
            exit(1);
        }
```

It's here:
```
    923c:       f44f 60fa       mov.w   r0, #2000       ; 0x7d0
    9240:       f00e ebe8       blx     0x17a14
    9244:       2800            cmp     r0, #0
    9246:       d1df            bne.n   0x9208
    9248:       f44f 60fa       mov.w   r0, #2000       ; 0x7d0
    924c:       f015 fc71       bl      0x1eb32
    9250:       2800            cmp     r0, #0
    9252:       d1d9            bne.n   0x9208
```

Just zap it with `nop`-s:
```
    923c:       bf00            nop
    ...
    9252:       bf00            nop
```

### The complete changes

```
--- adbd.orig.hex	2019-04-30 20:41:35.356186000 +0400
+++ adbd.hacked.hex	2019-04-30 20:41:44.481757000 +0400
@@ -282,16 +282,16 @@
 0001190 2000 447e 58f7 6839 9125 f00e eeae 48dd
 00011a0 4478 f7fe efe6 2101 200d f015 fcdb f002
 00011b0 fdcd 48d9 4478 f013 f935 2800 f000 813a
-00011c0 4601 48d6 2201 4478 f013 f932 2400 4625
+00011c0 4601 48d6 2201 4478 f013 f932 e01f 4625
 00011d0 2200 2017 9500 4621 4613 f00e ec4c 2800
 00011e0 db15 1fa0 2801 d908 2200 2018 9500 4621
 00011f0 4613 f00e ec40 2800 db01 3401 e7e8 f00b
 0001200 feb2 6802 2a16 d0f8 2001 f013 f8c7 f8df
 0001210 e310 f10d 0910 44fe e8be 000f e8a9 000f
 0001220 e8be 000f e8a9 000f e89e 0003 e889 0003
-0001230 200a a904 f00e ec0e 2800 d1e5 f44f 60fa
-0001240 f00e ebe8 2800 d1df f44f 60fa f015 fc71
-0001250 2800 d1d9 4bb3 58f4 6821 07ca f100 8122
+0001230 200a a904 f00e ec0e 2800 d1e5 bf00 bf00
+0001240 bf00 bf00 bf00 bf00 bf00 bf00 bf00 bf00
+0001250 bf00 bf00 4bb3 58f4 6821 07ca f100 8122
 0001260 48b1 2100 4478 f00e ed66 b138 48af 2100
 0001270 4478 f00e ed60 2800 f040 80d9 f00a f92e
 0001280 f04f 0901 4daa a90e 48aa 447d 4478 462a
```


## Getting this root-adbd invoked

... instead of the original one.

Even if our script is running as root, `/sbin/adbd` is on
the root filesystem that we can't remount to read-write.

Besides, making an unverified change reboot-persistent is
a sure recipe for trouble...

So, we're going to over-bind-mount it :D.

Our new [`runme.inc`](runme.inc):

```
ADBD_SRC=/mnt/sda1/custom_upgrade/adbd.hacked
ADBD_DST=/data/local/tmp/adbd.hacked

cp $ADBD_SRC $ADBD_DST
chmod 755 $ADBD_DST

mount -o bind $ADBD_DST /sbin/adbd
setprop service.adb.tcp.port 5555
setprop ctl.start adbd
```

Reboot, attach USB stick, wait for the toasts, `adb connect ...`,
and ...

here we are, we have a root shell with all the caps :D.


## Making it persistent and auto-started

For this we need a `service` definition in some of the `/*.rc`,
that is not `disabled` and which we can intercept.

(Remember, these `/*.rc` are on the root image, that is
actually a ramdisk, uncompressed from a raw image on boot,
so it's difficult to modify and it's quite error-prone, and
the impact of an error is high.)

But look what's there at the end of `/init.rc`:

```
service preeshare /system/bin/preeshare.sh
    class main
    user root
    group root
    oneshot

service xiri /system/bin/xiriservice
    class main
    user root
    group root
    oneshot
```

Two commands that don't even exist on this `/system`, just waiting for us :).

This `xiriservice` will be our hacked adbd, and perhaps in the future
we may use that `preeshare.sh` for initialising a SuperSU, if need be.

**NOTE** And now you see the impact of leaving such loose ends.
If they were cleaned up, it would've been much harder to add a new service.

```
mount -o remount,rw /system
cp /data/local/tmp/adbd.hacked /system/bin/xiriservice
mount -o remount,ro /system
sync
```

Remove the USB stick, reboot, `adb connect`, and there you have the root shell again.

(As of the port number, it's using its hardwired default 5555. If you want something
else, then add a persistent property for it: `setprop persist.adb.tcp.port 5555`.)


## Cleaning up

As we no longer need it, we may even restore that `burn-logo.sh`, if we want.
(Do it on a file level, not by `dd` because it might have moved since.)

I don't need that logo updater (can `dd` it manually if need be), so I left it
as it is, just in case. Yet another known backdoor, who knows when will I need it...

[//]: # ( vim: set sw=4 ts=4 et: )
