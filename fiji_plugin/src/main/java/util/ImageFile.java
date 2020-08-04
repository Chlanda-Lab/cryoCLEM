package util;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class ImageFile extends File {
    public final ArrayList<Series> series;
    public final String base_name;

    public ImageFile(String path, boolean move_to_subfolder) throws DependencyException, ServiceException, IOException, FormatException {
        // Move file to subfolder (if required)
        super(move_to_subfolder ? remove_extension(path) + File.separator + new File(path).getName() : path);
        //log.debug(String.format("Loading new image file: %s, subfolder=[%b], read_metadata=[%b]", path, move_to_subfolder, read_metadata));
        final File working_dir = new File(this.getParent());
        this.base_name = remove_extension(this.getName());
        if (working_dir.exists() && working_dir.isFile())
            throw new IOException(String.format("Cannot create subdirectory [%s], there is a file with equal name!", working_dir.getAbsolutePath()));
        else if (!working_dir.exists()) {
            working_dir.mkdirs();
        }
        if (move_to_subfolder) {
            // Move image file into equally named subfolder
            new File(path).renameTo(new File(this.getPath()));
            // For LIF-files, there is also the lifext which needs to be moved
            if (new File(path + "ext").exists()) {
                new File(path + "ext").renameTo(new File(this.getPath() + "ext"));
            }
        }
        //log.debug(String.format("Setup working area:\nworking_dir=%s\nimage path=%s", working_dir.getPath(), this.getPath()));

        final IFormatReader reader = new ChannelSeparator();
        final ServiceFactory factory = new ServiceFactory(); // DependencyException
        final OMEXMLService service = factory.getInstance(OMEXMLService.class); // DependencyException
        final IMetadata meta = service.createOMEXMLMetadata(); // ServiceException
        reader.setMetadataStore(meta);
        reader.setId(this.getPath()); // FormatException
        final MetadataRetrieve retrieve = service.asRetrieve(reader.getMetadataStore());
        this.series = new ArrayList<>(reader.getSeriesCount());

        for (int i = 0; i < reader.getSeriesCount(); i++) {
            this.series.add(new Series(this, i, reader, retrieve));
        }
        reader.close();
    }

    public Collection<String> unique_series() {
        return series.stream().map(s -> s.name).distinct().collect(Collectors.toList());
    }

    public Channel[] channels(String series_name) {
        // The channels should all be the same, so I'll just take the first one that matches
        for (Series s : series)
            if (s.name.equals(series_name))
                return s.channels;
        return null;
    }

    private static String remove_extension(final String path) {
        final int last = path.lastIndexOf(".");
        return last >= 1 ? path.substring(0, last) : path;
    }

}


