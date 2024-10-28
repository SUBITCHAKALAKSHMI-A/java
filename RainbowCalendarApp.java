import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class RainbowCalendarApp {
    private JFrame frame;
    private JPanel calendarPanel;
    private JPanel eventPanel;
    private JPanel clockPanel;
    private JTextArea eventTextArea;
    private JLabel clockLabel;
    private HashMap<LocalDate, ArrayList<String>> events;
    private LocalDate currentDate;
    private LocalDate today;
    private JButton prevMonthButton, nextMonthButton, showEventsButton, completeTaskButton;
    private JLabel monthYearLabel;
    private JList<String> eventList;
    static final String DB_URL = "jdbc:mysql://localhost:3306/";
    static final String USER = "root";
    static final String PASS = "Subieng@2023";

    public RainbowCalendarApp() {
        events = new HashMap<>();
        currentDate = LocalDate.now();
        today = LocalDate.now();
        createAndShowGUI();
        checkAndCreateDatabase();
        loadEventsFromDatabase(); // Load existing events from the database
    }

    private void createAndShowGUI() {
        frame = new JFrame("Rainbow Calendar Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Navigation panel
        JPanel navigationPanel = new JPanel();
        prevMonthButton = new JButton("<");
        nextMonthButton = new JButton(">"); // Ensure this button is created
        showEventsButton = new JButton("Show Events");
        completeTaskButton = new JButton("Complete Task");
        monthYearLabel = new JLabel("", SwingConstants.CENTER);

        prevMonthButton.addActionListener(e -> {
            currentDate = currentDate.minusMonths(1);
            updateCalendar();
        });

        nextMonthButton.addActionListener(e -> {
            currentDate = currentDate.plusMonths(1);
            updateCalendar();
        });

        showEventsButton.addActionListener(e -> showEvents(currentDate)); // Show events for current date
        completeTaskButton.addActionListener(e -> completeTask()); // Mark task as completed

        navigationPanel.setLayout(new GridLayout(1, 5)); // Use grid layout for better spacing
        navigationPanel.add(prevMonthButton);
        navigationPanel.add(monthYearLabel);
        navigationPanel.add(showEventsButton);
        navigationPanel.add(completeTaskButton);
        navigationPanel.add(nextMonthButton); // Add next month button last

        frame.add(navigationPanel, BorderLayout.NORTH);

        // Calendar panel
        calendarPanel = new JPanel();
        calendarPanel.setLayout(new GridLayout(0, 7));
        frame.add(calendarPanel, BorderLayout.CENTER);

        // Event panel
        eventPanel = new JPanel();
        eventPanel.setLayout(new BorderLayout());

        eventTextArea = new JTextArea(10, 30);
        eventTextArea.setEditable(false);

        // List to display days with events
        DefaultListModel<String> listModel = new DefaultListModel<>();
        eventList = new JList<>(listModel);
        JScrollPane listScrollPane = new JScrollPane(eventList);

        JButton addButton = new JButton("Add Event");
        addButton.addActionListener(e -> addEvent());

        eventPanel.add(listScrollPane, BorderLayout.CENTER);
        eventPanel.add(addButton, BorderLayout.SOUTH);

        frame.add(eventPanel, BorderLayout.EAST);

        // Clock panel
        clockPanel = new JPanel();
        clockLabel = new JLabel();
        clockPanel.add(clockLabel);
        frame.add(clockPanel, BorderLayout.SOUTH);

        // Start with the current month
        updateCalendar();
        updateClock();
        new Timer(1000, e -> updateClock()).start();

        frame.setVisible(true);
    }

    private void updateCalendar() {
        calendarPanel.removeAll();
        updateEventList();

        monthYearLabel.setText(currentDate.getMonth().name() + " " + currentDate.getYear());

        LocalDate firstDate = currentDate.withDayOfMonth(1);
        int daysInMonth = currentDate.lengthOfMonth();
        LocalDate firstDayOfMonth = firstDate.withDayOfMonth(1);
        int dayOfWeek = firstDayOfMonth.getDayOfWeek().getValue();
        if (dayOfWeek == 7) dayOfWeek = 0;

        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String day : days) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(Color.CYAN);
            calendarPanel.add(label);
        }

        for (int i = 0; i < dayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(currentDate.getYear(), currentDate.getMonth(), day);
            RoundedButton button = new RoundedButton(String.valueOf(day), date);
            button.setPreferredSize(new Dimension(60, 60));
            button.addActionListener(e -> showEvents(date));

            if (date.equals(today)) {
                button.setBackground(Color.YELLOW);
            } else if (events.containsKey(date)) {
                button.setBackground(Color.GREEN);
            } else {
                button.setBackground(Color.WHITE);
            }

            calendarPanel.add(button);
        }

        int remainingDays = 42 - (daysInMonth + dayOfWeek);
        for (int i = 0; i < remainingDays; i++) {
            calendarPanel.add(new JLabel(""));
        }

        frame.revalidate();
        frame.repaint();
    }

    private void updateEventList() {
        DefaultListModel<String> listModel = (DefaultListModel<String>) eventList.getModel();
        listModel.clear();

        LocalDate startOfMonth = currentDate.withDayOfMonth(1);
        LocalDate endOfMonth = currentDate.withDayOfMonth(currentDate.lengthOfMonth());

        for (LocalDate date = startOfMonth; !date.isAfter(endOfMonth); date = date.plusDays(1)) {
            if (events.containsKey(date)) {
                listModel.addElement(date + ": " + events.get(date).size() + " events");
            }
        }
    }

    private void showEvents(LocalDate date) {
        ArrayList<String> eventList = events.getOrDefault(date, new ArrayList<>());
        ArrayList<String> completedList = fetchCompletedTasksFromDatabase(date);

        eventTextArea.setText("Events on " + date + ":\n");
        
        if (eventList.isEmpty() && completedList.isEmpty()) {
            eventTextArea.append("No events or completed tasks for this date.\n");
        }

        for (String event : eventList) {
            eventTextArea.append("Event: " + event + "\n");
        }
        
        for (String completedTask : completedList) {
            eventTextArea.append("Completed Task: " + completedTask + "\n");
        }
    }

    private ArrayList<String> fetchCompletedTasksFromDatabase(LocalDate date) {
        ArrayList<String> completedList = new ArrayList<>();
        String query = "SELECT task_name FROM completed_tasks WHERE task_date = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL + "VRD", USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String taskName = rs.getString("task_name");
                completedList.add(taskName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return completedList;
    }

    private void addEvent() {
        String input = JOptionPane.showInputDialog(frame, "Enter Date for Event (YYYY-MM-DD):", LocalDate.now());
        if (input != null) {
            LocalDate date;
            try {
                date = LocalDate.parse(input);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Invalid date format. Please enter in YYYY-MM-DD format.");
                return;
            }

            String event = JOptionPane.showInputDialog(frame, "Enter Event:");
            if (event != null && !event.isEmpty()) {
                ArrayList<String> eventList = events.getOrDefault(date, new ArrayList<>());
                eventList.add(event);
                events.put(date, eventList);
                insertEventIntoDatabase(event, date);
                updateCalendar();
            }
        }
    }

    private void insertEventIntoDatabase(String eventName, LocalDate eventDate) {
        String insertSQL = "INSERT INTO events (event_name, event_date) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL + "VRD", USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, eventName);
            pstmt.setDate(2, java.sql.Date.valueOf(eventDate));
            pstmt.executeUpdate();
            System.out.println("Event added to database.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void completeTask() {
        String input = JOptionPane.showInputDialog(frame, "Enter Date for Completed Task (YYYY-MM-DD):", LocalDate.now());
        if (input != null) {
            LocalDate date;
            try {
                date = LocalDate.parse(input);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Invalid date format. Please enter in YYYY-MM-DD format.");
                return;
            }

            String task = JOptionPane.showInputDialog(frame, "Enter Completed Task:");
            if (task != null && !task.isEmpty()) {
                insertCompletedTaskIntoDatabase(task, date);
                updateCalendar();
            }
        }
    }

    private void insertCompletedTaskIntoDatabase(String taskName, LocalDate taskDate) {
        String insertSQL = "INSERT INTO completed_tasks (task_name, task_date) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL + "VRD", USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, taskName);
            pstmt.setDate(2, java.sql.Date.valueOf(taskDate));
            pstmt.executeUpdate();
            System.out.println("Completed task added to database.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateClock() {
        LocalTime time = LocalTime.now();
        clockLabel.setText(time.getHour() + ":" + String.format("%02d", time.getMinute()) + ":" + String.format("%02d", time.getSecond()));
    }

    class RoundedButton extends JButton {
        private static final int DIAMETER = 60;

        public RoundedButton(String label, LocalDate date) {
            super(label);
            this.setFocusPainted(false);
            this.setBorderPainted(false);
            this.setContentAreaFilled(false);
            this.setForeground(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(getBackground());
            g2d.fillRoundRect(0, 0, DIAMETER, DIAMETER, 20, 20);
            g2d.setFont(getFont());
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(getText());
            int textHeight = fm.getAscent();
            int x = (DIAMETER - textWidth) / 2;
            int y = (DIAMETER + textHeight) / 2 - 3;
            g2d.setColor(getForeground());
            g2d.drawString(getText(), x, y);
            g2d.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(DIAMETER, DIAMETER);
        }
    }

    private void checkAndCreateDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            String dbName = "VRD";
            ResultSet resultSet = stmt.executeQuery("SHOW DATABASES LIKE '" + dbName + "'");
            if (resultSet.next()) {
                System.out.println("Database already exists.");
            } else {
                String sql = "CREATE DATABASE " + dbName;
                stmt.executeUpdate(sql);
                System.out.println("Database created successfully...");
                createTables(conn); // Create tables if the database is created
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            String createEventsTable = "CREATE TABLE events ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "event_name VARCHAR(255) NOT NULL, "
                    + "event_date DATE NOT NULL)";
            String createCompletedTasksTable = "CREATE TABLE completed_tasks ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "task_name VARCHAR(255) NOT NULL, "
                    + "task_date DATE NOT NULL)";
            stmt.executeUpdate(createEventsTable);
            stmt.executeUpdate(createCompletedTasksTable);
            System.out.println("Tables created successfully...");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadEventsFromDatabase() {
        String query = "SELECT event_name, event_date FROM events";
        try (Connection conn = DriverManager.getConnection(DB_URL + "VRD", USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String eventName = rs.getString("event_name");
                LocalDate eventDate = rs.getDate("event_date").toLocalDate();
                ArrayList<String> eventList = events.getOrDefault(eventDate, new ArrayList<>());
                eventList.add(eventName);
                events.put(eventDate, eventList);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RainbowCalendarApp::new);
    }
}

