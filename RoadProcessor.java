
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import javax.imageio.ImageIO;

/**
 *
 * Author Philip
 */
//The purpose of this class is to take in an image, and proecss it to give access
//to a processed version of the image as well as points on the image for use in
//deciding how to recact to the current placement on the road of some robot.
public class RoadProcessor {
    private BufferedImage resultingImage;
    private ArrayList<Point> resultingImagePoints;
    
    public RoadProcessor(File imageFile, boolean verbose) throws Exception
    {
        this(ImageIO.read(imageFile), verbose);
    }
    
    //This constructor does all the heavy lifting. It completely processes the image.
    public RoadProcessor(BufferedImage image, boolean verbose) throws Exception
    {
        ARGB[][] imagePixels;
        ARGB[][] modifiedImagePixels;
        
        imagePixels = new ARGB[image.getWidth()][image.getHeight()];
        for(int x = 0; x < image.getWidth(); x++)
            for(int y = 0; y < image.getHeight(); y++)
            {
                imagePixels[x][y] = new ARGB(image.getRGB(x, y));
            }
        
        //Average the image's pixels with a rnage from each pixel of 5.
        modifiedImagePixels = new ARGB[image.getWidth()][image.getHeight()];
        for(int x = 0; x < image.getWidth(); x++)
            for(int y = 0; y < image.getHeight(); y++)
            {
                ARGB average = ARGB.getAverage(pixelsAround(x, y, 5, imagePixels));
                modifiedImagePixels[x][y] = average;
            }
        
        //Figure out the color of the road by looking at the base of the image 
        //and averaging the color of some pixels seen there
        ArrayList<ARGB> roadPoints = new ArrayList<ARGB>();
        roadPoints.addAll(pixelsAround((image.getWidth()/4), image.getHeight()-image.getHeight()/10, 15, modifiedImagePixels));
        roadPoints.addAll(pixelsAround(3*(image.getWidth()/4), image.getHeight()-image.getHeight()/10, 15, modifiedImagePixels));
        ARGB roadColor = ARGB.getAverage(roadPoints);
        
        //Now, try to color in the road! Start coloring from two places, since 
        //often a line on the road divides the road and prevents proper coloring.
        double strength = 1.0;
        for(int t = 0; t < 8; t++)
        {
            if (verbose)
                System.out.println("Coloring with strength = " + strength);
            
            ARGB[][] oldPixels = new ARGB[modifiedImagePixels.length][modifiedImagePixels[0].length];
            for(int r = 0; r < oldPixels.length; r++)
                for(int c = 0; c < oldPixels[r].length; c++)
                    oldPixels[r][c] = modifiedImagePixels[r][c];
            
            boolean[][] visited = new boolean[image.getWidth()+1][image.getHeight()+1];
            colorSegment(new ARGB(255, 255, 0, 255), roadColor, image.getWidth()/3, image.getHeight()-image.getHeight()/20, modifiedImagePixels, visited, strength);
            colorSegment(new ARGB(255, 255, 0, 255), roadColor, 2*image.getWidth()/3, image.getHeight()-image.getHeight()/20, modifiedImagePixels, visited, strength);
            
            resultingImage = copyImage(image);
            resultingImagePoints = makePoints(resultingImage, modifiedImagePixels, new ARGB(255, 255, 0, 255));
            
            if (resultingImagePoints == null)
            {
                resultingImage = null;
                if (verbose)
                    System.out.println("Failure! Retrying...");
                strength = newStrength(strength, modifiedImagePixels, new ARGB(255, 255, 0, 255));
                modifiedImagePixels = oldPixels;
            }
            else
            {
                if (verbose)
                    System.out.println("Done!");
                break;
            }
        }
        
        //If you wanted to view what was being colored, include this line.
        //pushToFile(modifiedImagePixels, new File("out2.png"));
    }
    
    public BufferedImage getResultingImage()
    {
        return resultingImage;
    }
    
    public ArrayList<Point> getResultingImagePoints()
    {
        return resultingImagePoints;
    }
    
    private double newStrength(double oldStrength, ARGB[][] coloredImage, ARGB endColor)
    {
        int total = coloredImage.length*coloredImage[0].length;
        int pink = 0;
        for(int r = 0; r < coloredImage.length; r++)
            for(int c = 0; c < coloredImage[r].length; c++)
                if (coloredImage[r][c].equals(endColor))
                    pink++;
        return (pink/(double)total > 0.5) ? oldStrength-0.1 : oldStrength+0.1;
    }
    
