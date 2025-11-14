import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

public class Resources {
    public static ImageIcon icon(String name) {
        // Try classpath root
        URL url = Resources.class.getResource("/" + name);
        if (url != null) {
            return new ImageIcon(url);
        }
        // Try working directory
        File f = new File(name);
        if (f.exists()) {
            return new ImageIcon(f.getAbsolutePath());
        }
        // Try sprites directory at project root
        File fs = new File("sprites/" + name);
        if (fs.exists()) {
            return new ImageIcon(fs.getAbsolutePath());
        }
        // Try under src (for IDE runs)
        File f2 = new File("src/" + name);
        if (f2.exists()) {
            return new ImageIcon(f2.getAbsolutePath());
        }
        // Try under src package folder
        File f3 = new File("src/minesweeper/" + name);
        if (f3.exists()) {
            return new ImageIcon(f3.getAbsolutePath());
        }
        // Fallback: empty icon of some size
        Image img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        return new ImageIcon(img);
    }
}
