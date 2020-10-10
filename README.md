| <!-- -->    | <!-- -->    |
-|-
[![Actions Status](https://github.com/huntj88/TileGame/workflows/Deploy/badge.svg)](https://github.com/huntj88/TileGame/actions)  |  <a href="https://play.google.com/store/apps/details?id=me.jameshunt.tilegame"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" height=60px /></a>

# App
The custom view has a grid of falling tiles that will always fall down according to gravity (rotate/tilt phone).
If more than 3 (adjustable) tiles of the same color are in a row, then remove the tiles
#### Configurable variables:
    1. numTilesSize:        defualt 8   Number of tiles across the grid is
    2. numTileTypes:        default 3   Number of unique tile types, currently represented with a solid color
    3. numToMatch:          default 3   Number of consecutive tiles of same type to be considered a match
    4. milliBetweenUpdate:  default 16  Number of milliseconds between each state update

# Code
Quick sliding tile experiment app to play around with some state machine concepts.
The Game state itself is represented with immutable data structures.

The keyword `val` and immutable data structures are preferred.

Mutable data structures are only to be used in pure functions that do not manipulate outside state.
The keyword `var` should also only be used in pure functions, except when representing external user input.


# CI/CD setup
When a new commit is added to the master branch a github action for deploying to Google Play is started.
#### Action Steps:
    1. Checkout master branch
    2. Build And run unit tests
    3. Tag with a new version number
    4. Assemble Android app Bundle
    5. Sign the bundle with the release key
    6. upload to Google Play console and publish on internal track

Building and testing also takes place when a pull request is opened

# State Machine
![StateMachine diagram for game state](statemachine.png)

For every 'tick' that happens, the current state is taken as an input,
a new state is computed from the old state, but advanced 1 tick further ahead in time.
