# Photon Imaging #

---------------------

### What is this repository for? ###

* Authors: Lonneke Scheffer & Wout van Helvoirt
* Version: 1.0
* This plug-in for ImageJ is able to process single photon event data, by locating the center point of each photon and create a combined grayscale image with all found photons per pixel mapped to the correct pixel value.

### How do I get set up? ###

* This plug-in requires at least [Java 8](https://www.oracle.com/downloads/index.html) to function.
* Make sure that [Fiji](http://fiji.sc/) is installed on your Windows/Mac/Linux device and that Fiji uses Java 8.

### How do I use this web application? ###

The plug-in (jar file) can be installed in Fiji via 'Plugins>Install PlugIn...'. After you installed the plug-in, restart Fiji.
When correctly installed, you'll now have 'Plugins>Photon Image Processor' available.

** Open TIFF Files **

This option can be used to open all TIFF files in a directory as virtual stack. Each sub-directory in the user selected directory will be searched for any containing TIFF files. The opened virtual stack can be used as input for the 'Process Photon Images' option.

** Process Photon Images **

This option is able to process a stack containing single photon events data and create a combined high resolution image. Each light point within the image (based on user given tolerance value) is being processed as photon. Each photon has a center that can be calculated in a fast or a more accurate way. The available calculations modes are:

* ** Fast ** uses the lightest points found as coordinates for the output image.
* ** Accurate ** improves on fast by calculating the exact center from the light points before creating an output image.
* ** Sub-pixel resolution ** uses the accurate method but outputs a higher resolution image with four times the amount of pixels. This requires a larger amount of images than the other methods.

Photons are being counted and mapped to the correct pixel values to create a 16-bit output image. The output image may be used for the option 'Threshold Photon Count' to remove noise.

** Threshold Photon Count **

This option can be used to filter noise from the output image created by the 'Process Photon Images' option. All pixels will get a new value based on there current value minus the given threshold value.