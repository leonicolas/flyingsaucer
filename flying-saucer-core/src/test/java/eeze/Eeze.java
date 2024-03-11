/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Patrick Wright
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package eeze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xhtmlrenderer.simple.FSScrollPane;
import org.xhtmlrenderer.simple.Graphics2DRenderer;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.util.XRLog;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import static java.awt.Frame.NORMAL;
import static java.awt.event.InputEvent.ALT_MASK;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;


/**
 * Eeze is a mini-application to test the Flying Saucer renderer across a set of
 * XML/CSS files.
 *
 * @author Who?
 */
public class Eeze {
    private static final Logger log = LoggerFactory.getLogger(Eeze.class);
    
    private List<File> testFiles;
    private JFrame eezeFrame;
    private File currentDisplayed;
    private Action growAction;
    private Action shrinkAction;
    private Action nextDemoAction;
    private Action chooseDemoAction;
    private Action increase_font, reset_font, decrease_font, showHelp, showGrid, saveAsImg, overlayImage;
    private XHTMLPanel html;
    private FSScrollPane scroll;
    private JSplitPane split;
    private ImagePanel imagePanel;
    private boolean comparingWithImage;
    private File directory;
    
    private static final FileFilter HTML_FILE_FILTER = f ->
      f.getName().endsWith(".html") ||
        f.getName().endsWith(".htm") ||
        f.getName().endsWith(".xhtml") ||
        f.getName().endsWith(".xml");
    private ReloadPageAction reloadPageAction;
    private ReloadFileListAction reloadFileList;

    private Eeze() {
    }