    private void pushToFile(ARGB[][] pixels, File out)
    {
        BufferedImage img = new BufferedImage(pixels.length, pixels[0].length, BufferedImage.TYPE_INT_ARGB);
        for(int x = 0; x < pixels.length; x++)
        {
            for(int y = 0; y < pixels[x].length; y++)
            {
                img.setRGB(x, y, pixels[x][y].getAwtPixel());
            }
        }
        try
        {
            ImageIO.write(img, "png", out);
        }
        catch (Exception exc)
        {
        
        }
    }
    
    // Marks points along the road and draws them onto img. The points represent
    // the average X point of all pixels matching roadColor along 30 horizontal lines.
    // If there isn't much pink data on a horizontal line, it is skipped. Returns null
    // if failure occurred, meaning that there's an increase in coloring from bottom to top.
    
    private ArrayList<Point> makePoints(BufferedImage img, ARGB[][] coloredPixels, ARGB roadColor)
    {
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        ArrayList<Point> pts = new ArrayList<Point>();
        int last = Integer.MAX_VALUE;
        final int skip = img.getHeight()/30;
        for(int y = img.getHeight()-skip; y > 0; y-=skip)
        {
            //Look for dat road and get average x
            int pointsHit = 0;
            int xsum = 0;
            for(int x = 0; x < img.getWidth(); x++)
            {
                if (coloredPixels[x][y].equals(roadColor))
                {
                    xsum += x;
                    pointsHit++;
                }
            }
            if (pointsHit-(0.10*last) > last)
            {
                //Failure occurred!
                return null;
            }
            else
            {
                last = pointsHit;
            }
            if (pointsHit <= 100)
                continue;
            int avgX = xsum/pointsHit;
            pts.add(new Point(avgX, y, 0));
            
            //Draw the point on the BufferedImage!
            g.fillOval(avgX-2, y-2, 10, 10);
        }
        return pts;
    }
    
    //Returns a new BufferedImage that is a copy of image.
    private BufferedImage copyImage(BufferedImage image){
        BufferedImage b = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g = b.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return b;
    }
    
    //Will attempt to color a segment of the image from (startX, startY) from roadColor to endColor. 
    private void colorSegment(ARGB endColor, ARGB roadColor, int startX, int startY, ARGB[][] pixels, boolean[][] visited, double strength) throws Exception
    {
        if (strength < 0.2)
        {
            throw new Exception("Critical failure! Cannot see!");
        }
        
        ARGB[][] pixelsOld = new ARGB[pixels.length][pixels[0].length];
        for(int x = 0; x < pixels.length; x++)
            for(int y = 0; y < pixels[x].length; y++)
                pixelsOld[x][y] = pixels[x][y];
        
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        Point start = new Point(startX, startY, 0);
        Queue<Point> q = new LinkedList<Point>();
        
        
        q.add(start);
        visited[start.x][start.y] = true;
        pixels[start.x][start.y] = endColor;
        
        while(!q.isEmpty())
        {
            Point point = q.poll();
            
            for(int d = 0; d < dx.length; d++)
            {
                int newX = point.x+dx[d];
                int newY = point.y+dy[d];
                if (newX >= 0 && newX < pixels.length && newY >= 0 && newY < pixels[newX].length && !visited[newX][newY])
                {
                    //ARGB.rgbDistance(matchedPixels[newX][newY], roadColor) < 20 || 
                    if (ARGB.rgbDistance(pixels[newX][newY], roadColor) < 25*strength || 
                            (ARGB.rgbDistance(pixels[newX][newY], pixelsOld[point.x][point.y]) < 1*strength && ARGB.rgbDistance(pixels[newX][newY], roadColor) < 35*strength) || 
                            (ARGB.shadeDistance(pixels[newX][newY], roadColor) < 10*strength && ARGB.rgbDistance(pixels[newX][newY], roadColor) < 600*strength))
                    {
                        q.add(new Point(newX, newY, point.dist+1));
                        pixels[newX][newY] = endColor;
                        visited[newX][newY] = true;
                    }
                }
            }
        }
    }
    
