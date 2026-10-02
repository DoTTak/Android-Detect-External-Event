# InputDetect

Android 앱에 들어오는 터치/마우스 입력이 **실제 손가락·물리 장치**에서 온 것인지, 아니면 **scrcpy, `adb shell input`, Vysor 같은 원격 제어 도구로 주입(inject)된 것**인지 판별하는 실험용 앱입니다.

## 동작 원리

scrcpy나 `adb shell input`은 `MotionEvent`를 직접 만들어 `InputManager.injectInputEvent()`로 밀어 넣습니다. 이런 이벤트는 실제 커널 입력 장치에 묶여 있지 않아서 앱에서 읽을 수 있는 흔적이 남습니다. `InputClassifier`는 이 흔적에 점수를 매겨 판정합니다.

| 신호 | 점수 | 의미 |
|---|---|---|
| `deviceId <= 0` | +100 | 실제 입력 장치 없이 주입됨 |
| `InputDevice.getDevice()`가 `null` | +60 | 해당 ID의 입력 장치가 없음 |
| `InputDevice.isVirtual` | +80 | 가상 입력 장치 |
| 장치명에 `scrcpy`/`uinput`/`uhid`/`vysor`/`anydesk`/`teamviewer` | +60 | 원격 제어 도구로 의심되는 장치명 |
| 터치인데 `touchMajor`/`touchMinor`/`size`가 모두 0 | +15 | 접촉 면적 없음 |

마우스 여부는 `SOURCE_MOUSE`, `TOOL_TYPE_MOUSE`, `buttonState != 0`, 호버 이벤트로 판단합니다.

점수가 **60 이상**이면 주입된 입력으로 보고, 아래 네 가지 중 하나로 판정합니다.

- 🟢 **실제 손가락 터치**
- 🔵 **물리 마우스 (OTG/BT)**
- 🔴 **원격 주입 터치** (scrcpy/adb 등)
- 🔴 **원격 주입 마우스** (scrcpy 마우스 등)

터치 제스처는 `DOWN`부터 `UP`까지 받은 이벤트 중 점수가 가장 높은 결과로 판정하고, 호버·클릭·스크롤 같은 generic motion 이벤트는 받을 때마다 판정합니다.

## 프로젝트 구조

```
app/src/main/java/com/dottak/inputdetect/
├── InputClassifier.kt   # MotionEvent → 판정 (점수, 근거)
└── MainActivity.kt      # 터치/호버 이벤트 수집, 화면 표시, logcat 출력
tools/
└── scrcpy_inject.py     # scrcpy-server 제어 소켓으로 직접 이벤트 주입 (테스트용)
```

## 빌드 및 실행

요구 사항: Android SDK (compileSdk 35), JDK 17, 기기 Android 8.0 (API 26) 이상

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

앱을 실행한 뒤 화면을 터치하거나 커서를 올리면 상단에 판정 결과, 그 아래에 점수와 근거가 표시됩니다.

### 로그 확인

```bash
adb logcat -s INPUT_DETECT
```

이벤트마다 `source`, `deviceId`, `toolType`, `buttonState`, `pressure`, `touchMajor` 등의 원시 값과 판정 결과가 출력되고, 제스처가 끝나면 `GESTURE verdict=...` 줄이 한 번 찍힙니다.

## 테스트

### adb로 주입

```bash
adb shell input tap 500 1500
adb shell input swipe 500 1500 600 1600
```

### scrcpy로 주입

scrcpy 데스크톱 클라이언트로 미러링한 뒤 PC 마우스로 클릭하거나 커서를 올려봅니다. 또는 스크립트로 같은 이벤트를 재현할 수 있습니다.

```bash
python3 tools/scrcpy_inject.py [serial]
```

이 스크립트는 기기에 `scrcpy-server`를 올리고 제어 소켓에 연결해 마우스 호버 → 마우스 왼쪽 클릭 → 손가락 터치를 차례로 주입합니다. `scrcpy`가 설치되어 있어야 하며, 서버 경로는 Homebrew 기준(`/opt/homebrew/share/scrcpy/scrcpy-server`)으로 되어 있습니다.

## 한계

- 휴리스틱 기반이므로 우회가 가능합니다. 예를 들어 `uinput`으로 실제 커널 장치를 만들어 이벤트를 보내면 일반 장치처럼 보일 수 있습니다(장치명 검사로 일부만 잡힘).
- 제조사·기기마다 실제 터치의 `touchMajor`/`size` 값이 다를 수 있습니다.
