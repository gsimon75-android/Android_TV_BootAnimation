# Android TV BootAnimation

The story began with buying a nice Android TV. As some reverse engineering will be involved, I
wouldn't like to publish the brand, let's just say that it matches the regex `/Ni.*ai/`.

It's quite a good TV by the way, well worth the price. Its built-in speakers are crap, but
you'd buy a decent sound bar to a TV anyway.

As every Android based TV, it requires some time (10-20 seconds) to boot up.
Have you noticed that it's roughly the same time the ancient black-and-white CRT TVs required
to heat up the cathode? Fascinating, how much the science managed to speed thing up, isn't it...

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
just before starting the Android system.

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

Whoa! **If** this script is invoked, it checks for `custom_upgrade/bootanimation.zip` on all attached USB sticks, and uses the first one found to install as aboot animation.

**NOTE** This is the 1st security weakness, as it's basically a backdoor.

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

`./com/cvte/tv/api/CustomUpgrade.java:45:    private static final String PROP_BOOTANIM = "sys.cvt.burn-bootanim";
`

And here we are! That `CustomUpgrade.java` is quite straightforward, here is what it actually does:

1. It catches the `MEDIA_MOUNTED` intent (sent when an USB stick is mounted)
2. Checks if there is a `custom_upgrade/custom_upgrade_cfg.json` on the USB stick

Its format is:
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

3. If one of these fields is present and 1, and a corresponding data file is also present if it makes sense for that command
4. Then the corresponding property will be set to 1

This is will trigger the service, that execute the script, and that will actually do the job.

A nice trip, wasn't it :D ?

Btw, I mentioned the complete lack of authentication before replacing key parts of the system, did I?

Imagine a malicious vandal with a malignantly prepared USB stick going around a TV shop, 'just checking how his favourite movie shows on this and that device', and meanwhile silently overwriting ... who knows what, with what consequences...


## How it turned out for me

Having been in IT for 25 years has made me a bit paranoid, so before any invasive step I backed up whatever I had read access to. Including the readable parts of `/system` and the original `bootanimation.zip` as well.


### First test

Then I grabbed an USB stick, created a folder `custom_upgrade`, snatched a `bootanimation.zip` from the net, created that `.json` file, took a deep breath, and plugged the USB stick into the TV.

The toasts appeared, hooray! Reboot, and indeed the new boot animation was played.

A portrait animation on a landscape TV, but hey, the principle worked.


### Second attempt

Extracted the animation, rotated the images (`for` loops and `gm convert this.jpg -rotate 90 that.jpg` are the tools), modified the `desc.txt`, repacked the .zip, and updated to it.

Nothing at all.

Some experimenting revealed that the .zip mustn't use compression, so it's `zip -0r ../whatever.zip .`

And the landscape animation played well. The quality was crap, mostly because the frames were 590x352 .jpegs, and neither the upscaling nor the jpeg compression did any good to what most probably was crap even in the beginning.

But now I know how to construct my own `bootanimation.zip`, so...


### Third attempt (the almost-fatal one)

So I bought (yes, for real money, all the $2.50 of it) a nice 1280x1080 animation from [videobacks](https://www.videobacks.com), and extracted the frames from it (`ffmpeg -i whatever.mov -q 2 "part0/%03.jpg"`), all 399 of them.

Built the .zip, updated it, rebooted, and...

... and the animation started to play, lagged as hell, sometimes full white frames appeared ...

... and then the TV rebooted and the whole thing started over and over again!

Put the original .zip to the USB stick and tried to re-update it, hoping that the boot process reaches that point in some way, but no luck, just rebooting again and again.


### Is that what's called 'bricked'?

Almost. It's 'only' a boot loop, but the effects are the same: you have a nice 1.5m x 1m x 8cm thing that plays the start of that animation, some visual artifacts, and reboots, but is not responding in any way.

Searching the net for this case revealed that a lot of others have met this fate :(.

Most of the comments started with "Boot your phone into recovery mode by pressing this-and-that keys while powering on..."
Now, the TV does have physical 'Volume Up' and 'Down' keys, even one with 'Standby' on it (it actually powers down the set), none of the combination had
any effect on the boot process.

There are 8 keys, so I tried all 256 combinations. A switchable power extender makes it a lot easier, you can trust my experience on this.

If I just had a serial console, or an alternate boot flash socket... I've been working with embedded Android devices before, so I'm somewhat familiar with this part, so I grabbed a screwdriver... The warranty can't be more void anyway...

Tough luch, evident sign of service connector, or probe pads on the PCB, or covered sockets, or anything. One big SoC the size of half my palm, power regulators, display drivers, etc.

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

Could've known not to trust any unfounded assumptions, but better late than never, lesson learned.

But to do proceed I will definitely need unrestricted root access!


---

Proceed to [rooting](ROOTING.md)

[//]: # ( vim: set sw=4 ts=4 et: )
