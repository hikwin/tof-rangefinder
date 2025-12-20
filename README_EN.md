# ToF Rangefinder [中文](README.md)

A simple and practical mobile measurement toolkit. The core function is based on **Time-of-Flight (ToF)** sensors to provide fast and intuitive distance measurement, without relying on bulky AR components. It also includes a variety of practical daily measurement tools.

## 🎯 Core Function: ToF Distance
Directly accesses the phone's Laser AF / ToF sensor to obtain raw distance data, suitable for quickly measuring short distances (such as height, furniture dimensions, spacing, etc.).

- **Raw Data**: Displays the millimeter-level integer value returned directly by the sensor, without algorithmic smoothing delay.
- **Auxiliary Display**:
    - **Crosshair Adjustment**: Supports dragging the crosshair to select the measurement point, or locking the center.
    - **Freeze Frame**: One-click pause (freeze screen and data), convenient for reading data after measuring in corners where the screen is hard to see.
    - **Countdown Measurement**: Set a delay to automatically lock the data, making single-person operation easier.
- **Environment Adaptation**: Perfectly adapted for Day/Night modes, providing clear contrast.

## 🛠️ Practical Toolbox
In addition to ToF ranging, you can quickly switch to various life measurement gadgets via the sidebar:

- **⚡ Lightning Distance**
  Uses the difference between the speed of light and the speed of sound to estimate the distance of a thunderstorm by detecting the time difference between the lightning flash and the thunder peak.
- **📐 Two-Step Height**
  Based on trigonometric principles, measure the bottom and top of an object respectively, input the holding height to calculate the height of a distant object.
- **📏 Ruler**
  Screen ruler, supports calibration, used for measuring tiny objects.
- **🔄 Protractor**
  Measure object angles via camera perspective or screen touch.
- **⚖️ Bubble Level**
  Detect if a surface is horizontal or vertical.
- **🖼️ Frame Alignment**
  Uses camera preview and gravity sensor to help hang picture frames horizontally.
- **🧭 Compass**
  Minimalist compass, fusing accelerometer and magnetometer data to accurately display real-time azimuth and magnetic field strength.
- **🏔️ Altimeter**
  Dual-mode measurement: Supports barometric sensor for relative altitude (including boiling point estimation), or GPS for absolute altitude and coordinates.

## 📲 Usage Instructions
1. **Hardware Requirements**: ToF ranging requires phone hardware support for ToF / Laser AF sensors. Other tools mainly rely on the accelerometer, magnetometer, and microphone.
2. **Range Hint**: The effective range of ToF sensors is usually between **1cm ~ 5m** (depending on the specific model); values outside this range may be invalid.

## 🔨 Build & Install
Project based on standard Android Gradle build:

- **Build APK**:
  ```shell
  .\gradlew.bat clean assembleDebug
  ```
- **Install & Debug**:
  ```shell
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  ```
