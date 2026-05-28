# Auto Clicker

An Android auto clicker app built with Kotlin and an `AccessibilityService`.
It lets you create reusable automation flows with tap, swipe, and long press
actions, adjust timing and loop settings, and run them through a floating overlay.

## Support

If you want to support development, use the button below. They mirror the
funding source defined in `.github/FUNDING.yml`.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support-FFDD00?logo=buy-me-a-coffee&logoColor=000000)](https://www.buymeacoffee.com/zororaka)
[![PayPal](https://img.shields.io/badge/PayPal-Donate-00457C?logo=paypal&logoColor=ffffff)](https://paypal.me/RakaWidhiAntoro)

## Features

- Single Target mode with one draggable reticle
- Multi Target mode for sequential automation flows
- Click, swipe, and long press actions
- Custom long press duration with quick presets and manual input
- Loop count and loop delay settings for saved flows
- Per point delay, repeat count, hold duration, and swipe duration control
- Flow editor for creating and editing saved configurations
- History screen for loading, editing, deleting, importing, and exporting flows
- Advanced History tools with search, sorting, folders, pin, and clone support
- JSON backup support with local save and share options
- Google Drive backup option for premium users
- Quick Start Shortcut support for faster access to Single or Multi Target mode
- Floating overlay control while the service is running
- Status notification during execution

## Premium Features

The app may include a one-time premium upgrade through Google Play Billing.

Premium features include:

- Advanced History tools
- Search and sort saved configurations
- Organize saved flows with folders
- Pin important folders or configurations
- Clone saved flows
- Google Drive backup option
- Lifetime premium access for supported premium tools

The main auto clicker features remain focused on tap, swipe, long press,
configuration saving, and floating overlay control.

## Requirements

- Android 7.0+ (`minSdk 24`)
- Android Studio with JDK 11
- Accessibility permission enabled for the app
- Notification permission on Android 13+

## Build

```bash
./gradlew assembleDebug
```

To install the debug build on a connected device:

```bash
./gradlew installDebug
```

## Setup

1. Install the app on your Android device.
2. Open the app and enable the Auto Clicker accessibility service in system settings.
3. Grant any permission prompt that appears, including notification permission on Android 13+.
4. Choose a mode:
   - `Single Target` for one draggable point
   - `Multi Target` for multiple points executed in sequence

## Usage

1. Create a new flow from the main screen or open an existing flow from History.
2. Add one or more points.
3. Configure the action type, delay, repeat count, and duration for each point.
4. Save the flow.
5. Start the overlay and run the flow from the floating control.
6. Use the History tab to reload, edit, delete, import, export, search, sort, or organize flows.

## Shortcuts

The app supports pinned launcher shortcuts for quick access.

Available shortcut options:

- Single Quick Start
- Multi Quick Start

Shortcut availability depends on the device launcher support.

## Import and Export

The app stores flows as JSON and supports multiple export paths:

- Save to a local JSON file
- Share the JSON backup with another app
- Save or share through Google Drive when available

You can also import a previously exported JSON file from the History screen.

## Proprietary Dependencies

Google Play releases may include proprietary Google services such as:

- Google Play Billing
- Google Play Services, when required by Google Play Billing or related Play distribution features

These components are provided under their own licenses and are not covered by
the GNU General Public License. The main application source code remains licensed
under the GPL as described below.

## Branding and Assets

The source code of this project is licensed under the GNU General Public License
v3.0 or later.

However, the app name, logo, icon, screenshots, store listing assets, and other
branding materials are not licensed under the GPL and may not be reused as
branding or trademarks without explicit permission from RRNDev.

## Project Info

- Package name: `id.biz.rrndev.autoclicker`
- Version: `2.0.0`
- Version code: `8`
- Min SDK: `24`
- Target SDK: `36`
- Compile SDK: `36`
- Build system: Gradle Kotlin DSL
- Main language: Kotlin

## License

This project is licensed under the GNU General Public License v3.0 or later.
See [LICENSE](LICENSE) for details.