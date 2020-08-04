package util;

import loci.formats.IFormatReader;
import loci.formats.meta.MetadataRetrieve;
import ome.units.quantity.Length;
import ome.xml.model.primitives.Color;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import stitching.CommonFunctions;

import java.io.File;


public class Series {
    public final File file;
    public final String name, mp_title;
    public final int index;
    public final double pos_x, pos_y;
    public final Channel[] channels;

    private Series(File file, String name, String mp_title, int index, double pos_x, double pos_y, Channel[] channels) {
        this.file = file;
        this.name = name;
        this.mp_title = mp_title;
        this.index = index;
        this.pos_x = pos_x;
        this.pos_y = pos_y;
        this.channels = channels;
    }

    Series(Series series) {
        this(series.file, series.name, series.mp_title, series.index, series.pos_x, series.pos_y, series.channels);
    }

    public Series(final File file, final int index, final IFormatReader reader, final MetadataRetrieve retrieve) {
        // log.debug(String.format("Reading metadata for file %s, index=%d", file.getName(), index));
        this.file = file;
        this.index = index;
        this.name = retrieve.getImageName(index).replaceAll("/", "-").replaceAll("\\s+", "_");
        this.mp_title = this.name + "_mp_" + this.index + ".tif";
        reader.setSeries(this.index);
        double[] location;
        try {
            location = CommonFunctions.getPlanePosition(reader, retrieve, this.index, 0, false, false, true);
        } catch (IllegalArgumentException e) {
            // log.error(String.format("getPlanePosition failed for series=[%d] with message:\n%s", index,
                    // e.getMessage()));
            location = new double[]{0, 0};
        }
        // Calibrate X and Y coordinate
        final String dimOrder = reader.getDimensionOrder().toUpperCase();
        Length cal = retrieve.getPixelsPhysicalSizeX(index);
        if (dimOrder.indexOf('X') >= 0 && cal != null && cal.value().doubleValue() != 0)
            this.pos_x = location[0] * 1000000 / cal.value().doubleValue();
        else
            this.pos_x = 0;
        if (dimOrder.indexOf('Y') >= 0 && cal != null && cal.value().doubleValue() != 0)
            this.pos_y = location[1] * 1000000 / cal.value().doubleValue();
        else
            this.pos_y = 0;

        this.channels = new Channel[retrieve.getChannelCount(this.index)];
        for (int c = 0; c < retrieve.getChannelCount(this.index); c++) {
            Color color = retrieve.getChannelColor(this.index, c);
            this.channels[c] = Channel.get_channel(color);
            if (this.channels[c] == null) {
                // log.error(String.format("Unable to initialize channel with RGB color: [%d, %d, %d]", color.getRed(), color.getGreen(), color.getBlue()));
            }
        }
    }
}