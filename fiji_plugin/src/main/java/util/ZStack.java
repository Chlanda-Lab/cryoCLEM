package util;

import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.io.IOException;

public class ZStack extends Series {
    public final ImagePlus imp;

    public ZStack(final Series series, boolean virtual) throws IOException, FormatException {
        super(series);
        // log.debug(String.format("Starting import of new util.ZStack: %s[%d]", series.name, series.index));
        final ImporterOptions options = new ImporterOptions();
        options.setId(series.file.getPath());
        options.clearSeries();
        options.setSeriesOn(series.index, true);
        options.setVirtual(virtual);
        options.checkObsoleteOptions();
        this.imp = BF.openImagePlus(options)[0];
        // log.debug(String.format("Done importing util.ZStack %s[%d]", series.name, series.index));
    }

    private void discard() {
        this.imp.changes = false;
        this.imp.close();
    }
}
