import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import javax.swing.*;

public class MainMenuPanel extends JPanel {
    private static final Color BG = Color.decode("#0e0219");
    private final java.util.List<Star> stars = new ArrayList<>();
    private final Random rng = new Random();
    private final Timer animTimer;
    private final JLabel titleLabel = new JLabel();
    private final JButton beginButton = new JButton();
    private final JButton upgradeButton = new JButton();
    private final JButton quitButton = new JButton();

    private final Image titleBase = Resources.icon("sprites/mainTitle.png").getImage();
    private final Image playBase = Resources.icon("sprites/playGame.png").getImage();
    private final Image upgradeBase = Resources.icon("sprites/upgradeMenuButton.png").getImage();
    private final Image exitBase = Resources.icon("sprites/exitGame.png").getImage();

    public MainMenuPanel(Runnable onBegin, Runnable onUpgrade, Runnable onQuit) {
        setLayout(null);
        setBackground(BG);

        // Configure components
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setVerticalAlignment(SwingConstants.CENTER);
        titleLabel.setOpaque(false);
        add(titleLabel);

        makeImageButton(beginButton);
        beginButton.addActionListener(e -> onBegin.run());
        add(beginButton);

        makeImageButton(upgradeButton);
        upgradeButton.addActionListener(e -> onUpgrade.run());
        add(upgradeButton);

        makeImageButton(quitButton);
        quitButton.addActionListener(e -> onQuit.run());
        add(quitButton);

        animTimer = new Timer(33, e -> {
            stepStars();
            repaint();
        });
        animTimer.start();
    }

    private void makeImageButton(JButton b) {
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static class Star {
        float x, y; // 0..1 relative
        float life; // 0..1
        float speed;
        float size; // pixels
    }

    private void stepStars() {
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);
        int spawn = (int) Math.max(1, (w * h) / 50000.0);
        for (int i = 0; i < spawn; i++) {
            if (rng.nextFloat() < 0.35f) {
                Star s = new Star();
                s.x = rng.nextFloat();
                s.y = rng.nextFloat();
                s.life = 0f;
                s.speed = 0.02f + rng.nextFloat() * 0.06f;
                s.size = 2f + rng.nextFloat() * 3f;
                stars.add(s);
            }
        }

        for (Iterator<Star> it = stars.iterator(); it.hasNext();) {
            Star s = it.next();
            s.life += s.speed;
            if (s.life >= 1f) it.remove();
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int titleAreaH = h / 3; // top third
        int margin = Math.max(8, h / 100);

        // Title image scaled to fit within top third with some padding
        Dimension tDim = fit(titleBase, (int) (w * 0.9), (int) (titleAreaH * 0.9));
        titleLabel.setIcon(new ImageIcon(titleBase.getScaledInstance(tDim.width, tDim.height, Image.SCALE_SMOOTH)));
        int tX = (w - tDim.width) / 2;
        int tY = (titleAreaH - tDim.height) / 2;
        titleLabel.setBounds(tX, tY, tDim.width, tDim.height);

        // Remaining area for buttons
        int remainingY = titleAreaH + margin;
        int remainingH = h - remainingY - margin;

        // Scale buttons relative to remaining area
        int btnMaxW = (int) (w * 0.5);
        int btnMaxH = Math.max(40, remainingH / 6);

        Dimension playDim = fit(playBase, btnMaxW, btnMaxH);
        beginButton.setIcon(new ImageIcon(playBase.getScaledInstance(playDim.width, playDim.height, Image.SCALE_SMOOTH)));
        int playX = (w - playDim.width) / 2;
        int playY = remainingY + (int) (remainingH * 0.10);
        beginButton.setBounds(playX, playY, playDim.width, playDim.height);

        Dimension upDim = fit(upgradeBase, btnMaxW, btnMaxH);
        upgradeButton.setIcon(new ImageIcon(upgradeBase.getScaledInstance(upDim.width, upDim.height, Image.SCALE_SMOOTH)));
        int upY = playY + playDim.height + Math.max(margin * 2, btnMaxH / 2);
        upgradeButton.setBounds((w - upDim.width) / 2, upY, upDim.width, upDim.height);

        Dimension exitDim = fit(exitBase, btnMaxW, btnMaxH);
        quitButton.setIcon(new ImageIcon(exitBase.getScaledInstance(exitDim.width, exitDim.height, Image.SCALE_SMOOTH)));
        int gap = Math.max(margin * 2, btnMaxH / 2);
        int exitY = upY + upDim.height + gap;
        quitButton.setBounds((w - exitDim.width) / 2, exitY, exitDim.width, exitDim.height);
    }

    private static Dimension fit(Image img, int maxW, int maxH) {
        int iw = Math.max(1, img.getWidth(null));
        int ih = Math.max(1, img.getHeight(null));
        double s = Math.min(maxW / (double) iw, maxH / (double) ih);
        int w = (int) Math.round(iw * s);
        int h = (int) Math.round(ih * s);
        return new Dimension(Math.max(1, w), Math.max(1, h));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        // sparkling stars
        for (Star s : stars) {
            float t = s.life; // 0..1
            float alpha = (float) (Math.sin(t * Math.PI)); // fade in/out
            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            int x = (int) (s.x * getWidth());
            int y = (int) (s.y * getHeight());

            g.setColor(new Color(255, 255, 255, (int) (a * 0.5)));
            int glow = (int) (s.size * 3);
            g.fillOval(x - glow / 2, y - glow / 2, glow, glow);

            g.setColor(new Color(255, 255, 255, a));
            int core = (int) (s.size);
            g.fillOval(x - core / 2, y - core / 2, core, core);
        }
    }
}
