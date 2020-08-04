package util;

import ome.xml.model.primitives.Color;

public class Channel {
    public String name;
    public int R, G, B;

    public static Channel[] channels = {new Channel("Red", 255, 0, 0), new Channel("Green", 0, 255, 0),
            new Channel("Blue", 0, 0, 255), new Channel("Cyan", 0, 255, 255), new Channel("Magenta", 255, 0, 255),
            new Channel("Yellow", 255, 255, 0), new Channel("Grays", 255, 255, 255)};

    public Channel(String name, int R, int G, int B) {
        this.name = name;
        this.R = R;
        this.G = G;
        this.B = B;
    }

    public static Channel get_channel(Color color) {
        for (Channel c : channels) {
            if (c.R == color.getRed() && c.G == color.getGreen() && c.B == color.getBlue())
                return c;
        }
        return null;
    }
}
