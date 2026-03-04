package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;


public class KinoApp extends JFrame {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/kino2?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    // Paleta
    private static final Color BG         = new Color(8, 10, 16);
    private static final Color PANEL_BG   = new Color(18, 22, 36);
    private static final Color ACCENT     = new Color(0, 170, 255);
    private static final Color ACCENT_DARK= new Color(0, 130, 220);
    private static final Color TEXT       = new Color(230, 235, 245);
    private static final Color TEXT_MUTED = new Color(160, 170, 190);
    private static final Color BORDER     = new Color(40, 50, 80);
    private static final Color DANGER     = new Color(220, 60, 60);

    private JTable tabela;
    private DefaultTableModel model;
    private JButton btnAnulujCala;
    private JLabel statusLabel;
    private Timer timer;

    public KinoApp() {
        setTitle("Kino – Rezerwacje");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1024, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(12, 12));

        // Nagłówek
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(0, 0, 0));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 5, 0, ACCENT));

        JLabel title = new JLabel("KINO REZERWACJE", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 38));
        title.setForeground(ACCENT);
        title.setBorder(new EmptyBorder(25, 0, 20, 0));
        headerPanel.add(title, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Tabela
        model = new DefaultTableModel(
                new String[]{"ID seansu", "Tytuł filmu", "Data", "Godzina", "Zajętych miejsc"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        tabela = new JTable(model);
        tabela.setBackground(PANEL_BG);
        tabela.setForeground(TEXT);
        tabela.setGridColor(BORDER);
        tabela.setSelectionBackground(ACCENT);
        tabela.setSelectionForeground(Color.WHITE);
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tabela.setShowGrid(true);
        tabela.setIntercellSpacing(new Dimension(10, 8));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < model.getColumnCount(); i++) {
            tabela.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 2, true));
        scroll.setBackground(PANEL_BG);
        scroll.getViewport().setBackground(PANEL_BG);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                scroll.getBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        add(scroll, BorderLayout.CENTER);

        // Dolny panel
        JPanel south = new JPanel(new BorderLayout(0, 15));
        south.setBackground(BG);
        south.setBorder(new EmptyBorder(15, 20, 25, 20));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 15));
        btnPanel.setBackground(BG);

        JButton btnRefresh = stworzPrzycisk("Odśwież listę", ACCENT);
        btnAnulujCala = stworzPrzycisk("Anuluj całą grupę", DANGER);
        btnAnulujCala.setEnabled(false);

        btnPanel.add(btnRefresh);
        btnPanel.add(btnAnulujCala);

        statusLabel = new JLabel("Gotowy – 0 seansów załadowanych", SwingConstants.CENTER);
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        south.add(btnPanel, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        // Akcje
        btnRefresh.addActionListener(e -> zaladujListeGrup());

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnAnulujCala.setEnabled(tabela.getSelectedRow() >= 0);
            }
        });

        btnAnulujCala.addActionListener(e -> anulujCalaGrupe());

        tabela.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tabela.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        tabela.setRowSelectionInterval(row, row);
                        int id = (Integer) model.getValueAt(row, 0);
                        otworzSzczegoly(id);
                    }
                }
            }
        });

        // Timer 8 sekund
        timer = new Timer(100, e -> zaladujListeGrup());
        timer.start();

        zaladujListeGrup();
    }

    private JButton stworzPrzycisk(String tekst, Color bg) {
        JButton b = new JButton(tekst);
        b.setFont(new Font("Segoe UI", Font.BOLD, 18));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(280, 58));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1),
                new EmptyBorder(14, 0, 14, 0)
        ));

        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(bg.darker()); }
            public void mouseExited(MouseEvent e)  { if (b.isEnabled()) b.setBackground(bg); }
            public void mousePressed(MouseEvent e) { if (b.isEnabled()) b.setBackground(bg.darker().darker()); }
            public void mouseReleased(MouseEvent e) { if (b.isEnabled()) b.setBackground(bg.darker()); }
        });

        return b;
    }

    private void zaladujListeGrup() {
        Integer selId = null;
        int selRow = tabela.getSelectedRow();
        if (selRow >= 0) selId = (Integer) model.getValueAt(selRow, 0);

        model.setRowCount(0);

        String sql = """
            SELECT 
                s.id_seansu, 
                s.tytul, 
                s.data_seansu, 
                s.godzina,
                COUNT(r.id_rezerwacji) AS ile
            FROM seanse s
            LEFT JOIN rezerwacje r ON s.id_seansu = r.id_seansu
            GROUP BY s.id_seansu
            HAVING ile > 0
            ORDER BY s.data_seansu, s.godzina
        """;

        int count = 0;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                count++;
                model.addRow(new Object[]{
                        rs.getInt("id_seansu"),
                        rs.getString("tytul"),
                        rs.getString("data_seansu"),
                        rs.getString("godzina"),
                        rs.getInt("ile")
                });
            }

            model.fireTableDataChanged();
            tabela.revalidate();
            tabela.repaint();

            if (selId != null) {
                for (int i = 0; i < model.getRowCount(); i++) {
                    if (selId.equals(model.getValueAt(i, 0))) {
                        tabela.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            }

            statusLabel.setText("Załadowano " + count + " seansów z rezerwacjami • " +
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));

        } catch (SQLException ex) {
            statusLabel.setText("Błąd połączenia");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void anulujCalaGrupe() {
        int row = tabela.getSelectedRow();
        if (row < 0) return;

        int id = (Integer) model.getValueAt(row, 0);
        int ile = (Integer) model.getValueAt(row, 4);

        int choice = JOptionPane.showConfirmDialog(this,
                "Usunąć wszystkie " + ile + " miejsc dla seansu #" + id + "?",
                "Anulowanie grupy", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM rezerwacje WHERE id_seansu = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                int deleted = ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Usunięto " + deleted + " miejsc", "Sukces", JOptionPane.INFORMATION_MESSAGE);
                zaladujListeGrup();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void otworzSzczegoly(int idSeansu) {
        JDialog d = new JDialog(this, "Miejsca – Seans #" + idSeansu, true);
        d.setSize(580, 520);
        d.setLocationRelativeTo(this);
        d.getContentPane().setBackground(PANEL_BG);

        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        ta.setBackground(PANEL_BG);
        ta.setForeground(TEXT);
        ta.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(PANEL_BG);
        d.add(sp, BorderLayout.CENTER);

        StringBuilder sb = new StringBuilder("Zajęte miejsca:\n\n Miejsce     Data rezerwacji\n────────────────────────────────────\n");

        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT miejsce, created_at FROM rezerwacje WHERE id_seansu = ? ORDER BY miejsce")) {
            ps.setInt(1, idSeansu);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(String.format(" %-10s   %s%n", rs.getString("miejsce"), rs.getTimestamp("created_at")));
                }
            }
        } catch (SQLException ex) {
            sb.append("\nBłąd: ").append(ex.getMessage());
        }
        ta.setText(sb.toString());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));
        p.setBackground(PANEL_BG);

        JTextField tf = new JTextField(10);
        JButton b = stworzPrzycisk("Anuluj pojedyncze", ACCENT_DARK);

        p.add(new JLabel("Miejsce:"));
        p.add(tf);
        p.add(b);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(PANEL_BG);
        bottom.add(p, BorderLayout.CENTER);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        d.add(bottom, BorderLayout.SOUTH);

        b.addActionListener(e -> {
            String m = tf.getText().trim().toUpperCase();
            if (m.isEmpty()) return;
            if (JOptionPane.showConfirmDialog(d, "Anulować " + m + "?", "Potwierdź", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                usunPojedyncze(idSeansu, m);
                d.dispose();
            }
        });

        d.setVisible(true);
    }

    private void usunPojedyncze(int idSeansu, String miejsce) {
        String sql = "DELETE FROM rezerwacje WHERE id_seansu = ? AND miejsce = ?";
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSeansu);
            ps.setString(2, miejsce);
            int rows = ps.executeUpdate();
            String msg = rows > 0 ? "Usunięto " + miejsce : "Nie znaleziono " + miejsce;
            JOptionPane.showMessageDialog(this, msg, "Informacja", JOptionPane.INFORMATION_MESSAGE);
            zaladujListeGrup();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KinoApp().setVisible(true));
    }
}