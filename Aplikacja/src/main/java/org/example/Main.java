import org.example.KinoApp;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new KinoApp().setVisible(true);
        });
    }
}