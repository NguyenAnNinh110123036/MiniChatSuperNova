package common;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.Normalizer;
import java.util.Base64;
import java.util.regex.Pattern;

public class AvatarUtil {

    // Tạo Avatar hình tròn có kèm chấm trạng thái Online/Offline (nếu showStatus =
    // true)
    public static ImageIcon createCircleAvatar(String base64Img, String fallbackName, int size, boolean showStatus,
            boolean isOnline) {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        BufferedImage srcImg = decodeImage(base64Img);
        if (srcImg != null) {
            Shape clip = new Ellipse2D.Double(0, 0, size, size);
            g2.setClip(clip);
            g2.drawImage(srcImg, 0, 0, size, size, null);
            g2.setClip(null);
        } else {
            Color[] palette = {
                    new Color(0, 104, 255), new Color(40, 167, 69),
                    new Color(253, 126, 20), new Color(111, 66, 193),
                    new Color(220, 53, 69)
            };
            int idx = Math.abs((fallbackName == null ? "U" : fallbackName).hashCode()) % palette.length;
            g2.setColor(palette[idx]);
            g2.fillOval(0, 0, size, size);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
            String letter = (fallbackName == null || fallbackName.isEmpty()) ? "U"
                    : fallbackName.substring(0, 1).toUpperCase();
            FontMetrics fm = g2.getFontMetrics();
            int x = (size - fm.stringWidth(letter)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(letter, x, y);
        }

        if (showStatus) {
            int dotSize = Math.max(10, size / 4);
            int dx = size - dotSize - 1;
            int dy = size - dotSize - 1;
            g2.setColor(Color.WHITE);
            g2.fillOval(dx - 2, dy - 2, dotSize + 4, dotSize + 4);
            g2.setColor(isOnline ? new Color(40, 199, 111) : new Color(160, 165, 175));
            g2.fillOval(dx, dy, dotSize, dotSize);
        }

        g2.dispose();
        return new ImageIcon(canvas);
    }

    // Đọc file ảnh và thu nhỏ để làm Avatar hoặc ảnh gửi qua chat
    public static String encodeImageFile(File file, int maxDim) {
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null)
                return "";
            int w = original.getWidth();
            int h = original.getHeight();
            if (w > maxDim || h > maxDim) {
                double scale = Math.min((double) maxDim / w, (double) maxDim / h);
                w = Math.max(1, (int) (w * scale));
                h = Math.max(1, (int) (h * scale));
            }
            BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, w, h, Color.WHITE, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static BufferedImage decodeImage(String base64) {
        if (base64 == null || base64.isEmpty())
            return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public static String fileToBase64(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int r = fis.read(data, read, data.length - read);
                if (r == -1)
                    break;
                read += r;
            }
            return Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean base64ToFile(String base64, File outFile) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(data);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Bỏ dấu tiếng Việt để tìm kiếm tên bạn bè, tên nhóm và tin nhắn dễ dàng
    public static String removeAccents(String s) {
        if (s == null)
            return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }
}