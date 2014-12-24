# README #

Mods for Huifei/RoadMaster/Klyde/GS/JY android 4.2.2/4.4.2/4.4.4 headunits running MTC apps

* Change preferred music app from MTCMusic to something else
* Change preferred video app from MTCMovie to something else
* Ability to override the default mode switch behavior to include only the sources you want (in addition to Music)
* Overridden mode switching can add your selected Nav app (in Settings->Gps) to the rotation
* Ability to apply Loudness on boot (newer system images persist this setting)
* Time-based dimmer and adjustable brightness level when time is outside dimming period
* Ability to automatically set dimming start and end times based on current date, timezone, and GPS location (no data connection required)
* Ability to learn your radio presets and re-apply them if/when the presets get lost (usually on hard-reset)
* 3 replacement launchers for user-selected Music, Videos, and Radio apps for non-SWC vehicles
* Preference to allow all BT OBD adapters to pair with headunit
* Intents to start and stop the XMTC service
* Preference to override the stock volume OSD and place volume level in the statusbar
* Launcher to manually start the reverse camera view (BackView) -- disabled for now
* [Screen Filter](http://repo.xposed.info/module/com.tonymanou.screenfilter) integration for ultimate dimming control

New repository for migrated Android Studio project

### Install ###

* Download Xposed Framework, install it (might need to sideload it)
* Enable app_process, reboot
* Install this module, either from repo or sideload
* Enable module in Xposed Installer
* Run module settings and select your media player
* Reboot
