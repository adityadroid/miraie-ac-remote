# MirAIe (Panasonic India) cloud protocol

Shared spec for the macOS and Android clients. Reverse-engineered by the community
(`rkzofficial/miraie-ac`, `miraie-ac-in`); not an official API.

## 1. Login

`POST https://auth.miraie.in/simplifi/v1/userManagement/login`

```json
{ "clientId": "PBcMcfG19njNCL8AOgvRzIC8AjQa", "scope": "an_14214235325",
  "mobile": "+91XXXXXXXXXX", "password": "..." }
```

Use `"email"` instead of `"mobile"` when the username contains `@`.
Mobile numbers are sent with the `+91` country code.

Response 200: `{ "accessToken", "refreshToken", "userId", "expiresIn" }` (seconds).
On any 401 from the REST API or MQTT auth failure, log in again.

All REST calls below send `Authorization: Bearer <accessToken>`.

## 2. Homes and devices

`GET https://app.miraie.in/simplifi/v1/homeManagement/homes`

Returns an array of homes. Use `[0]`:

```
homeId
spaces[].devices[]: { deviceId, deviceName, topic: ["<base>"] }
```

Topics per device: `<base>/control` (publish), `<base>/status` and
`<base>/connectionStatus` (subscribe).

## 3. Device status

`GET https://app.miraie.in/simplifi/v1/deviceManagement/devices/{deviceId}/mobile/status`

| key      | meaning                      | values                                         |
|----------|------------------------------|------------------------------------------------|
| `ty`     | device type                  | `"AC"`                                         |
| `onlineStatus` | online                 | `"true"` / `"false"`                           |
| `ps`     | power                        | `"on"` / `"off"`                               |
| `acmd`   | mode                         | `cool` `auto` `dry` `fan` `heat`               |
| `actmp`  | set temperature (°C)         | string or number, 16–30, 1° steps             |
| `rmtmp`  | room temperature             | see quirk below                                |
| `acfs`   | fan speed                    | `auto` `quiet` `low` `medium` `high`           |
| `acvs`   | vertical swing               | `0` = auto/swing, `1`–`5` fixed positions      |
| `achs`   | horizontal swing             | `0` = auto/swing, `1`–`5` fixed positions      |
| `acem`   | eco                          | `"on"` / `"off"`                               |
| `acpm`   | powerful (boost)             | `"on"` / `"off"`                               |
| `acec`   | clean                        | `"on"` / `"off"`                               |
| `acng`   | nanoe                        | `"on"` / `"off"` (not on all models)           |
| `acdc`   | indoor display               | `"on"` / `"off"`                               |
| `cnv`    | convertible (capacity %)     | `110` `100` `90` `80` `70` `55` `40`; `0` off; `1` model doesn't support it |
| `actm`   | device timers                | `[offAt, onAt]` local-time seconds (see below); `-1` = not set |

**`rmtmp` quirk:** valid values have a fractional part of `.0` or `.5`. Some firmware
sends e.g. `150.27`; when the fraction is anything else, the room temperature is
`round(fraction * 100)` (→ 27).

The MQTT `<base>/status` messages carry the same keys (often only the changed ones):
merge them into the current state.

## 4. MQTT

- Host `mqtt.miraie.in`, port `8883`, TLS (public CA), MQTT 3.1.1
- Username = `homeId`, password = `accessToken`, client id = any unique string
- Keep-alive 60 s; reconnect after 5 s; re-login before reconnecting if auth failed

Control message (QoS 0) on `<base>/control`, base fields plus one or more settings:

```json
{ "ki": 1, "cnt": "an", "sid": "1", "actmp": "23.0" }
```

| action          | fields                                              |
|-----------------|-----------------------------------------------------|
| power           | `"ps": "on" \| "off"`                               |
| temperature     | `"actmp": "23.0"` (string)                          |
| mode            | `"acmd": "cool"`                                    |
| fan speed       | `"acfs": "auto"`                                    |
| vertical swing  | `"acvs": 0` (number)                                |
| horizontal swing| `"achs": 0` (number)                                |
| eco on          | `"acem": "on", "acpm": "off"`                       |
| eco off         | `"acem": "off"`                                     |
| powerful on     | `"acpm": "on", "acem": "off"`                       |
| powerful off    | `"acpm": "off"`                                     |
| convertible     | `"cnv": 80, "acem": "off", "acpm": "off"` (number)  |
| timers          | `"actm": [offAt, onAt]` (both numbers, send both)   |

**Convertible** caps the compressor at a share of rated capacity (110 % "HC" is the
high-capacity boost, 40 % the most frugal). `cnv` is `0` when off. Eco and powerful are
mutually exclusive with it, so turning one of those on resets `cnv` to `0` and vice versa.
When status reports `cnv: 1` (or omits it) the model has no convertible support; hide the control.

**Timers** run on the AC itself, so they fire with every client closed. Each slot holds the
time the AC switches off / on, or `-1` when unset. Always send the full pair: to set the off
timer, keep the current on timer in slot 1. Treat a time already in the past as unset when
displaying it. Source for the field: the Raycast MirAIe extension (`buildTimerPayload`).

**The AC counts timers against its own clock, which can be wrong.** Measured on one unit
(13 Oct 2026): every round trip fitted

    stored = sent + realNow − deviceNow,   deviceNow = 1791590400 (10 Oct 00:00 UTC, not ticking)

i.e. the AC fires `sent − deviceNow` seconds from now, and status reports the real Unix time
it will fire. A plain Unix time for "in 1 hour" was therefore reported ~91 h out (1:23 AM
showed as 8:23 PM, 3 days later). So:

- **Sending:** `sent = target − (realNow − deviceNow)`.
- **Reading:** status `actm` is already real Unix time; no conversion.
- **Calibrating:** `deviceNow = sent + sentAt − stored` from any round trip. The clients start
  with plain Unix time, read status ~5 s later, and if a slot landed more than 60 s off,
  store the measured `deviceNow` and resend once. `ts` in status is real server time and says
  nothing about the AC's clock.
- Resending a slot's *reported* value unchanged moves it (it gets re-offset), so always
  convert both slots from real time.

Status also carries `acsp` (`"none"` when off), most likely sleep mode; its other values are unknown.

Recurring weekly schedules from the MirAIe app are stored in MirAIe's cloud through an
undocumented REST API and are not covered here. Sleep mode also has no known key yet.
Capture a live status payload or the MirAIe app's traffic to find both.
