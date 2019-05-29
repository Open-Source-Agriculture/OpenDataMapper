# OpenDataMapper
A general Android App for logging data from a sensor (via Bluetooth) to the phone and geo-locating it with the phones GPS data. 
The current virsion takes a string containing 2 commas that contains 1 Id field and 2 data field. The ID is to be in the form of an interger and the data is to in the form of a float.

eg: `45,7.32,0.12`

Coloured pins are dropped at each location a sample is taken and the colour is based on the first data field.

This App is currently in beta mode.


![alt text](https://github.com/KipCrossing/EMI_Field/blob/master/Cobbity8/Screenshots/OpenDataMapperScreenshot.jpeg)

### To do:

- Fix bluetooth not connected crash error
- Scale colour based on data range