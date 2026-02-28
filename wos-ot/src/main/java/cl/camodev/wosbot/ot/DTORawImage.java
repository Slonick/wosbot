package cl.camodev.wosbot.ot;

public class DTORawImage {
    private final byte[] data;
    private final int width;
    private final int height;
    private final int bpp;

    public DTORawImage(byte[] data, int width, int height, int bpp) {
        this.data = data;
        this.width = width;
        this.height = height;
        this.bpp = bpp;
    }

    // Getters
    public byte[] getData() { return data; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getBpp() { return bpp; }
}