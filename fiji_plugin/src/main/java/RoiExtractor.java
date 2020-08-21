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
import util.ImageFile;
import util.ZStack;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(type = Command.class, menuPath = "Plugins>CryoCLEM>Get ZStacks from Stitch")
public class RoiExtractor implements Command {
    private final ExecutorService thread_pool = Executors.newWorkStealingPool();
    private final ExecutorService read_pool = Executors.newFixedThreadPool(1);
    private final ExecutorService write_pool = Executors.newFixedThreadPool(1);

    @Parameter
    RoiManager rm;
    // Parameters
    @Parameter
    private LogService log;
    @Parameter(label = "Original image file")
    private File original_file;
    @Parameter(label = "Stitched image file")
    private File stitched_file;
    @Parameter(label = "ROI file")
    private File roi_file;
    @Parameter(label = "Output directory", style = "directory")
    private File output_dir;

    @Override
    public void run() {
        log.setLevel(LogLevel.INFO);

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
                                                      /* Fix for issue #1:
                                                      * If you click anywhere but a ROI number, no ROI is selected.
                                                      * Then, getSelectedRoisAsArray() returns all ROIs, and all
                                                      * ROIs would then be exported. We don't want that.
                                                       */
                                                      if (rois != null && rois.length != 1)
                                                          return;
                                                      for (Roi roi : rois) {
                                                          Matcher matcher = series_num_regex.matcher(roi.getName());
                                                          if (matcher.find()) {
                                                              final int series_index = Integer.parseInt(matcher.group(0));
                                                              final int roi_index = rm.getRoiIndex(roi) + 1;
                                                              log.info(String.format("Queued extraction of ROI %d", roi_index));
                                                              thread_pool.submit(() -> {
                                                              try {
                                                                  final File base_path = new File(
                                                                          output_dir.getPath() + File.separator +
                                                                          image_file.base_name + "_zstack_" + roi_index + File.separator +
                                                                          image_file.base_name + "_zstack_" + roi_index);
                                                                  // If the file doesn't exist, check if the parent directory exists. If not, create it. If that fails, don't do anything
                                                                  if (new File(base_path.getParent()).isDirectory() || new File(base_path.getParent()).mkdirs()) {
                                                                      final Future<ZStack> future_zstack = read_pool.submit(() -> {
                                                                          log.info("Now reading ROI " + roi_index);
                                                                          return new ZStack(image_file.series.get(series_index), false);
                                                                      });
                                                                      ZStack zstack = future_zstack.get();
                                                                      write_pool.submit(() -> {
                                                                          log.info("Now writing ROI " + roi_index);
                                                                          ImagePlus[] channels = ChannelSplitter.split(zstack.imp);
                                                                          for (int c = 0; c < channels.length; c++) {
                                                                              final File path = new File(base_path.getPath() + "_channel_" + c + ".tif");
                                                                              if (path.exists() || !new FileSaver(channels[c]).saveAsTiff(path.getPath())) {
                                                                                  log.error(String.format("Error saving to " + path.getPath()));
                                                                              }
                                                                          }
                                                                      });
                                                                  }
                                                              } catch (ExecutionException e) {
                                                                  log.error(e.getMessage());
                                                                  return;
                                                              } catch (InterruptedException e) {
                                                                  log.error("Reading/writing was interrupted!\n" + e.getMessage());
                                                                  return;
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

