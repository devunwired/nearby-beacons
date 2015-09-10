# Nearby Beacons

This repository contains a sample beacon client application on Android using Google's [Nearby Messages API](https://developers.google.com/nearby/messages/overview) to observe beacons created with the  [Proximity Beacon API](https://developers.google.com/beacons/proximity/guides).

If you need a simple client to create beacons + attachments, try the [Proximity Manager sample](https://github.com/devunwired/proximity-manager).

# Disclaimer

This repository contains sample code intended to demonstrate the capabilities of the Nearby Messages API. It is not intended to be used as-is in applications as a library dependency—or a stand-alone production application—and will not be maintained as such. Bug fix contributions are welcome, but issues and feature requests will not be addressed.

# Sample Usage

The messages API (and this sample application) uses an API key attached to a Google Developer's Console projects where both the Proximity Beacon API and Nearby Messages API are enabled.

For more information on enabling the API and creating the proper API credentials, follow the beacon API [Getting Started Guide](https://developers.google.com/nearby/messages/android/get-started).

You will need to insert:
- Your own API key into the `AndroidManifest.xml`
- Your own list of Eddystone namespace ids into the `EddystoneScannerService.java` if you wish to enable background scanning

# License

The code supplied here is covered under the MIT Open Source License:

Copyright (c) 2015 Wireless Designs, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.