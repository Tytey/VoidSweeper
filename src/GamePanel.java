import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

public class GamePanel extends JPanel implements MouseListener, MouseMotionListener {
    private static final int TILE_SIZE = 32;
    private static final Color COLOR_BG = Color.decode("#1B032F");
    private static final Color COLOR_GRID = new Color(0x2a2a2a);
    private static final Color COLOR_COVERED = Color.decode("#1B032F");
    private static final Color COLOR_REVEALED = new Color(0x2F1743); // slightly lighter than BG
    private static final Color COLOR_ZERO = new Color(0x3A2150);    // a touch lighter for 0-adjacent
    private static final Color COLOR_MINE = new Color(0x9b1c1c);
    private static final Color COLOR_EXPLODED = new Color(0xff0000);
    private static final Color COLOR_FLAG = new Color(0xe63946);
    private static final Color COLOR_SEGMENT_OVERLAY = new Color(0x2e7d32);
    private static final Color COLOR_SEGMENT_BORDER = new Color(0x1b5e20);
    private static final Color COLOR_SEGMENT_GRID = new Color(220, 220, 220, 140);

    private final JButton menuButton = new JButton();
    private final Runnable onExitToMenu;
    private final Runnable onPlayAgain;
    private final Runnable onShowUpgrades;

    private int offsetX = 0;
    private int offsetY = 0;

    private int pressX, pressY;
    private int startOffsetX, startOffsetY;
    private boolean dragging = false;
    private int pressedButton = 0;

    private final long seed;
    // cell state bits: 1=revealed, 2=flagged, 4=exploded
    private final HashMap<Long, Byte> states = new HashMap<>();
    private final HashSet<Long> completedSegments = new HashSet<>();
    private int score = 0;
    private int correctFlags = 0;
    private int secondsLeft = 60;
    private final javax.swing.Timer countdown;
    private final java.util.List<Star> stars = new ArrayList<>();
    private final Random rng = new Random();
    private final javax.swing.Timer animTimer;
    private boolean firstClickMade = false;
    private final HashSet<Long> safeOverride = new HashSet<>();
    private final Image flagImg = Resources.icon("sprites/container.png").getImage();
    private final Image blackHoleImg = Resources.icon("sprites/blackHole.png").getImage();
    private final Image blackHoleContainedImg = Resources.icon("sprites/blackHoleContained.png").getImage();
    private final ImageIcon menuIcon = Resources.icon("sprites/menuButton.png");
    private final Image playBase = Resources.icon("sprites/playGame.png").getImage();
    private final Image upgradeBase = Resources.icon("sprites/upgradeMenuButton.png").getImage();

    // Overlay state and components
    private boolean overlayActive = false;
    private boolean overlayTimedOut = false;
    private boolean overlayHitMine = false;
    private String overlayText = "";
    private final JButton overlayPlay = new JButton();
    private final JButton overlayUpgrade = new JButton();
    private final JLabel overlayLabel = new JLabel("", SwingConstants.CENTER);
    private Rectangle overlayBox = new Rectangle();

    public GamePanel(Runnable onExitToMenu) {
        this(onExitToMenu, () -> {}, () -> {});
    }

