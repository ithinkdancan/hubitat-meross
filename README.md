<span align="center">

# Hubitat Meross

**[BETA]** Unofficial Hubitat Drivers for Meross Smart Devices

</span>

## Currently Supports

- MSS120 - Smart WiFi Plug (2 Channel)
- MSS620 - Smart WiFi Indoor/Outdoor Plug (2 Channel)
- MSS110 - Smart Plug Mini
- MSG200 - Smart WiFi Garage Door Opener
- MDP100 - Smart WiFi Indoor/Outdoor Dimmer Plug

## Authorization & Configuration

1. If you're setting this plug up fresh, make sure you go through the
   typical Meross app for initial setup.

2. You will also have to obtain some information that the Meross mobile
   app uses in its HTTP request headers. See [How To Get Credentials](https://github.com/donavanbecker/homebridge-meross/wiki/Getting-Credentials) for more details.

## Generating a key for MSG200 - Smart WiFi Garage Door Opener

Included in the repo is a python scripts for generating keys for the latest garage door firmware taken from [meross-api](https://github.com/bapirex/meross-api/blob/master/login.py). Run the script with python and copy the values into your hubitat config

```
python3 ./login.py
```

If you are missing modules you can install them with pip:

```sh
python3 -m pip install requests
```

## Generating a key using Chrome

If you're not familiar with python you can also generate a key using chrome or any other modern browser using the [included JS script](./login.js).

1. Go to https://iot.meross.com/v1/Auth/login in your browser
1. Right click on the page, click inspect and locate the console in Chrome Dev Tools
1. Paste the script into the console
1. Update USERNAME and PASSWORD strings in the last line. Hit enter.

## Prior Art

Based off [homebridge-meross](https://github.com/donavanbecker/homebridge-meross)
