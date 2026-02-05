package control;

import java.awt.EventQueue;
import java.awt.Font;

import javax.swing.UIManager;

import interfaz.Principal;

public class Main {

    public static void main(String[] args) {
        applyModernStyle();
        EventQueue.invokeLater(() -> {
            try {
                Principal window = new Principal();
                window.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void applyModernStyle() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // ignore
        }
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("Button.font", base);
        UIManager.put("Label.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("TableHeader.font", base.deriveFont(Font.BOLD));
        UIManager.put("Menu.font", base);
        UIManager.put("MenuItem.font", base);
        UIManager.put("ScrollPane.font", base);
        UIManager.put("OptionPane.font", base);
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", base);
    }
}
