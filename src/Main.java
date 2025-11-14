import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setTitle("minesweeper");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            CardLayout card = new CardLayout();
            JPanel root = new JPanel(card);

            // Root panels
            UpgradePanel upgradePanel = new UpgradePanel(() -> {
                card.show(root, "menu");
                root.requestFocusInWindow();
            });

            Runnable showUpgrades = () -> {
                upgradePanel.refresh();
                card.show(root, "upgrade");
                root.requestFocusInWindow();
            };

            final Runnable[] startGameHolder = new Runnable[1];
            Runnable startGame = startGameHolder[0] = () -> {
                GamePanel game = new GamePanel(
                        () -> { card.show(root, "menu"); root.requestFocusInWindow(); },
                        () -> SwingUtilities.invokeLater(startGameHolder[0]),
                        showUpgrades
                );
                root.add(game, "game");
                card.show(root, "game");
                game.requestFocusInWindow();
            };

            MainMenuPanel menu = new MainMenuPanel(
                    startGame,
                    showUpgrades,
                    () -> System.exit(0)
            );

            root.add(menu, "menu");
            root.add(upgradePanel, "upgrade");
            frame.setContentPane(root);

            // Fullscreen window
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            frame.setVisible(true);
            card.show(root, "menu");
        });
    }
}
