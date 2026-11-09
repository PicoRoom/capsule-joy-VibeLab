import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.byted.idea.spatial.common.utils.SdfGenerator;

/** Asset packaging with the installed PICO App Identity Configurator SDF generator. */
public class BuildSpatialIcon {
    static final int SIZE = 1024;
    static BufferedImage canvas() { return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB); }
    static void save(BufferedImage image, String path) throws Exception {
        ImageIO.write(image, "png", new File(path));
    }
    static void sdf(BufferedImage image, String path) throws Exception {
        byte[] input = new byte[SIZE * SIZE * 4], output = new byte[input.length];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int c = image.getRGB(x, y), i = (y * SIZE + x) * 4;
            input[i] = (byte)(c >> 16); input[i+1] = (byte)(c >> 8);
            input[i+2] = (byte)c; input[i+3] = (byte)(c >>> 24);
        }
        // Exact 1024px settings used by PICO IconGenerationService.
        SdfGenerator.INSTANCE.generateRaw(input, output, SIZE, SIZE, 32f, 0f, 0f,
            SdfGenerator.RGBFillMode.Distance, 200);
        BufferedImage result = canvas();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int i = (y * SIZE + x) * 4;
            result.setRGB(x, y, ((output[i+3]&255)<<24) | ((output[i]&255)<<16)
                | ((output[i+1]&255)<<8) | (output[i+2]&255));
        }
        save(result, path);
    }
    public static void main(String[] args) throws Exception {
        BufferedImage bg = canvas(), fg = canvas();
        // Solid cream circular base: every pixel center inside the circle is opaque.
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (Math.hypot(x + .5 - 512, y + .5 - 512) <= 512)
                bg.setRGB(x, y, 0xfff5ecda);
        }
        Graphics2D g = fg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(new File("design/icon/foreground-source.png")), 0, 0, SIZE, SIZE, null);
        g.dispose();
        // Enforce the platform's circular crop; generated near-transparent pixels
        // outside the safe area must not become a rectangular spatial silhouette.
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (Math.hypot(x + .5 - 512, y + .5 - 512) > 512)
                fg.setRGB(x, y, 0);
        }
        String res = "app/src/main/res/";
        BufferedImage[] layers = { bg, fg };
        for (int i = 0; i < layers.length; i++) {
            save(layers[i], res + "drawable/icon_3d_layer_" + i + ".png");
            sdf(layers[i], res + "drawable/icon_3d_sdf_" + i + ".png");
        }
        BufferedImage merged = canvas();
        g = merged.createGraphics(); g.drawImage(bg, 0, 0, null); g.drawImage(fg, 0, 0, null); g.dispose();
        save(merged, res + "mipmap-xxxhdpi/ic_spatial_launcher.png");
    }
}
