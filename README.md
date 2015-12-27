# WIP: Night Colors

This is supposed to be a simple Android app that changes the screen colors on a Galaxy Nexus with CM11 during the night to contain less blue and green (inspired by [f.lux](https://justgetflux.com/)).

A primary design goal of this app is to be simple and to not to permanently run in the background.

## Current state

First tests indicate that the app seems to work for me but its software design might need to be improved. Missing features:

* Set the colors (currently fixed values are used)
* Alert the user on errors (e.g. if the used color control interface is not supported on the user's device)

Due to how the color control interface is implemented in CM11 for Galaxy Nexus, the display needs to be on when the colors are set.
When the display is off when at the scheduled time, the app currently starts a background service that waits for the screen to turn on.
This might not be the best design but I couldn't find a better solution (and according to [this Stack Overflow question](http://stackoverflow.com/questions/12830660/managing-a-service-based-on-action-screen-off-action-screen-on-intents) it is the only possible design).
The app should be relatively robust to the background service's being restarted but I did not test this.
Similarly the settings activity might have problems with restarts.
