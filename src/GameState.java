public class GameState {
    private static int bankPoints = 0;
    private static int timerUpgrades = 0; // each adds +5 seconds

    public static synchronized int getBankPoints() { return bankPoints; }
    public static synchronized void addPoints(int pts) { bankPoints = Math.max(0, bankPoints + pts); }
    public static synchronized boolean spendPoints(int pts) {
        if (bankPoints >= pts) { bankPoints -= pts; return true; }
        return false;
    }

    public static synchronized int getTimerUpgrades() { return timerUpgrades; }
    public static synchronized void incrementTimerUpgrade() { timerUpgrades++; }

    public static int baseTimerSeconds() { return 60 + 5 * getTimerUpgrades(); }
}

