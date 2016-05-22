/*
 * Copyright (c) 2016 Lonneke Scheffer and Wout van Helvoirt
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.gui.StackWindow;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Recursive_TIFF_Opener
 *
 * This class can be used to open all TIFF files in a directory as virtual stack.
 * Each sub-directory in the user selected directory will be searched for any containing TIFF files. The opened virtual
 * stack can be used as input for the 'Process Photon Images' option.
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */
public final class Recursive_TIFF_Opener implements PlugIn {

    /** The directory path given by user. */
    private String dir = "";
    /** A VirtualStack for TIFF files. */
    private VirtualStack vis;
    /** The window height for the VirtualStack, default is 0. */
    private int winHeight = 0;
    /** The window width for the VirtualStack, default is 0. */
    private int winWidth = 0;

    /**
     * Run method gets executed when setup is finished and when the user selects this class via plug-ins in Fiji.
     * This method does most of the work, calls all other methods in the right order.
     *
     * @param ip image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(final String arg) {

        // If arg is about, display help message and quit.
        if (arg.equals("about")) {
            this.showAbout();
            return;
        }

        try {
            // Show prompt where user can select directory to be searched.
            this.dir = new DirectoryChooser("Select TIFF directory").getDirectory();

            // Search directory.
            searchDirectory(this.dir);
            
            // Make new ImagePlus from VirtualStack with chosen directory as name.
            ImagePlus imp = new ImagePlus(this.dir, this.vis);

            // Create new window for stacks and make it visable to user.
            StackWindow sw = new StackWindow(imp);
            sw.setVisible(true);

        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    /**
     * This method searches the user given directory for each Tiff file.
     *
     * When the first file has been found, setVirtualStack is called. For each directory found, searchDirectory is
     * called un till all files are found. Each File is added to the VirtualStack.
     *
     * @param dir File with user selected directory path.
     * @throws java.io.IOException
     */
    public void searchDirectory(String dir) throws IOException {

        // Walk through given directory and do for each TIFF file (also hidden) something.
        Files.walk(Paths.get(dir), Integer.MAX_VALUE).filter((filePath) -> {

            // Check if filePath file is not hidden and is TIFF file.
            return !filePath.toFile().isHidden() && filePath.toString().matches(".*\\.[Tt]+?[Ii]+?[Ff]+?[Ff]?");
        }).forEach(filePath -> {

            // Show status
            IJ.showStatus("Searching for TIFF files...");

            // If tiff file and not hidden, check if VirtualStack set and item to VirtualStack.
            if (this.vis != null) {
                this.vis.addSlice(filePath.toString().replace(this.dir, ""));

            // If VirtualStack not yet set, set it (Only the first found TIFF file go's in here) and add TIFF file.
            } else if (this.vis == null) {
                setVirtualStack(filePath);
                this.vis.addSlice(filePath.toString().replace(this.dir, ""));
            }
        });
    }

    /**
     * This method set the VirtualStack based on the width and height of image.
     *
     * @param filePath File path with tiff image.
     */
    public void setVirtualStack(Path filePath) {
        ImagePlus imp = new ImagePlus(filePath.toString());

        // Get height and width of ImagePlus and set VirtualStack.
        this.winWidth = imp.getWidth();
        this.winHeight = imp.getHeight();
        this.vis = new VirtualStack(this.winWidth, this.winHeight, ColorModel.getRGBdefault(), this.dir);

        // Close ip when done without annoying popup message.
        imp.changes = false;
        imp.close();
    }

    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Open TIFF Files", "<html>"
            + "<b>This option can be used to open all TIFF files in a directory as virtual stack.</b><br>"
            + "Each sub-directory in the user selected directory will be searched for any containing TIFF files. The "
            + "opened virtual stack can be used as input for the 'Process Photon Images' option.<br><br>"
            + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ, loads an image and calls the plug-in, e.g.
     * after setting breakpoints. Main method will get executed when running this file from IDE.
     *
     * @param args unused
     */
    public static void main(final String[] args) {
        // set the plugins.dir property to make the plug-in appear in the Plugins menu
        Class<?> clazz = Image_Thresholder.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // Open the image sequence
        // IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/kleinbeetjedata");
        // IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
        // IJ.run("Image Sequence...", "open=/Volumes/Bioinf/SinglePhotonData");
        // IJ.run("Image Sequence...", "open=/Users/Wout/Desktop/100100");
        ImagePlus image = IJ.getImage();

        // Only if you use new ImagePlus(path) to open the file
        // image.show();
        // run the plug-in
        IJ.runPlugIn(clazz.getName(), "");
    }
}
