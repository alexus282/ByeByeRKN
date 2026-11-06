# Сторонние компоненты

## Туннель

| Компонент | Где используется | Лицензия |
| --- | --- | --- |
| [amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android), тег `v3.1.20260814` | исходники в `tunnel/`, с изменениями (см. README) | Apache-2.0, [`tunnel/LICENSE`](tunnel/LICENSE) |
| [amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go) | внутри `libwg-go.so` | MIT, [`licenses/amneziawg-go.txt`](licenses/amneziawg-go.txt) |
| [wireguard-go](https://git.zx2c4.com/wireguard-go) | основа amneziawg-go | MIT, тот же текст |
| [Go](https://go.dev) | среда выполнения внутри `libwg-go.so` | BSD-3-Clause, [`licenses/go.txt`](licenses/go.txt) |

### Готовые библиотеки

`tunnel/src/main/jniLibs/*/libwg-go.so` взяты без изменений из APK официального релиза amneziawg-android
`v3.1.20260814`. SHA-256:

| ABI | SHA-256 |
| --- | --- |
| `arm64-v8a` | `8bd1d044fcf1828924981bb8b1d66571b884c16d2d779de603d5b359d6ed4416` |
| `armeabi-v7a` | `f0f7e76889b4fd19c5f58eb940ece4424aeb1d754426f421c2e8d38472722045` |
| `x86` | `14d137cb95139fae1461ff75215f6dbf2203830d03a6c2d09d0830be0e59ab86` |
| `x86_64` | `05dd7e0df77dda53d070848d8f1b176578629ff0ed0eacbe36f451239637ad7d` |

## Библиотеки Android

Подключаются через Gradle, версии указаны в [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Все распространяются
по лицензии Apache-2.0:

- [AndroidX и Jetpack Compose](https://developer.android.com/jetpack/androidx)
- [Kotlin и kotlinx.coroutines](https://kotlinlang.org)
- [OkHttp](https://square.github.io/okhttp/)
- [Miuix](https://github.com/compose-miuix-ui/miuix)

## Товарные знаки

Cloudflare и WARP являются товарными знаками Cloudflare, Inc. WireGuard является зарегистрированным товарным знаком
Jason A. Donenfeld. Проект не связан с этими компаниями и лицами и не одобрен ими.