    //Returns the pixels in the form of a list of ARGB objects around the pixel at (sx, sy) within range distance.
    private ArrayList<ARGB> pixelsAround(int sx, int sy, int range, ARGB[][] image)
    {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        ArrayList<ARGB> around = new ArrayList<ARGB>();
        HashSet<Point> visited = new HashSet<Point>();
        //BFS around the source!
        Queue<Point> q = new LinkedList<Point>();
        visited.add(new Point(sx, sy, 0));
        q.add(new Point(sx, sy, 0));
        around.add(image[sx][sy]);
        while(!q.isEmpty())
        {
            Point point = q.poll();
            for(int d = 0; d < dx.length; d++)
            {
                int newX = point.x+dx[d];
                int newY = point.y+dy[d];
                if (!visited.contains(new Point(newX, newY, -1)) && newX >= 0 && newX < image.length && newY >= 0 && newY < image[newX].length && point.dist+1 <= range)
                {
                    //This is a valid pixel
                    visited.add(new Point(newX, newY, -1));
                    q.add(new Point(newX, newY, point.dist+1));
                    around.add(image[point.x][point.y]);
                }
            }
        }
        return around;
    }
    
    private static class ARGB
    {
        int a, r, g, b;
        public ARGB(int a, int r, int g, int b)
        {
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public ARGB(int awtPixel)
        {
            this.a = (awtPixel >> 24) & 0xff;
            this.r = (awtPixel >> 16) & 0xff;
            this.g = (awtPixel >> 8) & 0xff;
            this.b = (awtPixel) & 0xff;
        }
        
        public String toString()
        {
            return "[A: " + a + ", R: " + r + ", G: " + g + ", B: " + b + "]";
        }
        
        public int getAwtPixel()
        {
            int rgb = new Color(r, g, b).getRGB();
            return rgb;
        }
        
        public ARGB getClosest(ArrayList<ARGB> options)
        {
            int bestIndex = 0;
            double distance = rgbDistance(this, options.get(0));
            for(int i = 1; i < options.size(); i++)
            {
                double dist = rgbDistance(this, options.get(i));
                if (dist < distance)
                {
                    bestIndex = i;
                    distance = dist;
                }
            }
            return options.get(bestIndex);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + this.a;
            hash = 97 * hash + this.r;
            hash = 97 * hash + this.g;
            hash = 97 * hash + this.b;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ARGB other = (ARGB) obj;
            if (this.a != other.a) {
                return false;
            }
            if (this.r != other.r) {
                return false;
            }
            if (this.g != other.g) {
                return false;
            }
            if (this.b != other.b) {
                return false;
            }
            return true;
        }
        
        public static double shadeDistance(ARGB p1, ARGB p2)
        {
            int min1 = Math.min(p1.r, Math.min(p1.g, p1.b));
            int min2 = Math.min(p2.r, Math.min(p2.g, p2.b));
            ARGB n1 = new ARGB(p1.a, p1.r-min1, p1.g-min1, p1.b-min1);
            ARGB n2 = new ARGB(p2.a, p2.r-min2, p2.g-min2, p2.b-min2);
            return rgbDistance(n1, n2);
        }
        
        public static double rgbDistance(ARGB p1, ARGB p2)
        {
            return Math.sqrt(Math.pow(p2.r-p1.r, 2) + Math.pow(p2.g-p1.g, 2) + Math.pow(p2.b-p1.b, 2));
        }
        
        public static ARGB getAverage(ArrayList<ARGB> pixels)
        {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            for(ARGB pixel : pixels)
            {
                a += pixel.a;
                r += pixel.r;
                g += pixel.g;
                b += pixel.b;
            }
            a = a/pixels.size();
            r = r/pixels.size();
            g = g/pixels.size();
            b = b/pixels.size();
            return new ARGB(a, r, g, b);
        }
    }
    
    public static class Point
    {
        int x, y, dist;
        public Point(int x, int y, int dist)
        {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + this.x;
            hash = 67 * hash + this.y;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Point other = (Point) obj;
            if (this.x != other.x) {
                return false;
            }
            if (this.y != other.y) {
                return false;
            }
            return true;
        }
        
        public String toString()
        {
            return "(" + (x) + ", " + (y) +")";
        }
    }
    
    public static void main(String[] args)
    {
        Scanner scan = new Scanner(System.in);
        while(true)
        {
            System.out.println("Enter an image file path to process:");
            File file = new File(scan.nextLine());
            try
            {
                RoadProcessor rp = new RoadProcessor(file, false);
                ImageIO.write(rp.getResultingImage(), "png", new File("out.png"));
                System.out.println("The image was processed and the result is stored at " + "out.png");
            }
            catch (Exception exc)
            {
                System.err.println("An issue occurred while processing the image.");
            }
        }
    }
}
