# WIP: Night Colors

This is supposed to be a simple Android app that changes the screen colors on a Galaxy Nexus with CM11 during the night to contain less blue and green (inspired by [f.lux](https://justgetflux.com/)).

A primary design goal of this app is to be simple and to not to permanently run in the background.

## Current state

First tests indicate that the app seems to work for me but its software design might need to be improved. Missing features:

* Set the colors (currently fixed values are used)
* Alert the user on errors (e.g. if the used color control interface is not supported on the user's device)
* Handle situations where the start time is before the end time
* Handle all situations where the app and especially its services are killed

Due to how the color control interface is implemented in CM11 for Galaxy Nexus, the display needs to be on when the colors are set. When the display is off when at the scheduled time, the app currently starts a background service that waits for the screen to turn on. This might not be the best design and most probably fails when the background service is killed or restarted.
