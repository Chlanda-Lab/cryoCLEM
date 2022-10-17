import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageConverter;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import util.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Plugin(type = Command.class, menuPath = "Plugins>CryoCLEM>Stitch TileScan")
public class StitchTileScan implements Command {

    @Parameter
    private LogService log;
    @Parameter
    private ThreadService threadService;
    @Parameter(label = "LIF file(s)")
    private File[] files;
    @Parameter(label = "Create subfolder(s)")
    private boolean subfolder;
    @Parameter(label = "Use cross-correlation")
    private boolean use_cross_correlation;
    @Parameter(label = "Write PNG export(s)")
    private boolean png_export;
    @Parameter(label = "Invert X coordinate")
    private boolean invert_x;
    @Parameter(label = "Invert Y coordinate")
    private boolean invert_y;

    @Override
    public void run() {
        log.setLevel(LogLevel.INFO);
        // Prepare all image files, filter out unwanted file extensions
        final ArrayList<ImageFile> image_files = new ArrayList<>();
        for (File f : files) {
            try {
                log.debug("Starting read of image file:\n" + f.getPath());
                image_files.add(new ImageFile(f.getPath(), subfolder));
            } catch (DependencyException | ServiceException | IOException | FormatException e) {
                log.debug("Failed reading image file:\n" + f.getPath());
                log.error(e.getMessage());
            }

        }

        // Ask the user which series she/he would like to import
        ArrayList<StitchingJob> stitches = new ArrayList<>();
        GenericDialog gd = new GenericDialog("Tile Scans to import");
        for (ImageFile image_file : image_files) {
            gd.addMessage(String.format("[%s]", image_file.getName()));
            for (String n : image_file.unique_series()) {
                stitches.add(new StitchingJob(image_file, n));
                gd.addCheckbox(n, false);
            }
        }
        gd.showDialog();
        if (gd.wasCanceled())
            return;

        // Read the user input
        for (StitchingJob stitch : stitches)
            if (gd.getNextBoolean()) {
                try {
                    stitch.run();
                } catch (ExecutionException e) {
                    log.error(String.format("One job has failed!\nFile = [%s]\nutil.Series = [%s]\n", stitch.image_file.base_name, stitch.series_name));
                    for (StackTraceElement el : e.getStackTrace()) {
                        log.error(String.format("%s:%s:%d", el.getClassName(), el.getMethodName(), el.getLineNumber()));
                    }
                } catch (InterruptedException e) {
                    log.error("Interrupted\n" + e.getMessage());
                    return;
                } catch (FileNotFoundException e) {
                    log.error("Didn't find/unable to create tile config file\n" + e.getMessage());
                    continue;
                } catch (UnsupportedEncodingException e) {
                    log.error("Unsupported encoding for tileconfig file\n" + e.getMessage());
                    continue;
                }
            }
    }

    private class StitchingJob {
        final ImageFile image_file;
        final String series_name;
        final File max_projection_dir, tileconfig;
        final Channel[] channels;

        private StitchingJob(ImageFile image_file, String series_name) {
            this.image_file = image_file;
            this.series_name = series_name;
            this.max_projection_dir = new File(this.image_file.getParent() + File.separator + ".max_projections");
            this.tileconfig = new File(this.max_projection_dir.getAbsolutePath() + File.separator + "tileconfig_" + this.series_name + ".txt");
            this.channels = image_file.channels(series_name);
        }

