# Android TV BootAnimation

The story began with buying a nice Android TV. As some reverse engineering will be involved, I
wouldn't like to publish the brand, let's just say that it matches the regex `/Ni.*ai/`.
It's quite a good TV by the way, well worth the price. Its built-in speakers are crap, but
you'd buy a decent sound bar to a TV anyway.

As every Android based TV, it requires some time (10-20 seconds) to boot up.
(Have you noticed that it's roughly the same time the ancient black-and-white CRT TVs required
to heat up the cathode? Fascinating, how much the science managed to speed thing up, isn't it...)

During this boot-up it plays some animation, neither an hourglass nor a spinner, but some in-and-out
culminating blobs with the words 'Wisdom' and 'Share' around them.
I bet they sound more inspirational and have more spiritual depth in their original language, than
in English, but after a while I wanted something ... fancier.

And so the quest began...


## Android bootanimations in general

The boot sequence of an Android system can be divided in 3 major steps:

1. Bootloader: It is read directly from a designated part of the flash, its purpose is to load
the next stage, and it may display a static logo, read also directly from a partition.

2. Linux: It starts with `/init`, which reads `/*.rc` and initialises the system
by starting all the services specified in those files. This is where the boot animation is started,
in parallel with the Android system.

3. Android: All the UI and services, and when the Launcher is ready, it notifies the bootloader
process that the startup is complete and it may now exit.

The bootanimation player is a Linux binary executable in `/system/bin/bootanimation`, and it
plays an animation from a .zip file, specified by the these system properties:

```
[ro.third.bootanimation.path_one]: [/data/video/bootanimation.zip]
[ro.third.bootanimation.path_two]: [/atv/bootvideo/bootanimation.zip]
```

The .zip file contains a `desc.txt` which describes the screen resolution, frame rate and
the parts of the animation. The parts are folders which contain the frames as still images,
I think we might call it MotionJPEG, only the images needn't be JPEGs, they may be PNGs as well.

[Here](https://www.addictivetips.com/mobile/how-to-change-customize-create-android-boot-animation-guide/)'s a good description about it.

In our case the 1st path doesn't exist, but the 2nd one does, so that `/atv/bootvideo/bootanimation.zip` has to be replaced.


## The intended way of replacing the bootanimation

That `bootanimation.zip` is not world-writeable, even its path is not readable, so the trivial way is not viable.


### The updater script

When searching for the string 'bootanimation' in `/system`, I saw not only the `bootanimation` binary, but also a `burn-bootanim.sh`, which was even readable (and executable).

```
#!/system/bin/sh

usbpath="/mnt"
for path in $usbpath/*
do
    if [ -e $path/custom_upgrade/bootanimation.zip ]
    then
		cp $path/custom_upgrade/bootanimation.zip /atv/bootvideo/bootanimation.zip
		sync
		break
    fi
done
```

Whoa! **When** this script is invoked, it checks for `custom_upgrade/bootanimation.zip` on all attached USB sticks, and uses the first one found to install as boot animation.

**NOTE** This is a security weakness, as it's basically a backdoor.

At least if there were some authentication, like 'if the files are digitally signed by someone whose certificate is signed by an authorised CA', but here the door is open for anyone who knows the filename to place on the USB stick.

Executing the script with plain user rights won't do any good, if I don't have the right to overwrite that file, neither will the scripts I invoke.

So, how is it invoked? Let's search for `burn-bootanim.sh`.


### The updater service 

In `init.rc`:

```
service burn-bootanim /system/bin/burn-bootanim.sh
    class burn-bootanim
    user root
    group root
    disabled
    oneshot
```

So if the init service 'burn-bootanim' is started (eg. `setprop svc.start burn-bootanim`), it'll invoke the script as root (who does have the right to overwrite that .zip).

**NOTE** Obscurity is not security, but this is a good example why those `*.rc` files are usually not world-readable.

Starting services also isn't allowed for mere users, so let's follow the chain, where is this service started?


### The updater trigger property

Also in `init.rc`:

```
on property:sys.cvt.burn-bootanim=1
    start burn-bootanim
    setprop sys.cvt.burn-bootanim 0
```

Setting the system property `sys.cvt.burn-bootanim` to 1 triggers it.
Not feasible for plain users, let's search further.

Not found anywhere! But why put such a half-mechanism there if it can't be invoked? There must be a reference somewhere!


### The updater Android service

And there is indeed, in one of the Android apps/services.

The command `pm list packages -f` shows all the installed apps and services and their .apk packages as well. Copy those to a USB stick, move it to our desktop so we can examine them more comfortably.

.apk files are essentially .zip archives, so they can be extracted (613MB in total), and then we can search for `sys.cvt.burn-bootanim` in them as well.

`Binary file ./cvte-tv-service.apk/classes.dex matches`

There is a fine piece of software that decompiles .dex binaries to .java sources: [jadx](https://github.com/skylot/jadx)

Let's decompile the `classes.dex` of that `cvte-tv-service.apk` package.


### The updater Android service source

... and continue searching within those sources.

`./com/cvte/tv/api/CustomUpgrade.java:45:    private static final String PROP_BOOTANIM = "sys.cvt.burn-bootanim";
`

And here we are! That [`CustomUpgrade.java`](CustomUpgrade.java) is quite straightforward, here is what it actually does:

1. It catches the `MEDIA_MOUNTED` intent (sent when a USB stick is mounted)
2. Checks if there is a `custom_upgrade/custom_upgrade_cfg.json` on the USB stick

Its format is like:
```
{
    "burn-bootanim":    0,
    "burn-ini":         0,
    "burn-pq":          0,
    "export-ini":       0,
    "export-pq":        0,
    "burn-key-xml":     0,
    "burn-logo":        0,
    "burn-panelparam":  0,
    "panelPin15":       0,
    "panelPin16":       0,
    "panelPin22":       0,
    "panelPinType":     0,
    "export-log":       0
}
```

3. If some of these fields are present and 1, and the corresponding data files are also present (if it makes sense for those commands)
4. Then the corresponding property will be set to 1

This is will trigger the service, that execute the script, and that will actually do the job.

A nice trip, wasn't it :D ?

Btw, I mentioned the complete lack of authentication before replacing key parts of the system, did I?

Imagine a malicious vandal with a malignantly prepared USB stick going around a TV shop, 'just checking how his favourite movie shows on this and that device', and meanwhile silently overwriting ... who knows what, with what consequences...


## How it turned out for me

Having been in IT for 25 years has made me a bit paranoid (but apparently still not paranoid enough), so before any invasive step I backed up whatever I had read access to. Including the readable parts of `/system` and the original `bootanimation.zip` as well.


### First attempt

Then I grabbed a USB stick, created a folder `custom_upgrade`, snatched a `bootanimation.zip` from the net, created that `.json` file, took a deep breath, and plugged the USB stick into the TV.

The toasts appeared, hooray! Reboot, and indeed the new boot animation was played.

A portrait animation on a landscape TV, but hey, the principle worked.


### Second attempt

Extracted the animation, rotated the images (`for` loops and `gm convert this.jpg -rotate 90 that.jpg` are the tools), modified the `desc.txt`, repacked the .zip, updated the animation, rebooted the TV.

Nothing at all.

Some experimenting revealed that the .zip mustn't use compression, so it's `zip -0r ../whatever.zip .`

And the landscape animation played well. The quality was crap, mostly because the frames were 590x352 .jpegs, and neither the upscaling nor the jpeg compression did any good to what most probably was crap even in the beginning.

But now I know how to construct my own `bootanimation.zip`, so...


### Third attempt (the almost-fatal one)

So I bought (yes, for real money, all the $2.50 of it) a nice 1920x1080 animation from [videobacks](https://www.videobacks.com), and extracted the frames from it (`ffmpeg -i whatever.mov -q 2 "part0/%03.jpg"`), all 399 of them.

Built the .zip, updated it, rebooted, and...

... and the animation started to play, lagged as hell, sometimes full white frames appeared ...

... and then the TV rebooted and the whole thing started over and over again!

Put the original .zip to the USB stick and tried to re-update it, hoping that the boot process reaches that point in some way, but no luck, just rebooting again and again.


### Is that what's called 'bricked'?

Almost. It's 'only' a boot loop, but the effects are the same: you have a nice 1.5m x 1m x 8cm thing that plays the start of that animation, some visual artifacts, and reboots, but is not responding in any way.

Searching the net for this case revealed that a lot of others have met this fate :(.

Most of the comments started with "Boot your phone into recovery mode by pressing this-and-that keys while powering on..."
Now, the TV does have physical 'Volume Up' and 'Down' keys, even one with 'Standby' on it (it actually powers down the set), but none of the combination had
any effect on the boot process.

There are 8 keys, so I tried all 256 combinations. A switchable power extender makes it a lot easier, you can trust my experience on this. Or even better, read on to know how to avoid it in the first place.

If I just had a serial console, or an alternate boot flash socket... I've been working with embedded Android devices before, so I'm somewhat familiar with this part, so I grabbed a screwdriver... The warranty can't be more void anyway...

"A programmer wielding a screwdriver is a bad omen, and one with a soldering iron is a herald of hell breaking loose.", mwahaaa :D !

Tough luch, no evident sign of service connector, or probe pads on the PCB, or covered sockets, or anything trivial.
Just one huge SoC big as half of my palm (at least its flat heatsink is that size), power regulators, display drivers, IR receiver, connectors, etc.

And my wife will be home in two hours, so I'll have some hard time explaining why have I bricked the TV - only for replacing the animation. And I also have a one-hour errand to run till then...

As a 'why not' attempt I plugged the USB stick in and left the set on, and went on that errand, mentally already preparing for the storm :D.


### The way out

When I arrived home 1.5 hours later, I found the TV booted up, the UI responsive, the boot animation flashed back to the original.

Either a miracle, or somehow the boot process got to the point where it recognised the USB stick and restored the `bootanimation.zip` from it.
Later on I'll tell what I think happened and how.

**NOTE** If you get this sort of boot loop, **let it run**, it's **not** deterministic (!), and sooner or later even a favourable execution flow may happen.

At that point I decided never to make unverified modifications that could block the way of restoring them if needed.

The keywords are 'unverified' and 'block the way of restoring'.

I never thought that a freaking boot animation can block the system from booting up, but apparently it can.
Therefore whatever .zip I want to give a try to, I want first test it in a non-reboot-persistent way.

(Could've known not to trust any unfounded assumptions, but better late than never, lesson learned.)

But to do proceed I will definitely need unrestricted root access!


## Getting root access

Read about it [here](ROOTING.md)


## Testing boot animations manually

`/system/bin/bootanimation` can be started manually, and if done with root privileges, it'll
just display the animation. Or crash. Or reboot, in which case it'll be invoked at startup
on the same .zip file again...

The simplest way to 'change' a file in a non-reboot-persistent way is to over-bind-mount it,
so:

```
#!/system/bin/sh

f="/atv/bootvideo/bootanimation.zip"
umount $f

dmesg -c > /dev/null

mount -o bind $1 $f

/system/bin/bootanimation &
while sleep 0.2; do
    ps | grep bootanim
    #grep -e MemFree -e SwapFree -e Commit /proc/meminfo
    dmesg -c
done
```

Even if it crashes, that bind-mount is transient, so even if a hard power-on-off
is needed, it'll boot with the stable original boot animation.

First let's test the original one:

### `bootanimation.orig.zip`

* size: 3230326
* frames: 65
* frame format: PNG 940x398
* frame size: 20..100 kbytes

```
root      3028  3026  34888  7136  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  55376  4476  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  77280  5876  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  89616  6496  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  97856  6620  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  110240 5956  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  122572 3956  ffffffff b6efb644 S /system/bin/bootanimation
<6>lowmemorykiller: Killing 'd.play.games.ui' (2613), adj 1000,
<6>   to free 20784kB on behalf of 'kswapd0' (442) because
<6>   cache 32112kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 17432kB above reserved
<6>lowmemorykiller: Killing 'id.partnersetup' (2533), adj 1000,
<6>   to free 16084kB on behalf of 'kswapd0' (442) because
<6>   cache 31656kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 17672kB above reserved
<6>lowmemorykiller: Killing 'm.cvte.fac.menu' (2235), adj 1000,
<6>   to free 15372kB on behalf of 'kswapd0' (442) because
<6>   cache 31048kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 20180kB above reserved
<6>lowmemorykiller: Killing 'd.configupdater' (2468), adj 1000,
<6>   to free 15132kB on behalf of 'kswapd0' (442) because
<6>   cache 30668kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 23236kB above reserved
<6>lowmemorykiller: Killing 'cvte.tv.setting' (2565), adj 1000,
<6>   to free 15068kB on behalf of 'kswapd0' (442) because
<6>   cache 29984kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 25404kB above reserved
<6>lowmemorykiller: Killing 'timeinitializer' (2510), adj 1000,
<6>   to free 14920kB on behalf of 'kswapd0' (442) because
<6>   cache 29908kB is below limit 32384kB for oom_score_adj 1000
<6>   Free memory is 27228kB above reserved
root      3028  3026  153436 3756  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  163712 4580  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  174000 4376  ffffffff b6efb644 S /system/bin/bootanimation
<6>lowmemorykiller: Killing 'android.youtube' (2578), adj 764,
<6>   to free 22928kB on behalf of 'kswapd0' (442) because
<6>   cache 25820kB is below limit 25904kB for oom_score_adj 529
<6>   Free memory is 14508kB above reserved
root      3028  3026  191056 2544  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 2004  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1304  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1320  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1352  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1360  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1356  ffffffff b6efb644 S /system/bin/bootanimation
root      3028  3026  191056 1360  ffffffff b6efb644 S /system/bin/bootanimation
```

`lowmemorykiller`, hmmm... it rings a bell:

* [What it does](https://forum.xda-developers.com/showthread.php?t=622666)
* [How to finetune](https://elinux.org/Android_Notes#OOM_Killer_information)

On an up-and-running system it even makes sense (considering the restartable nature of
suspended Activities), but during a startup every process is needed right there and
right then.

Besides, the original animation would use about 191000 kbytes...

... which it doesn't have in an up-and-running system,

... but during startup there **is** enough memory, otherwise it wouldn't boot normally.


## The cause of the boot loop

Now it's getting clearer: if the animation was *too big*, this ~f.cking~ naughty
lowmemkiller has shot some innocent process in the head, and as it happened during startup,
it proved fatal.

And when I let it try again and again, once it made a lucky guess and killed either
only some non-essentials or maybe `bootanimation` itself, and the boot process could continue.

By the way, lowmemkiller has a bigger brother, the [OOM killer](https://www.win.tue.nl/~aeb/linux/lk/lk-9.html#ss9.6),
which is another sad example of irresponsibly committed half-measure 'but it worked for me once'
hacks, that just make everything unpredictable and unstable.

On the other hand, an even sadder thing is that abomination does have a reason for existence,
namely Android, which does allocate way more memory that it touches even once.

```
# cat /proc/meminfo
MemTotal: 752608 kB
MemFree: 252784 kB
...
SwapTotal: 102396 kB
SwapFree: 89296 kB
...
CommitLimit: 478700 kB
Committed_AS: 12570532 kB
```

In fact, it acquires about 12.6 **GB**s, of which it actually uses about 510 MBs.
Yes, this ... junk ... is the OS you're running on your phone...

But back to business, we've got two more steps to go.


## Testing boot animations in real startup

So the question is how much memory is *available* at startup?

The answer is: I can't tell, because it depends on the heuristics of `lowmemkiller`.

All I can do is to devise some way that on a reboot a given .zip is tried 
**only once**, and the next reboot shall use the original .zip even if the previous
attempt crashed the system.

`/system/bin/bootanimation` needn't be a binary executable, it might as well be a script,
so we may rename the original to `bootanimation.orig`, and put [this](bootanimation.hacked) in its place
(take care to set the ownership and permissions to match):

```
#!/system/bin/sh

f="/data/local/tmp/bootanimation.test.zip"
if [ -r $f ]; then
	mv $f $f.tried
	mount -o bind $f.tried /atv/bootvideo/bootanimation.zip
fi

exec /system/bin/bootanimation.orig "$@"
```

If there is a file to test, it renames it (to prevent testing it on the next reboot),
then over-mounts the boot animation .zip with it, and then passes the execution to the
original `bootanimation` binary.


### The results

I've made some experiments, changing the following factors:

* Number of frames
* Resolution of frames
* Format of frames (.png vs high-compression .jpg vs. high-quality .jpg)
* Display size (in `descr.txt`)

It turned out that

* The display size in `descr.txt` doesn't matter, 960x540 frames need the same memory when scaled up to 1920x1080
* The frame format (.png vs. .jpg) matters only a little: where 51 .jpgs just didn't cause lowmemkills, 51 .png frames did, but 49 .pngs were OK, too

It seems that the sum of frame sizes is the main factor:

*  14 x 1920 x 1080 = 29030400 ok
*  15 x 1920 x 1080 = 31104000 some lowmemkills

*  51 x  960 x  540 = 26438400 ok
*  52 x  960 x  540 = 26956800 some lowmemkills

*  99 x  640 x  360 = 22809600 ok
* 101 x  640 x  360 = 23270400 some lowmemkills

So, for the 1920x1080 display my best suggestion is to use less than 100 frames of 640x360 high-quality .jpgs:

`ffmpeg -i ../whatever.mov -r <framerate> -s 640x360 -q 2 "part0/%03d.jpg"`

The `<framerate>` controls how many of the original frames remain (or gets duplicated), use that
to adjust the number of frames below 100. (The actual replay framerate is set in `desc.txt`.)


[//]: # ( vim: set sw=4 ts=4 et: )