    /**
     * Main processing method for the Eeze object
     */
    private void run() {
        buildFrame();
        SwingUtilities.invokeLater(() -> {
            try {
                File fontFile = new File(directory + "/support/AHEM____.TTF");
                if (fontFile.exists()) {
                    html.getSharedContext().setFontMapping("Ahem",
                            Font.createFont(Font.TRUETYPE_FONT, fontFile.toURI().toURL().openStream()));
                }
            } catch (FontFormatException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        testFiles = buildFileList();
        try {
            SwingUtilities.invokeLater(this::showHelpPage);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void parseArgs(String[] args) {
        for (String arg : args) {
            File f = new File(arg);

            if (!f.exists()) {
                showUsageAndExit("Does not exist: " + arg);
            }
            if (!f.isDirectory()) {
                showUsageAndExit("You specified a file, not a directory: " + arg);
            }

            this.directory = f;
        }
        if ( this.directory == null ) {
            showUsageAndExit("Please specify a directory");
        }
    }

    private List<File> buildFileList() {
        try {
            File[] list = directory.listFiles(HTML_FILE_FILTER);
            return list == null ? emptyList() : asList(list);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void buildFrame() {
        eezeFrame = new JFrame("FS Eeze");
        final JFrame frame = eezeFrame;
        frame.setExtendedState(NORMAL);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        SwingUtilities.invokeLater(() -> {
            html = new XHTMLPanel();
            scroll = new FSScrollPane(html);
            frame.getContentPane().add(scroll);
            frame.pack();
            frame.setSize(1024, 768);
            frame.setVisible(true);

            frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    html.relayout();
                }
            });

            nextDemoAction = new NextDemoAction();
            reloadPageAction = new ReloadPageAction();
            chooseDemoAction = new ChooseDemoAction();
            growAction = new GrowAction();
            shrinkAction = new ShrinkAction();

            increase_font = new FontSizeAction(FontSizeAction.INCREMENT, KeyStroke.getKeyStroke(KeyEvent.VK_I, CTRL_MASK));
            reset_font = new FontSizeAction(FontSizeAction.RESET, KeyStroke.getKeyStroke(KeyEvent.VK_0, CTRL_MASK));
            decrease_font = new FontSizeAction(FontSizeAction.DECREMENT, KeyStroke.getKeyStroke(KeyEvent.VK_D, CTRL_MASK));

            reloadFileList = new ReloadFileListAction();
            showGrid = new ShowGridAction();
            showHelp = new ShowHelpAction();
            saveAsImg = new SaveAsImageAction();
            overlayImage = new CompareImageAction();

            frame.setJMenuBar(new JMenuBar());
            JMenu doMenu = new JMenu("Do");
            doMenu.add(reloadPageAction);
            doMenu.add(nextDemoAction);
            doMenu.add(chooseDemoAction);
            doMenu.add(growAction);
            doMenu.add(shrinkAction);
            doMenu.add(increase_font);
            doMenu.add(reset_font);
            doMenu.add(decrease_font);
            doMenu.add(showGrid);
            doMenu.add(saveAsImg);
            doMenu.add(overlayImage);
            doMenu.add(reloadFileList);
            doMenu.add(showHelp);
            doMenu.setVisible(false);
            frame.getJMenuBar().add(doMenu);
        });
    }

    private void switchPage(File file, boolean reload) {
        eezeFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (reload) {
                XRLog.load("Reloading " + currentDisplayed);
                html.reloadDocument(file.toURI().toURL().toExternalForm());
            } else {
                XRLog.load("Loading " + currentDisplayed);
                html.setDocument(file.toURI().toURL().toExternalForm());
            }
            currentDisplayed = file;
            changeTitle(file.toURI().toURL().toString());
            SwingUtilities.invokeLater(() -> {
                imagePanel.imageWasLoaded();
                imagePanel.repaint();
            });
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        } finally {
            eezeFrame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private void showHelpPage() {
        eezeFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            URL help = eezeHelp();
            html.setDocument(help.openStream(), help.toString());
            changeTitle(html.getDocumentTitle());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            eezeFrame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private void resizeFrame(float hdelta, float vdelta) {
        Dimension d = eezeFrame.getSize();
        eezeFrame.setSize((int) (d.getWidth() * hdelta),
                (int) (d.getHeight() * vdelta));
    }

    private void changeTitle(String newPage) {
        eezeFrame.setTitle("Eeze:  " + html.getDocumentTitle() + "  (" + newPage + ")");
    }

    private URL eezeHelp() {
        return this.getClass().getClassLoader().getResource("eeze/eeze_help.html");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            showUsageAndExit("Eeze needs some information to work.");
        }
        Eeze eeze = new Eeze();
        eeze.parseArgs(args);
        eeze.run();
    }

    private static void showUsageAndExit(String error) {
        String sb = String.format("Oops! %s %n %nEeze %n  A frame to walk through a set of XHTML/XML pages with Flying Saucer %n" +
                " %n" +
                " Usage: %n" +
                "    java eeze.Eeze {directory}%n" +
                " %n" +
                " where {directory} is a directory containing XHTML/XML files.%n" +
                " %n" +
                " All files ending in .*htm* are loaded in a list, in alphabetical %n" +
                " order. The first is rendered. Use Alt-h to show keyboard navigation %n" +
                " shortcuts.%n" +
                " %n", error);
        System.out.println(sb);
        System.exit(-1);
    }

    class ImagePanel extends JPanel {
        private static final long serialVersionUID = 1L;

        Image currentPageImg;

        ImagePanel() {
            // intercept mouse and keyboard events and do nothing
            this.addMouseListener(new MouseAdapter() {
            });
            this.addMouseMotionListener(new MouseMotionAdapter() {
            });
            this.addKeyListener(new KeyAdapter() {
            });
            this.setOpaque(false);
        }

        public void setImage(Image i) {
            currentPageImg = i;
            repaint();
        }

        public boolean imageWasLoaded() {
            if (!Eeze.this.comparingWithImage)
                return false;

            currentPageImg = loadImageForPage();
            if (currentPageImg != null) {
                this.setPreferredSize(new Dimension(currentPageImg.getWidth(null), currentPageImg.getHeight(null)));
            }
            return (currentPageImg != null);
        }

        private Image loadImageForPage() {
            Image img = null;
            try {
                File file = currentDisplayed;
                File parent = file.getAbsoluteFile().getParentFile();
                if (parent == null) parent = file;
                File imgDir = new File(parent.getAbsolutePath() + File.separator + "ref-img");
                String name = file.getName().substring(0, file.getName().lastIndexOf(".")) + ".png";
                File target = new File(imgDir.getAbsolutePath() + File.separator + name);
                if (target.exists()) {
                    img = new ImageIcon(target.toURI().toURL()).getImage();
                } else {
                    JOptionPane.showMessageDialog(
                            Eeze.this.eezeFrame,
                            "No stored reference image, use Ctrl-S to create one.",
                            "Overlay Reference Image",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(
                        Eeze.this.eezeFrame,
                        "Error on trying to save image, check stack trace on console.",
                        "Save As Image",
                        JOptionPane.ERROR_MESSAGE
                );
                throw new RuntimeException(e1);
            }
            return img;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            if (currentPageImg == null) {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, (int) this.getPreferredSize().getWidth(), (int) this.getPreferredSize().getHeight());
            } else {
                g2d.drawImage(currentPageImg, 0, 0, currentPageImg.getWidth(null), currentPageImg.getHeight(null), null);

                /* Code if you want to use as a glasspane--with transparency
                Composite oldComp = g2d.getComposite();
                float alpha = 1F;
                Composite alphaComp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
                g2d.setComposite(alphaComp);

                g2d.draw....

                g2d.setComposite(oldComp);
                Toolkit.getDefaultToolkit().beep();
                */
            }
        }
    }

    static final class GridGlassPane extends JPanel {
        private static final long serialVersionUID = 1L;

        private final Color mainUltraLightColor = new Color(128, 192, 255);
        private final Color mainLightColor = new Color(0, 128, 255);
        private final Color mainMidColor = new Color(0, 64, 196);
        private final Color mainDarkColor = new Color(0, 0, 128);

        GridGlassPane() {
            // intercept mouse and keyboard events and do nothing
            this.addMouseListener(new MouseAdapter() {
            });
            this.addMouseMotionListener(new MouseMotionAdapter() {
            });
            this.addKeyListener(new KeyAdapter() {
            });
            this.setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D graphics = (Graphics2D) g;
            BufferedImage oddLine = createGradientLine(this.getWidth(), mainLightColor,
                    mainDarkColor, 0.6);
            BufferedImage evenLine = createGradientLine(this
                    .getWidth(), mainUltraLightColor,
                    mainMidColor, 0.6);

            int height = this.getHeight();
            for (int row = 0; row < height; row = row + 10) {
                if ((row % 2) == 0) {
                    graphics.drawImage(evenLine, 0, row, null);
                } else {
                    graphics.drawImage(oddLine, 0, row, null);
                }
            }
        }


        public BufferedImage createGradientLine(int width, Color leftColor,
                                                Color rightColor, double opacity) {
            BufferedImage image = new BufferedImage(width, 1,
                    BufferedImage.TYPE_INT_ARGB);
            int iOpacity = (int) (255 * opacity);

            for (int col = 0; col < width; col++) {
                double coef = col / (double) width;
                int r = (int) (leftColor.getRed() + coef
                        * (rightColor.getRed() - leftColor.getRed()));
                int g = (int) (leftColor.getGreen() + coef
                        * (rightColor.getGreen() - leftColor.getGreen()));
                int b = (int) (leftColor.getBlue() + coef
                        * (rightColor.getBlue() - leftColor.getBlue()));

                int color = (iOpacity << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(col, 0, color);
            }
            return image;
        }
    }

    /**
     * Action to trigger frame to grow in size.
     *
     * @author Who?
     */
    final class GrowAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        GrowAction() {
            super("Grow Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_G);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, ALT_MASK));
        }

        /**
         * Invoked when an action occurs.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            float increment = 1.1F;
            resizeFrame(increment, increment);
        }
    }

    /**
     * Action to show a grid over the current page
     *
     * @author Who?
     */
    final class ShowGridAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        private boolean on;
        private Component originalGlassPane;
        private final GridGlassPane gridGlassPane = new GridGlassPane();

        ShowGridAction() {
            super("Show Grid");
            putValue(MNEMONIC_KEY, KeyEvent.VK_G);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_G, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (on) {
                eezeFrame.setGlassPane(originalGlassPane);
                gridGlassPane.setVisible(false);
            } else {
                originalGlassPane = eezeFrame.getGlassPane();
                eezeFrame.setGlassPane(gridGlassPane);
                gridGlassPane.setVisible(true);
            }
            on = !on;
        }
    }

    /**
     * Action to show a grid over the current page
     *
     * @author Who?
     */
    final class CompareImageAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        CompareImageAction() {
            super("Compare to Reference Image");
            putValue(MNEMONIC_KEY, KeyEvent.VK_C);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL_MASK));
            imagePanel = new ImagePanel();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comparingWithImage = !comparingWithImage;

            if (comparingWithImage) {
                if (!imagePanel.imageWasLoaded()) {
                    comparingWithImage = false;
                    System.out.println("   but have no image to load");
                    return;
                }
                if (split == null) split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

                split.setLeftComponent(scroll);
                split.setRightComponent(new JScrollPane(imagePanel));
                split.setDividerLocation(eezeFrame.getHeight() / 2);
                eezeFrame.getContentPane().remove(scroll);
                eezeFrame.getContentPane().add(split);

                // HACK: content pane is not repainting unless frame is resized once
                // split is added. This workaround causes a flicker, but only when image
                // is first loaded
                SwingUtilities.invokeLater(() -> {
                    Dimension d = eezeFrame.getSize();
                    eezeFrame.setSize((int) d.getWidth(), (int) d.getHeight() + 1);
                    eezeFrame.setSize(d);
                });
            } else {
                eezeFrame.getContentPane().remove(split);
                eezeFrame.getContentPane().add(scroll);
            }
        }
    }

    /**
     * Action to trigger frame to shrink in size.
     *
     * @author Who?
     */
    final class ShrinkAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        ShrinkAction() {
            super("Shrink Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            float increment = 1 / 1.1F;
            resizeFrame(increment, increment);
        }
    }

    final class ShowHelpAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        ShowHelpAction() {
            super("Show Help Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_H);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showHelpPage();
        }
    }

    final class NextDemoAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        NextDemoAction() {
            super("Next Demo Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_N);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File nextPage = null;
            for (Iterator<File> iter = testFiles.iterator(); iter.hasNext();) {
                File f = iter.next();
                if (f.equals(currentDisplayed)) {
                    if (iter.hasNext()) {
                        nextPage = iter.next();
                        break;
                    }
                }
            }
            if (nextPage == null) {
                // go to first page
                Iterator<File> iter = testFiles.iterator();
                nextPage = iter.next();
            }

            switchPage(nextPage, false);
        }
    }

    final class SaveAsImageAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        SaveAsImageAction() {
            super("Save Page as PNG Image");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                File file = currentDisplayed;
                File parent = file.getAbsoluteFile().getParentFile();
                if (parent == null) parent = file;
                File imgDir = new File(parent.getAbsolutePath() + File.separator + "ref-img");
                if (!imgDir.exists()) {
                    if (!imgDir.mkdir()) {
                        JOptionPane.showMessageDialog(
                                Eeze.this.eezeFrame,
                                "Can't create dir to store images: \n" + imgDir,
                                "Save As Image",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }
                }
                String name = file.getName().substring(0, file.getName().lastIndexOf(".")) + ".png";
                File target = new File(imgDir.getAbsolutePath() + File.separator + name);
                if (target.exists() && JOptionPane.showConfirmDialog(
                        Eeze.this.eezeFrame,
                        "Stored image exists (" + name + "), overwrite?",
                        "Overwrite existing image file?",
                        JOptionPane.YES_NO_OPTION
                ) == JOptionPane.NO_OPTION) return;

                // TODO: our *frame* is sized to 1024 x 768 by default, but when scrollbars are on, we have a smaller
                // area. this becomes an issue when comparing a saved image with one rendered by FS, as fluid layouts
                // may expand to fill the size we pass in to the renderer on the next line; need to figure out the right
                // sizes to use...or maybe resize the frame when the image is loaded.
                BufferedImage image = Graphics2DRenderer.renderToImage(file.toURI().toURL().toExternalForm(), 1024, 768);
                ImageIO.write(image, "png", target);
                Toolkit.getDefaultToolkit().beep();

                if (comparingWithImage) {
                    imagePanel.setImage(image);
                }
            } catch (Exception e1) {
                showMessageDialog(
                        Eeze.this.eezeFrame,
                        "Error on trying to save image, check stack trace on console.",
                        "Save As Image",
                        ERROR_MESSAGE
                );
                log.error("Failed to save image", e1);
            }
        }
    }

    final class ReloadPageAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        ReloadPageAction() {
            super("Reload Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_R);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
           switchPage(currentDisplayed, true);
        }
    }

    final class ChooseDemoAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        ChooseDemoAction() {
            super("Choose Demo Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_C);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File nextPage = (File) JOptionPane.showInputDialog(eezeFrame,
                    "Choose a demo file",
                    "Choose Demo",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    testFiles.toArray(),
                    currentDisplayed);

            switchPage(nextPage, false);
        }
    }

    final class ReloadFileListAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        ReloadFileListAction() {
            super("Reload File List Page");
            putValue(MNEMONIC_KEY, KeyEvent.VK_F);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, ALT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            testFiles = buildFileList();
            currentDisplayed = testFiles.get(0);
            reloadPageAction.actionPerformed(null);
        }
    }

    final class FontSizeAction extends AbstractAction {
        private final int whichDirection;
        static final int DECREMENT = 0;
        static final int INCREMENT = 1;
        static final int RESET = 2;

        FontSizeAction(int which, KeyStroke ks) {
            super("FontSize");
            whichDirection = which;
            putValue(Action.ACCELERATOR_KEY, ks);
        }

        FontSizeAction(float scale, int which, KeyStroke ks) {
            this(which, ks);
            html.setFontScalingFactor(scale);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            switch (whichDirection) {
                case INCREMENT:
                    html.incrementFontSize();
                    break;
                case RESET:
                    html.resetFontSize();
                    break;
                case DECREMENT:
                    html.decrementFontSize();
                    break;
            }
        }
    }
}
