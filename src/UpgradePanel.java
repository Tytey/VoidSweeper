import javax.swing.*;
import java.awt.*;

public class UpgradePanel extends JPanel {
    private final JButton menuButton = new JButton();
    private final JLabel pointsLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton upgradeTimerBtn = new JButton("Upgrade Timer (+5s)");
    private final Runnable onBackToMenu;

    private final ImageIcon menuIcon = Resources.icon("sprites/menuButton.png");

    public UpgradePanel(Runnable onBackToMenu) {
        this.onBackToMenu = onBackToMenu;
        setLayout(null);
        setBackground(Color.decode("#1B032F"));

        pointsLabel.setForeground(Color.WHITE);
        pointsLabel.setFont(pointsLabel.getFont().deriveFont(Font.BOLD, 24f));
        add(pointsLabel);

        upgradeTimerBtn.setFont(upgradeTimerBtn.getFont().deriveFont(Font.BOLD, 18f));
        upgradeTimerBtn.addActionListener(e -> {
            int cost = 10; // adjustable cost per upgrade
            if (GameState.spendPoints(cost)) {
                GameState.incrementTimerUpgrade();
                JOptionPane.showMessageDialog(this, "+5s timer purchased!", "Upgrade", JOptionPane.INFORMATION_MESSAGE);
                refresh();
            } else {
                JOptionPane.showMessageDialog(this, "Not enough points.", "Upgrade", JOptionPane.WARNING_MESSAGE);
            }
        });
        add(upgradeTimerBtn);

        makeImageButton(menuButton, menuIcon);
        menuButton.addActionListener(e -> onBackToMenu.run());
        add(menuButton);
    }

    private void makeImageButton(JButton b, ImageIcon icon) {
        b.setIcon(icon);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void refresh() {
        pointsLabel.setText("Points: " + GameState.getBankPoints() + "  |  Timer: " + GameState.baseTimerSeconds() + "s");
        revalidate(); repaint();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int w = getWidth();
        int h = getHeight();

        // Top-right menu button scaled similarly to game
        int targetH = Math.max(24, Math.min(40, h / 20));
        Image base = menuIcon.getImage();
        int iw = Math.max(1, base.getWidth(null));
        int ih = Math.max(1, base.getHeight(null));
        double s = targetH / (double) ih;
        int bw = (int) Math.round(iw * s);
        int bh = (int) Math.round(ih * s);
        Image scaled = base.getScaledInstance(bw, bh, Image.SCALE_SMOOTH);
        menuButton.setIcon(new ImageIcon(scaled));
        menuButton.setBounds(w - bw - 16, 16, bw, bh);

        int centerW = Math.min(600, (int)(w * 0.8));
        int x = (w - centerW) / 2;
        pointsLabel.setBounds(x, h/4, centerW, 40);
        upgradeTimerBtn.setBounds(x, h/4 + 80, centerW, 60);
    }
}

