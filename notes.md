
### initial install
- no wifi
- disable all (updates/notifs/etc.)
- no password
- nb: KEEP "Gesture navigation" -> no bottom bar 

### setup:
~~- bloquer auto-rotation (?)~~
    -> cannot use "restart hack"
- disable wifi
- disable mic
- disable camera
- disable auto-brightness
    - full brightness (?)
- mute ring & notifications
    (ie. press notifs buttons to show settings)
- quick gesture -> disable all
  (nb: KEEP "Gesture navigation" -> no bottom bar)
  don't forget:
    - "Swipe down with 3 fingers" screenshots
    - "Press & hold power menu"
--
- enable developper mode
[- enable USB debugging
-> plug USB now
- enable File transfer
- install app via Android Studio
]()- set as device owner
  ```shell
  # cheat sheet:

  # set app as device owner:
  adb shell dpm set-device-owner pl.mrugacz95.kiosk/.MyDeviceAdminReceiver
  # set app as device owner:
  adb shell dpm remove-active-admin pl.mrugacz95.kiosk/.MyDeviceAdminReceiver
  ```
- allow "Photos and videos" permissions to the kiosk app
`- copy videos
`- put filemanager & kiosk app on the desktop (?)

### last check:
- disable wifi
- airplane mode (?) just to be sure
- disable auto-brightness
    - full brightness (?)
- check "unhid" the correct videos
- launch app once & reboot
