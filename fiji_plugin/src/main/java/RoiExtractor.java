import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.ChannelSplitter;
import ij.plugin.frame.RoiManager;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import util.ImageFile;
import util.ZStack;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(type = Command.class, menuPath = "Plugins>CryoCLEM>Get ZStacks from Stitch")
public class RoiExtractor implements Command {
    @Parameter
    private ThreadService thread_pool;
    private final ExecutorService read_pool = Executors.newFixedThreadPool(1);
    private final ExecutorService write_pool = Executors.newFixedThreadPool(1);

    @Parameter
    RoiManager rm;
    // Parameters
    @Parameter
    private LogService log;
    @Parameter(label = "Stitched image file", callback = "stitched_file_changed", persist = false)
    private File stitched_file;
    @Parameter(label = "Original image file", callback = "original_file_changed", persist = false)
    private File original_file;
    @Parameter(label = "ROI file", persist = false)
    private File roi_file;
    @Parameter(label = "Output directory", style = "directory", persist = false)
    private File output_dir;

    private void stitched_file_changed() {
        if (this.stitched_file == null) return;
        // The stitched filename has the format: <LIF basename>_<TileScan>_stitch.tif
        // Splitting at underscores should find us the LIF filename, which we can autofill
        log.debug("Looking for .lif file");
        String stitched = this.stitched_file.getPath();
        for (int i = stitched.indexOf('_'); i > 0 && i < stitched.length(); i = stitched.indexOf('_', i + 1)) {
            File f = new File(stitched.substring(0, i) + ".lif");
            if (f.isFile()) {
                this.original_file = f;
                break;
            }
        }
        // The ROIset file has the format <LIF basename>_<TileScan>_ROIset.zip
        // Replacing _stitched.tif with _ROIset.zip should get us the ROIset filename
        File f = new File(stitched.replace("_stitch.tif", "_ROIset.zip"));
        if (f.isFile()) this.roi_file = f;
        // Autofill output directory: default name is zstacks
        this.output_dir = new File(this.stitched_file.getParent() + File.separator + "zstacks");

    }

    private void original_file_changed() {
        if (this.original_file == null) return;
        final String basename = util.ImageFile.remove_extension(this.original_file.getName());
        // Autofill ROIset file if only one ROIset file is present
        File[] roiset_files = list_files_regex(new File(this.original_file.getParent()), basename + "_.*_ROIset\\.zip");
        if (roiset_files.length == 1) this.roi_file = roiset_files[0];
        // Autofill stitched file if only one stitched file is present
        File[] stitch_files = list_files_regex(new File(this.original_file.getParent()), basename + "_.*_stitch\\.png");
        if (stitch_files.length == 1) this.stitched_file = stitch_files[0];
        // Autofill output directory: default name is zstacks
        this.output_dir = new File(this.original_file.getParent() + File.separator + "zstacks");
    }

    private File[] list_files_regex(File dir, String regex) {
        if (!dir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + dir.getPath());
        final Pattern pattern = Pattern.compile(regex);
        return dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return pattern.matcher(file.getName()).matches();
            }
        });
    }

    @Override
    public void run() {
        log.setLevel(LogLevel.INFO);
        thread_pool.setExecutorService(Executors.newFixedThreadPool(2));

        log.debug("Creating output dir");
        if (!output_dir.exists()) {
            if (!output_dir.mkdirs()) {
                log.error("Unable to create output directory!");
                return;
            }
        }

        log.debug("Opening files");
        final ImagePlus stitched = IJ.openImage(stitched_file.getPath());
        stitched.show();
        rm.runCommand("Open", roi_file.getPath());
        rm.runCommand("Show All with labels");
        final ImageFile image_file;
        try {
            image_file = new ImageFile(this.original_file.getPath(), false);
        } catch (DependencyException | ServiceException | IOException | FormatException e) {
            log.error(e.getMessage());
            return;
        }
        stitched.getCanvas().addMouseListener(new MouseListener() {
                                                  final Pattern series_num_regex = Pattern.compile("(?<=_mp_)\\d+(?=\\.tif)");

                                                  @Override
                                                  public void mouseClicked(MouseEvent mouseEvent) {
                                                      final Roi[] rois = rm.getSelectedRoisAsArray();
                                                      if (rois != null && rois.length != 1) return;

                                                      for (Roi roi : rois) {
                                                          Matcher matcher = series_num_regex.matcher(roi.getName());
                                                          if (matcher.find()) {
                                                              final int series_index = Integer.parseInt(matcher.group(0));
                                                              final int roi_index = rm.getRoiIndex(roi) + 1;
                                                              log.info(String.format("Queued extraction of ROI %d", roi_index));
                                                              thread_pool.getExecutorService().submit(() -> {
                                                                  final File base_path = new File(
                                                                          output_dir.getPath() + File.separator +
                                                                                  //image_file.base_name + "_zstack_" + roi_index + File.separator +
                                                                                  image_file.base_name + "_zstack_" + roi_index);
                                                                  try {
                                                                      ZStack zstack = new ZStack(image_file.series.get(series_index), true);
                                                                      ImagePlus[] channels = ChannelSplitter.split(zstack.imp);
                                                                      for (int c = 0; c < channels.length; c++) {
                                                                          final File path = new File(base_path.getPath() + "_channel_" + c + ".tif");
                                                                          if (path.exists() || !new FileSaver(channels[c]).saveAsTiff(path.getPath())) {
                                                                              log.error(String.format("Error saving to " + path.getPath()));
                                                                          }

                                                                      }
                                                                      log.info("Done writing ROI " + roi_index);
                                                                  } catch (Exception e) {
                                                                      log.error(e.toString());
                                                                  }
                                                              });
                                                          }
                                                      }
                                                  }

                                                  @Override
                                                  public void mousePressed(MouseEvent mouseEvent) {
                                                  }

                                                  @Override
                                                  public void mouseReleased(MouseEvent mouseEvent) {
                                                  }

                                                  @Override
                                                  public void mouseEntered(MouseEvent mouseEvent) {
                                                  }

                                                  @Override
                                                  public void mouseExited(MouseEvent mouseEvent) {
                                                  }
                                              }
        );
    }
}