        private void run() throws ExecutionException, InterruptedException, FileNotFoundException, UnsupportedEncodingException {
            log.debug(String.format("Launching new stitching job.\nImage file: %s\nutil.Series name:%s\nMax Projections dir: %s\nTileconfig: %s",
                    this.image_file,
                    this.series_name,
                    this.max_projection_dir,
                    this.tileconfig));

            if (!this.max_projection_dir.exists())
                this.max_projection_dir.mkdirs();

            log.debug("Creating job list");
            final List<Callable<MaxProjection>> jobs = image_file.series.stream()
                    .filter(s -> s.name.equals(series_name))
                    .map(s -> new Callable<MaxProjection>() {
                        @Override
                        public MaxProjection call() throws Exception {
                            log.debug("Called max projection: " + s.mp_title);
                            final MaxProjection mp = new MaxProjection(new ZStack(s, true));
                            new FileSaver(mp.imp).saveAsTiff(max_projection_dir + File.separator + mp.mp_title);
                            return mp;
                        }
                    }).collect(Collectors.toList());

            log.debug("Writing tileconfig file to " + this.tileconfig);
            final PrintWriter writer = new PrintWriter(this.tileconfig, "UTF-8");
            writer.println("dim = 2");
            for (Future<MaxProjection> _mp : threadService.getExecutorService().invokeAll(jobs)) {
                final MaxProjection mp = _mp.get();
                log.debug("Done with max. projection: " + mp.mp_title);
                writer.println(String.format("%s;;(%.6f, %.6f)", mp.mp_title, mp.pos_x, mp.pos_y));
            }
            writer.close();
            String invert_xy = "";
            if (invert_x) {
                invert_xy = "invert_x ";
            }
            if (invert_y) {
                invert_xy += "invert_y ";
            }
            IJ.run("Grid/Collection stitching",
                    String.format("type=[Positions from file] " + "order=[Defined by TileConfiguration] " + "directory=[%s] "
                            + "layout_file=[%s] " + "fusion_method=[Linear Blending] "
                            + "regression_threshold=0.30 " + "max/avg_displacement_threshold=2.50 "
                            + "absolute_displacement_threshold=3.50 " + "add_tiles_as_rois " + (use_cross_correlation ? "compute_overlap " : "ignore_z_stage ")
                            + invert_xy
                            + "subpixel_accuracy " + "computation_parameters=[Save computation time (but use more RAM)] "
                            + "image_output=[Fuse and display]", this.max_projection_dir, tileconfig.getName()));

            final ImagePlus imp = IJ.getImage();
            final RoiManager rm = RoiManager.getRoiManager();

            log.debug("Setting channel colors and saturation");
            imp.setDisplayMode(IJ.COLOR);
            for (int c = 0; c < channels.length; c++) {
                if (channels[c] != null) {
                    imp.setC(c + 1); // Channels in ImagePlus are one-indexed
                    IJ.run(imp, channels[c].name, "");
                    IJ.run(imp, "Enhance Contrast", "saturated=0.35");
                }
            }
            imp.setDisplayMode(IJ.COMPOSITE);

            log.debug("Removing ROIs not associated to a file");
            Pattern unknown = Pattern.compile(".*file=unknown file \\d+$");
            final int[] rois_to_remove = Arrays.stream(rm.getRoisAsArray())
                    .filter(roi -> unknown.matcher(roi.getName()).matches())
                    .mapToInt(roi -> rm.getRoiIndex(roi)).toArray();
            if (rois_to_remove.length > 0) {
                rm.setSelectedIndexes(rois_to_remove);
                rm.runCommand("Delete");
                rm.runCommand("Select All");
            }

            log.debug("Saving ROIset");
            final String _save_path = image_file.getParent() + File.separator + image_file.base_name + "_" + series_name;
            rm.runCommand("Save", _save_path + "_ROIset.zip");
            log.debug("Saving stitched tif");
            new FileSaver(imp).saveAsTiff(_save_path + "_stitch.tif");

            // Export PNG
            if (png_export) {
                log.debug("Creating and saving PNG export");
                new ImageConverter(imp).convertToRGB();
                imp.updateAndDraw();
                new FileSaver(imp).saveAsPng(_save_path + "_stitched.png");
            }

            // Cleanup
            log.debug("Deleting max. projection dir at " + max_projection_dir.getPath());
            File[] contents = max_projection_dir.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    if (f.delete())
                        log.debug("Deleted " + f.getPath());
                    else
                        log.error("Unable to delete file " + f.getPath());
                }
            }
            if (max_projection_dir.delete())
                log.debug("Deleted max. projection dir");
            else
                log.error("Error deleting max. projection dir");
            log.debug("Closing windows and images");
            imp.changes = false;
            imp.close();
            IJ.selectWindow("Log");
            IJ.run("Close");
            rm.close();
        }
    }
}
