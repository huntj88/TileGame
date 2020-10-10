[![Actions Status](https://github.com/huntj88/TileGame/workflows/Deploy/badge.svg)](https://github.com/huntj88/TileGame/actions)

# App 
<a href="https://play.google.com/store/apps/details?id=me.jameshunt.tilegame"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" height=60px /></a>

Quick sliding tile app to play around with some Finite State Machine concepts. 
The game state itself is represented with immutable data structures.
The custom view has a grid of falling tiles that will always fall down according to gravity (rotate/tilt phone).
If more than 3 (adjustable) tiles of the same color are in a row, then remove the tiles

# Finite State Machine
![StateMachine diagram for game state](statemachine.png)

For every tick that happens, the current state is taken as an input,
a new state is computed from the old state, but advanced 1 tick further ahead in time.

# Motivation
Back in 2014 I had written something similar to this in 8 or so months in my spare time. 
That project was the really old project I liked to show people, both because it was pretty polished 
from a usability standpoint, but also because the code was just BAD. Comparing our old terrible code 
was something that I had done a handful of time with friends/coworker.

After one of these occasions of showing my coworkers, I wondered how long it would take to reimplement the 
parts that I struggled with the first time. 

Three days later I had reimplemented the core functionality, and since then i've just been using this app to tinker.

<table>
  <thead>
    <th>Old Implementation</th>
    <th>New Implementation</th>
  </thead>
  <tr>
    <td>
        The old version had probably 60+ mutable variables in a "god" object that resulted in everything 
        getting tangled together with side effects to update these mutable variables. 
        There was very little separation of concerns. Its a wonder I got it working at all.
    </td>
    <td>
        The new version had only a handful of mutable variables and the rest of the logic was driven by an 
        extremely simple Finite State Machine. <b>Now the code is much shorter, and much easier to think about.</b>
    </td>
  </tr>
</table>

#### My initial goal was completed. Since then I have added:
- Fall direction of tiles based on gravity (tilt and rotation of the phone)
- Removed most of the remaining mutable variables and refactor to have the state machine progress with 
  immutable data structures
- Added a CI/CD pipeline to upload release builds to google play

#### Configurable variables:
|Name|Default|Description|
-|-|-
numTilesSize | 8 | Number of tiles across the grid is
numTileTypes | 3 | Number of unique tile types, currently represented with a solid color
numToMatch | 3 | Number of consecutive tiles of same type to be considered a match
milliToSleepFor | 16 | Number of milliseconds to put thread to sleep for
sleepEveryXTicks | 1 | Number of ticks that occurs until thread sleep

# Code Style
- The keyword `val` and immutable data structures are preferred.
- The keyword `var` should only be used in pure functions, except when representing external user input.
- Mutable data structures are only to be used in pure functions that do not manipulate outside state.

# Tests
The Game state is completely decoupled from the UI, which enables us to write unit tests where we 
setup the board in a specific way. Testing is as easy as asserting the expected state of the board 
after the state has been progressed a known number of times.

# CI/CD setup
When a new commit is added to the master branch a github action for deploying to Google Play is started.
#### Releases
To bump and tag a new version, include one of the following text snippets in a commit message. Highest priority wins
- #major
- #minor
- #patch

#### Action Steps:
1. Checkout master branch
2. Build And run unit tests
3. Tag with a new version number
4. Assemble Android app Bundle
5. Sign the bundle with the release key
6. upload to Google Play console and publish on internal track

Building and testing also takes place when a pull request is opened
