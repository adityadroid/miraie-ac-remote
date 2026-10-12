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

Sleep mode has no known key yet. Capture a live status payload to find it.
