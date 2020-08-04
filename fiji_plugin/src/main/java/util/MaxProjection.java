package util;

import ij.ImagePlus;
import ij.plugin.ZProjector;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

public class MaxProjection extends Series {
    public final ImagePlus imp;

    public MaxProjection(final ZStack zstack) {
        super(zstack);
        // log.debug(String.format("Starting Z-Project of zstack %s[%d]", zstack.name, zstack.index));
        this.imp = ZProjector.run(zstack.imp, "max");
        // log.debug(String.format("Z-Project done of zstack %s[%d]", zstack.name, zstack.index));
    }
}
