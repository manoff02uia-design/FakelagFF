# Fake Lag FF

Android app — Free Fire network control tool.

## Tính năng

| Nút | Mô tả |
|-----|-------|
| **FREEZE** | Drop toàn bộ packet → nhân vật đứng im trên server |
| **GHOST** | Drop packet vị trí → di chuyển nhưng server không nhận |
| **TELE** | Buffer packet 800ms rồi xả cùng lúc → teleport |
| Slider | Điều chỉnh độ trễ fake lag (0–500ms) |

## Cơ chế

Dùng Android **VpnService** API (không cần root) để intercept toàn bộ UDP traffic.  
Overlay **TYPE_APPLICATION_OVERLAY** hiển thị trên mọi app kể cả Free Fire.

## Build thủ công

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push lên `main` → tự build → tạo Release với APK đính kèm.

## Quyền cần cấp khi mở lần đầu

1. **VPN** — hệ thống hỏi, bấm OK
2. **Hiển thị trên màn hình** — vào Settings → cấp quyền → quay lại

## Cấu trúc

```
FakeLagFF/
├── app/src/main/java/com/fakelag/ff/
│   ├── MainActivity.java       # UI chính + xin quyền
│   ├── FakeLagVpnService.java  # VPN service, packet queue
│   └── OverlayService.java     # Floating overlay + 3 nút
├── .github/workflows/build.yml # CI: build + release APK
└── ...
```
