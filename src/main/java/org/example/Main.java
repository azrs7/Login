package org.example;

import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final DatabaseHandler db = new DatabaseHandler(); // handles users table
    private static String currentUserEmail = null;
    private static double balance = 0.0;
    private static Connection conn;

    public static void main(String[] args) {
        while (true) {
            System.out.println("== Ledger System ==");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.print("> ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> loginUser();
                case "2" -> registerUser();
                default -> System.out.println("Invalid choice.\n");
            }
        }
    }

    private static void registerUser() {
        System.out.println("\n== Please fill in the form ==");

        System.out.print("Name: ");
        String name = scanner.nextLine();

        String email;
        while (true) {
            System.out.print("Email: ");
            email = scanner.nextLine();
            if (isValidEmail(email)) break;
            System.out.println("Invalid email format. Try again.");
        }

        String password;
        while (true) {
            System.out.print("Password: ");
            password = scanner.nextLine();
            System.out.print("Confirm Password: ");
            String confirmPassword = scanner.nextLine();

            if (!password.equals(confirmPassword)) {
                System.out.println("Passwords do not match.");
            } else break;
        }

        if (db.userExists(email)) {
            System.out.println("Email already registered!\n");
        } else {
            db.insertUser(name, email, password);
            System.out.println("\nRegistration successful!\n");
        }
    }

    private static void loginUser() {
        System.out.println("\n== Login ==");

        String email;
        while (true) {
            System.out.print("Email: ");
            email = scanner.nextLine();
            if (isValidEmail(email)) break;
            System.out.println("Invalid email format.");
        }

        System.out.print("Password: ");
        String password = scanner.nextLine();

        if (!db.userExists(email)) {
            System.out.println("Email not registered!\n");
        } else if (db.validateUser(email, password)) {
            System.out.println("\nLogin successful!\n");
            currentUserEmail = email;
            transactionMenu();
        } else {
            System.out.println("Incorrect password!\n");
        }
    }

    private static void transactionMenu() {
        try {
            connectTransactionDB();
            Scanner input = new Scanner(System.in);
            int choice;

            while (true) {
                System.out.println("\n==Transaction Menu==");
                System.out.println("1. Debit\n2. Credit\n3. History\n4. Logout");
                System.out.print("> ");
                choice = input.nextInt();
                input.nextLine();

                switch (choice) {
                    case 1 -> handleDebit(input);
                    case 2 -> handleCredit(input);
                    case 3 -> showHistory();
                    case 4 -> {
                        disconnectDatabase();
                        currentUserEmail = null;
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void handleDebit(Scanner input) {
        System.out.println("== Debit ==");
        System.out.print("Enter amount: ");
        double amount = input.nextDouble();
        input.nextLine();
        System.out.print("Description: ");
        String desc = input.nextLine();

        if (amount <= 0 || amount > 1_000_000 || desc.length() > 100) {
            System.out.println("Invalid input.");
            return;
        }

        if (amount > balance) {
            System.out.println("Insufficient balance.");
            return;
        }

        balance -= amount;
        saveTransaction("Debit", amount, desc);
        System.out.println("Debit recorded. Balance: " + balance);
    }

    private static void handleCredit(Scanner input) {
        System.out.println("== Credit ==");
        System.out.print("Enter amount: ");
        double amount = input.nextDouble();
        input.nextLine();
        System.out.print("Description: ");
        String desc = input.nextLine();

        if (amount <= 0 || desc.length() > 100) {
            System.out.println("Invalid input.");
            return;
        }

        balance += amount;
        saveTransaction("Credit", amount, desc);
        System.out.println("Credit recorded. Balance: " + balance);
    }

    private static void showHistory() {
        System.out.println("== Transaction History ==");
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM transactions WHERE email = ? ORDER BY id DESC")) {
            ps.setString(1, currentUserEmail);
            ResultSet rs = ps.executeQuery();

            System.out.printf("%-3s | %-6s | %-8s | %s\n", "ID", "Type", "Amount", "Description");
            System.out.println("-----------------------------------------");

            while (rs.next()) {
                System.out.printf("%-3d | %-6s | %8.2f | %s\n",
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("description"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to show history:");
            e.printStackTrace();
        }
    }

    private static void connectTransactionDB() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:transactions.db");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT NOT NULL,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            PreparedStatement ps = conn.prepareStatement("""
                SELECT SUM(CASE WHEN type = 'Credit' THEN amount ELSE -amount END) AS balance
                FROM transactions
                WHERE email = ?
            """);
            ps.setString(1, currentUserEmail);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                balance = rs.getDouble("balance");
            }
        }
    }

    private static void saveTransaction(String type, double amount, String description) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO transactions(email, type, amount, description) VALUES(?, ?, ?, ?)")) {
            ps.setString(1, currentUserEmail);
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.setString(4, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save transaction:");
            e.printStackTrace();
        }
    }

    private static void disconnectDatabase() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            System.out.println("Logged out and DB connection closed.");
        }
    }

    private static boolean isValidEmail(String email) {
        return Pattern.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", email);
    }


}