    public GamePanel(Runnable onExitToMenu, Runnable onPlayAgain, Runnable onShowUpgrades) {
        this.onExitToMenu = onExitToMenu;
        this.onPlayAgain = onPlayAgain;
        this.onShowUpgrades = onShowUpgrades;
        this.seed = new Random().nextLong();

        setBackground(COLOR_BG);
        setLayout(null);

        // countdown before handlers use it
        secondsLeft = GameState.baseTimerSeconds();
        countdown = new javax.swing.Timer(1000, e -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                ((javax.swing.Timer) ((java.awt.event.ActionEvent) e).getSource()).stop();
                showEndMenu(true, false);
            }
            repaint();
        });
        countdown.start();

        // star animation timer (sparser than menu)
        animTimer = new javax.swing.Timer(50, e -> { stepStars(); repaint(); });
        animTimer.start();

        add(menuButton);
        makeImageButton(menuButton, menuIcon);
        menuButton.addActionListener(e -> {
            countdown.stop();
            animTimer.stop();
            onExitToMenu.run();
        });

        addMouseListener(this);
        addMouseMotionListener(this);

        setFocusable(true);

        // Overlay components
        makeImageButton(overlayPlay, new ImageIcon(playBase));
        makeImageButton(overlayUpgrade, new ImageIcon(upgradeBase));
        overlayPlay.addActionListener(e -> onPlayAgain.run());
        overlayUpgrade.addActionListener(e -> onShowUpgrades.run());
        overlayLabel.setForeground(Color.WHITE);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 18f));
        add(overlayPlay);
        add(overlayUpgrade);
        add(overlayLabel);
        overlayPlay.setVisible(false);
        overlayUpgrade.setVisible(false);
        overlayLabel.setVisible(false);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int targetH = Math.max(24, Math.min(40, getHeight() / 20));
        Image base = menuIcon.getImage();
        int iw = Math.max(1, base.getWidth(null));
        int ih = Math.max(1, base.getHeight(null));
        double s = targetH / (double) ih;
        int w = (int) Math.round(iw * s);
        int h = (int) Math.round(ih * s);
        Image scaled = base.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        menuButton.setIcon(new ImageIcon(scaled));

        int x = getWidth() - w - 16;
        int y = 16;
        menuButton.setBounds(x, y, w, h);

        if (overlayActive) {
            int ww = getWidth();
            int hh = getHeight();
            int btnMaxW = (int) (ww * 0.5);
            int btnMaxH = Math.max(40, hh / 6);

            Dimension playDim = fit(playBase, btnMaxW, btnMaxH);
            Dimension upDim = fit(upgradeBase, btnMaxW, btnMaxH);
            int pad = 16;
            int gap = Math.max(20, btnMaxH / 3);
            int boxW = Math.max(playDim.width, upDim.width) + pad * 2;
            int boxH = playDim.height + gap + upDim.height + pad * 2 + 40;
            int bx = (ww - boxW) / 2;
            int by = (hh - boxH) / 2;
            overlayBox = new Rectangle(bx, by, boxW, boxH);

            overlayLabel.setBounds(bx + pad, by + pad, boxW - 2 * pad, 30);

            int playX = bx + (boxW - playDim.width) / 2;
            int playY = by + pad + 30 + Math.max(12, pad);
            overlayPlay.setIcon(new ImageIcon(playBase.getScaledInstance(playDim.width, playDim.height, Image.SCALE_SMOOTH)));
            overlayPlay.setBounds(playX, playY, playDim.width, playDim.height);

            int upX = bx + (boxW - upDim.width) / 2;
            int upY = playY + playDim.height + gap;
            overlayUpgrade.setIcon(new ImageIcon(upgradeBase.getScaledInstance(upDim.width, upDim.height, Image.SCALE_SMOOTH)));
            overlayUpgrade.setBounds(upX, upY, upDim.width, upDim.height);

            overlayPlay.setVisible(true);
            overlayUpgrade.setVisible(true);
            overlayLabel.setVisible(true);
        } else {
            overlayPlay.setVisible(false);
            overlayUpgrade.setVisible(false);
            overlayLabel.setVisible(false);
        }
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private byte getState(int x, int y) {
        return states.getOrDefault(key(x, y), (byte) 0);
    }

    private void setState(int x, int y, byte state) {
        states.put(key(x, y), state);
    }

    private void makeImageButton(JButton b, ImageIcon icon) {
        b.setIcon(icon);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static Dimension fit(Image img, int maxW, int maxH) {
        int iw = Math.max(1, img.getWidth(null));
        int ih = Math.max(1, img.getHeight(null));
        double s = Math.min(maxW / (double) iw, maxH / (double) ih);
        int w = (int) Math.round(iw * s);
        int h = (int) Math.round(ih * s);
        return new Dimension(Math.max(1, w), Math.max(1, h));
    }

    private static long segKey(int sx, int sy) {
        return (((long) sx) << 32) ^ (sy & 0xffffffffL);
    }

    private boolean isRevealed(int x, int y) {
        return (getState(x, y) & 1) != 0;
    }

    private boolean isFlagged(int x, int y) {
        return (getState(x, y) & 2) != 0;
    }

    private boolean isExploded(int x, int y) {
        return (getState(x, y) & 4) != 0;
    }

    private void toggleFlag(int x, int y) {
        byte s = getState(x, y);
        if ((s & 1) != 0) return; // cannot flag revealed
        if ((s & 2) != 0) s &= ~2;
        else s |= 2;
        setState(x, y, s);
        checkSegmentCompletion(Math.floorDiv(x, 10), Math.floorDiv(y, 10));
    }

    private void checkSegmentCompletion(int sx, int sy) {
        long sk = segKey(sx, sy);
        if (completedSegments.contains(sk)) return;

        // Collect flagged positions in this segment
        HashSet<Long> flagged = new HashSet<>();
        int gx0 = sx * 10;
        int gy0 = sy * 10;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int cx = gx0 + x;
                int cy = gy0 + y;
                if (isFlagged(cx, cy)) {
                    flagged.add(key(cx, cy));
                }
            }
        }

        // Collect actual mine positions
        HashSet<Long> mines = new HashSet<>();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int cx = gx0 + x;
                int cy = gy0 + y;
                if (isMineAt(cx, cy)) {
                    mines.add(key(cx, cy));
                }
            }
        }

        if (!mines.isEmpty() && flagged.equals(mines)) {
            // Reveal the entire segment, including mines
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    int cx = gx0 + x;
                    int cy = gy0 + y;
                    byte s = getState(cx, cy);
                    s |= 1; // revealed
                    setState(cx, cy, s);
                }
            }
            completedSegments.add(sk);
            correctFlags += mines.size();
            repaint();
        }
    }

    private boolean isMineAt(int gx, int gy) {
        if (safeOverride.contains(key(gx, gy))) return false;
        long x = gx;
        long y = gy;
        long z = seed ^ (x * 0x9E3779B97F4A7C15L) ^ Long.rotateLeft(y * 0xC2B2AE3D27D4EB4FL, 23);
        z ^= (z >>> 30);
        z *= 0xBF58476D1CE4E5B9L;
        z ^= (z >>> 27);
        z *= 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        // Convert to [0,1) using top 53 bits
        double r = (double) (z >>> 11) / (double) (1L << 53);
        // mine density ~16%
        return r < 0.16;
    }

    private int adjMines(int x, int y) {
        int c = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (isMineAt(x + dx, y + dy)) c++;
            }
        }
        return c;
    }

    private void reveal(int x, int y) {
        if (isRevealed(x, y) || isFlagged(x, y)) return;
        if (!firstClickMade) {
            firstClickMade = true;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    safeOverride.add(key(x + dx, y + dy));
                }
            }
        }
        if (isMineAt(x, y)) {
            showEndMenu(false, true);
            return;
        }

        ArrayDeque<int[]> q = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();
        q.add(new int[]{x, y});
        visited.add(key(x, y));

        int guard = 200_000; // safety guard for very large regions
        while (!q.isEmpty() && guard-- > 0) {
            int[] cur = q.removeFirst();
            int cx = cur[0], cy = cur[1];

            byte s = getState(cx, cy);
            if ((s & 1) != 0 || (s & 2) != 0) continue; // skip revealed or flagged
            s |= 1; // reveal
            setState(cx, cy, s);

            int a = adjMines(cx, cy);
            if (a == 0) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cx + dx, ny = cy + dy;
                        long k = key(nx, ny);
                        if (!visited.contains(k) && !isMineAt(nx, ny)) {
                            visited.add(k);
                            q.add(new int[]{nx, ny});
                        }
                    }
                }
            }
        }
        repaint();
    }

    private int screenToGridX(int sx) {
        int x = sx - offsetX;
        if (x < 0 && x % TILE_SIZE != 0) return (x / TILE_SIZE) - 1;
        return x / TILE_SIZE;
    }

    private int screenToGridY(int sy) {
        int y = sy - offsetY;
        if (y < 0 && y % TILE_SIZE != 0) return (y / TILE_SIZE) - 1;
        return y / TILE_SIZE;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g.setColor(COLOR_BG);
        g.fillRect(0, 0, w, h);

        // draw sparse stars
        for (Star s : stars) {
            float t = s.life;
            float alpha = (float) (Math.sin(t * Math.PI));
            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            int x = (int) (s.x * getWidth());
            int y = (int) (s.y * getHeight());
            g.setColor(new Color(255, 255, 255, (int) (a * 0.4)));
            int glow = (int) (s.size * 3);
            g.fillOval(x - glow / 2, y - glow / 2, glow, glow);
            g.setColor(new Color(255, 255, 255, a));
            int core = (int) (s.size);
            g.fillOval(x - core / 2, y - core / 2, core, core);
        }

        // visible grid bounds
        int gx0 = screenToGridX(0) - 1;
        int gy0 = screenToGridY(0) - 1;
        int gx1 = screenToGridX(w) + 1;
        int gy1 = screenToGridY(h) + 1;

        // draw tiles
        for (int gy = gy0; gy <= gy1; gy++) {
            for (int gx = gx0; gx <= gx1; gx++) {
                int sx = gx * TILE_SIZE + offsetX;
                int sy = gy * TILE_SIZE + offsetY;

                boolean revealed = isRevealed(gx, gy);
                boolean flagged = isFlagged(gx, gy);
                boolean exploded = isExploded(gx, gy);

                if (revealed) {
                    g.setColor(exploded ? COLOR_EXPLODED : (adjMines(gx, gy) == 0 ? COLOR_ZERO : COLOR_REVEALED));
                } else {
                    g.setColor(COLOR_COVERED);
                }
                g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

                g.setColor(COLOR_GRID);
                g.drawRect(sx, sy, TILE_SIZE, TILE_SIZE);

                if (revealed) {
                    if (isMineAt(gx, gy)) {
                        Image img = completedSegments.contains(segKey(Math.floorDiv(gx,10), Math.floorDiv(gy,10))) ? blackHoleContainedImg : blackHoleImg;
                        drawCenteredImage(g, img, sx, sy, TILE_SIZE, TILE_SIZE);
                    } else {
                        int a = adjMines(gx, gy);
                        if (a > 0) {
                            g.setColor(numberColor(a));
                            String s = Integer.toString(a);
                            Font f = g.getFont().deriveFont(Font.BOLD, (float) (TILE_SIZE * 0.55));
                            g.setFont(f);
                            FontMetrics fm = g.getFontMetrics();
                            int tx = sx + (TILE_SIZE - fm.stringWidth(s)) / 2;
                            int ty = sy + (TILE_SIZE - fm.getHeight()) / 2 + fm.getAscent();
                            g.drawString(s, tx, ty);
                        }
                    }
                } else if (flagged) {
                    drawCenteredImage(g, flagImg, sx, sy, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // draw completed segment overlays
        int segX0 = Math.floorDiv(gx0, 10);
        int segY0 = Math.floorDiv(gy0, 10);
        int segX1 = Math.floorDiv(gx1, 10);
        int segY1 = Math.floorDiv(gy1, 10);
        g.setStroke(new BasicStroke(2f));
        for (int sy = segY0; sy <= segY1; sy++) {
            for (int sx = segX0; sx <= segX1; sx++) {
                if (completedSegments.contains(segKey(sx, sy))) {
                    int px = sx * 10 * TILE_SIZE + offsetX;
                    int py = sy * 10 * TILE_SIZE + offsetY;
                    int pw = 10 * TILE_SIZE;
                    int ph = 10 * TILE_SIZE;
                    g.setColor(new Color(COLOR_SEGMENT_OVERLAY.getRed(), COLOR_SEGMENT_OVERLAY.getGreen(), COLOR_SEGMENT_OVERLAY.getBlue(), 60));
                    g.fillRect(px, py, pw, ph);
                    g.setColor(COLOR_SEGMENT_BORDER);
                    g.drawRect(px, py, pw, ph);
                    // checkmark
                    g.setColor(Color.WHITE);
                    g.setFont(g.getFont().deriveFont(Font.BOLD, 18f));
                    g.drawString("\u2713", px + 6, py + 18);
                }
            }
        }

        // Draw stars over revealed tiles only (clip to revealed areas)
        java.awt.geom.Area revealedArea = new java.awt.geom.Area();
        for (int gy = gy0; gy <= gy1; gy++) {
            for (int gx = gx0; gx <= gx1; gx++) {
                if (isRevealed(gx, gy)) {
                    int sx = gx * TILE_SIZE + offsetX;
                    int sy = gy * TILE_SIZE + offsetY;
                    revealedArea.add(new java.awt.geom.Area(new Rectangle(sx, sy, TILE_SIZE, TILE_SIZE)));
                }
            }
        }
        Shape oldClip = g.getClip();
        g.setClip(revealedArea);
        for (Star s : stars) {
            float t = s.life;
            float alpha = (float) (Math.sin(t * Math.PI));
            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            int sx = (int) (s.x * w);
            int sy = (int) (s.y * h);
            g.setColor(new Color(255, 255, 255, (int) (a * 0.35)));
            int glow = (int) (s.size * 3);
            g.fillOval(sx - glow / 2, sy - glow / 2, glow, glow);
            g.setColor(new Color(255, 255, 255, a));
            int core = (int) (s.size);
            g.fillOval(sx - core / 2, sy - core / 2, core, core);
        }
        g.setClip(oldClip);

        // draw 10x10 segment gridlines
        g.setColor(COLOR_SEGMENT_GRID);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        // reuse gx0..gy1 from above
        int startSegX = Math.floorDiv(gx0, 10) * 10;
        int startSegY = Math.floorDiv(gy0, 10) * 10;
        for (int gy = startSegY; gy <= gy1; gy += 10) {
            int yPix = gy * TILE_SIZE + offsetY;
            g.drawLine(gx0 * TILE_SIZE + offsetX, yPix, gx1 * TILE_SIZE + offsetX + TILE_SIZE, yPix);
        }
        for (int gx = startSegX; gx <= gx1; gx += 10) {
            int xPix = gx * TILE_SIZE + offsetX;
            g.drawLine(xPix, gy0 * TILE_SIZE + offsetY, xPix, gy1 * TILE_SIZE + offsetY + TILE_SIZE);
        }
        g.setStroke(old);

        // HUD: time and score
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(10, 10, 160, 36, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.drawString("Time: " + secondsLeft + "s", 20, 28);
        g.drawString("Score: " + computeScore(), 100, 28);

        if (overlayActive) {
            g.setColor(new Color(0,0,0,160));
            g.fillRect(0, 0, getWidth(), getHeight());
            // Overlay box
            g.setColor(new Color(0x25,0x25,0x38));
            g.fillRoundRect(overlayBox.x, overlayBox.y, overlayBox.width, overlayBox.height, 12, 12);
            g.setColor(new Color(255,255,255,60));
            g.drawRoundRect(overlayBox.x, overlayBox.y, overlayBox.width, overlayBox.height, 12, 12);
        }
    }

    private Color numberColor(int n) {
        switch (n) {
            case 1: return new Color(0x1976d2);
            case 2: return new Color(0x388e3c);
            case 3: return new Color(0xd32f2f);
            case 4: return new Color(0x512da8);
            case 5: return new Color(0x5d4037);
            case 6: return new Color(0x00897b);
            case 7: return Color.BLACK;
            case 8: return Color.GRAY;
            default: return Color.BLACK;
        }
    }

    private int computeScore() {
        int y = completedSegments.size();
        int x = correctFlags;
        double s = x * (1 + 0.5 * y);
        return (int)Math.round(s);
    }

    private void showEndMenu(boolean timedOut, boolean hitMine) {
        countdown.stop();
        animTimer.stop();
        int finalScore = computeScore();
        GameState.addPoints(finalScore);
        overlayActive = true;
        overlayTimedOut = timedOut;
        overlayHitMine = hitMine;
        overlayText = (timedOut ? "Time's up!" : "You hit a bomb!") + "  |  Score: " + finalScore;
        overlayLabel.setText(overlayText);
        revalidate();
        repaint();
    }

    private static class Star {
        float x, y; // 0..1
        float life; // 0..1
        float speed;
        float size; // px
    }

    private void stepStars() {
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);
        int spawn = (int) Math.max(1, (w * h) / 60000.0);
        for (int i = 0; i < spawn; i++) {
            if (rng.nextFloat() < 0.25f) {
                Star s = new Star();
                s.x = rng.nextFloat();
                s.y = rng.nextFloat();
                s.life = 0f;
                s.speed = 0.015f + rng.nextFloat() * 0.04f;
                s.size = 2f + rng.nextFloat() * 2f;
                stars.add(s);
            }
        }
        for (Iterator<Star> it = stars.iterator(); it.hasNext();) {
            Star s = it.next();
            s.life += s.speed;
            if (s.life >= 1f) it.remove();
        }
    }

    private void drawCenteredImage(Graphics2D g, Image img, int sx, int sy, int w, int h) {
        if (img == null) return;
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double s = Math.min(w / (double) iw, h / (double) ih);
        int dw = (int) Math.round(iw * s);
        int dh = (int) Math.round(ih * s);
        int dx = sx + (w - dw) / 2;
        int dy = sy + (h - dh) / 2;
        g.drawImage(img, dx, dy, dw, dh, null);
    }

    // Mouse events
    @Override
    public void mousePressed(MouseEvent e) {
        // ignore presses on the menu button area, let it handle
        if (menuButton.getBounds().contains(e.getPoint())) return;
        if (overlayActive) return;
        pressX = e.getX();
        pressY = e.getY();
        startOffsetX = offsetX;
        startOffsetY = offsetY;
        dragging = false;
        pressedButton = e.getButton();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pressedButton == 0 || overlayActive) return;
        int dx = e.getX() - pressX;
        int dy = e.getY() - pressY;
        if (!dragging) {
            if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragging = true;
        }
        if (dragging) {
            offsetX = startOffsetX + dx;
            offsetY = startOffsetY + dy;
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (pressedButton == 0) return;
        if (overlayActive) { pressedButton = 0; dragging = false; return; }
        if (!dragging && !menuButton.getBounds().contains(e.getPoint())) {
            int gx = screenToGridX(e.getX());
            int gy = screenToGridY(e.getY());
            if (SwingUtilities.isLeftMouseButton(e)) {
                reveal(gx, gy);
            } else if (SwingUtilities.isRightMouseButton(e)) {
                toggleFlag(gx, gy);
                repaint();
            }
        }
        pressedButton = 0;
        dragging = false;
    }

    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void mouseMoved(MouseEvent e) { }
}
