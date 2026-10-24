import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Scanner;

/**
 * Лабораторная работа, часть 3 (сложный вариант).
 * Ввод нескольких чисел, проверка каждого на целостность (целое или нет) и чётность.
 * Результаты сохраняются в PostgreSQL и выгружаются в Excel (CSV).
 */
public class Main {
    public static void main(String[] args) {
        lab3 a = new lab3();
        if (!a.podkl()) return;

        while (true) {
            System.out.println();
            System.out.println("========== МЕНЮ ==========");
            System.out.println("1. Вывести все таблицы из PostgreSQL");
            System.out.println("2. Создать таблицу в PostgreSQL");
            System.out.println("3. Выполнение задачи базового варианта (проверка чисел на целостность и чётность), результат сохранить в PostgreSQL с последующим выводом в консоль");
            System.out.println("4. Сохранить все данные из PostgreSQL в Excel и вывести на экран");
            System.out.println("0. Выход");
            System.out.print("Ваш выбор: ");

            int vib = a.chitatVybor();
            try {
                switch (vib) {
                    case 0:
                        a.zakr();
                        System.out.println("Работа завершена");
                        return;
                    case 1: a.tabli(); break;
                    case 2: a.sozdTabl(); break;
                    case 3: a.proverka(); break;
                    case 4: a.exel(); break;
                    default: System.out.println("Нет такого пункта меню, введите число от 0 до 4");
                }
            } catch (SQLException e) {
                System.out.println("Ошибка работы с базой данных: " + e.getMessage());
            }
        }
    }
}

class lab3 {
    // Параметры подключения к PostgreSQL (при необходимости поменяйте под себя)
    static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    static final String USER = "postgres";
    static final String PASSWORD = "postgres";

    Scanner scan = new Scanner(System.in);
    Connection conn;
    Statement stat;
    String tablename; // таблица, с которой сейчас работаем

    // ---------- Подключение к БД ----------

    boolean podkl() {
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            stat = conn.createStatement();
            System.out.println("Подключение к PostgreSQL прошло успешно");
            return true;
        } catch (SQLException e) {
            System.out.println("Не удалось подключиться к базе данных: " + e.getMessage());
            System.out.println("Проверьте, что PostgreSQL запущен и логин/пароль в классе lab3 верные");
            return false;
        }
    }

    void zakr() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
    }

    int chitatVybor() {
        if (!scan.hasNextLine()) return 0;
        String s = scan.nextLine().trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------- 1. Вывод всех таблиц ----------

    void tabli() throws SQLException {
        ResultSet rs = stat.executeQuery("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name");
        System.out.println("\n=== Список всех таблиц в базе данных ===");
        int count = 0;
        while (rs.next()) {
            count++;
            System.out.println(count + ". " + rs.getString("table_name"));
        }
        rs.close();
        if (count == 0) System.out.println("Таблиц не найдено");
        else System.out.println("Всего таблиц: " + count);
    }

    // ---------- 2. Создание таблицы ----------

    void sozdTabl() throws SQLException {
        String name;
        while (true) {
            System.out.print("Введите название таблицы (латиница, цифры, _): ");
            name = scan.nextLine().trim().toLowerCase();
            if (name.matches("[a-z_][a-z0-9_]*")) break;
            System.out.println("Некорректное название таблицы");
        }
        tablename = name;
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablename + " ("
                + "id SERIAL PRIMARY KEY, "
                + "vvod VARCHAR(100), "
                + "celoe BOOLEAN, "
                + "chetnost VARCHAR(20), "
                + "rezultat VARCHAR(200))");
        System.out.println("Таблица '" + tablename + "' создана (или уже существовала) и выбрана для работы");
    }

    // ---------- 3. Задача базового варианта ----------

    void proverka() throws SQLException {
        if (tablename == null) {
            System.out.println("Сначала создайте или выберите таблицу (пункт 2)");
            return;
        }

        int n;
        while (true) {
            System.out.print("Сколько чисел вы хотите ввести? ");
            String s = scan.nextLine().trim();
            try {
                n = Integer.parseInt(s);
                if (n > 0) break;
                System.out.println("Количество должно быть больше нуля");
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите целое число");
            }
        }

        int perviyId = -1;
        for (int i = 1; i <= n; i++) {
            System.out.print("Введите число " + i + " из " + n + ": ");
            String vvod = scan.nextLine().trim();

            Boolean celoe;       // null - введено вообще не число
            String chetnost;
            String rezultat;

            BigDecimal chislo = vChislo(vvod);
            if (chislo == null) {
                celoe = null;
                chetnost = "-";
                rezultat = "Ошибка: '" + vvod + "' - это не число";
            } else if (chislo.stripTrailingZeros().scale() > 0) {
                celoe = false;
                chetnost = "-";
                rezultat = "Ошибка: " + vvod + " - не целое число";
            } else {
                celoe = true;
                BigInteger bi = chislo.toBigIntegerExact();
                chetnost = bi.testBit(0) ? "нечётное" : "чётное";
                rezultat = vvod + " - целое " + chetnost + " число";
            }
            System.out.println(rezultat);

            PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tablename
                    + " (vvod, celoe, chetnost, rezultat) VALUES (?,?,?,?) RETURNING id");
            ps.setString(1, vvod);
            if (celoe == null) ps.setNull(2, Types.BOOLEAN); else ps.setBoolean(2, celoe);
            ps.setString(3, chetnost);
            ps.setString(4, rezultat);
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (perviyId < 0) perviyId = rs.getInt(1);
            rs.close();
            ps.close();
        }
        System.out.println("\nРезультаты сохранены в таблицу '" + tablename + "'");

        // Выводим из БД все только что сохранённые записи
        PreparedStatement sel = conn.prepareStatement("SELECT * FROM " + tablename
                + " WHERE id >= ? ORDER BY id");
        sel.setInt(1, perviyId);
        ResultSet r = sel.executeQuery();
        System.out.println("\n--- Результат проверки (данные из БД) ---");
        pechatZagolovok();
        while (r.next()) pechatStroku(r);
        r.close();
        sel.close();
    }

    // Пытаемся превратить ввод в число. Допускается запятая вместо точки.
    // Возвращает null, если это вообще не число.
    BigDecimal vChislo(String s) {
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void pechatZagolovok() {
        System.out.printf("%-5s %-20s %-12s %-10s %s%n", "id", "Ввод", "Целое?", "Чётность", "Результат");
    }

    void pechatStroku(ResultSet r) throws SQLException {
        System.out.printf("%-5s %-20s %-12s %-10s %s%n",
                r.getString("id"), r.getString("vvod"), celoeText(r),
                r.getString("chetnost"), r.getString("rezultat"));
    }

    String celoeText(ResultSet r) throws SQLException {
        Object o = r.getObject("celoe");
        if (o == null) return "не число";
        return (Boolean) o ? "да" : "нет";
    }

    // ---------- 4. Экспорт в Excel ----------

    void exel() throws SQLException {
        if (tablename == null) {
            System.out.println("Сначала создайте или выберите таблицу (пункт 2)");
            return;
        }
        ResultSet rs = stat.executeQuery("SELECT * FROM " + tablename + " ORDER BY id");
        File file = new File(papkaDlyaFaila(), tablename + ".csv");

        System.out.println("\n=== Содержимое таблицы '" + tablename + "' ===");
        pechatZagolovok();
        int count = 0;
        // CSV с BOM и разделителем ';' - Excel открывает его с правильной кодировкой
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write("id;Ввод;Целое число;Чётность;Результат\n");
            while (rs.next()) {
                count++;
                pechatStroku(rs);
                w.write(rs.getString("id") + ";" + csv(rs.getString("vvod")) + ";" + celoeText(rs) + ";"
                        + rs.getString("chetnost") + ";" + csv(rs.getString("rezultat")) + "\n");
            }
        } catch (IOException e) {
            System.out.println("Ошибка при создании файла: " + e.getMessage());
            return;
        } finally {
            rs.close();
        }

        if (count == 0) System.out.println("Таблица пуста");
        System.out.println("\nДанные сохранены в Excel-файл: " + file.getAbsolutePath() + " (записей: " + count + ")");
        otkrytFail(file);
    }

    // Ввод пользователя может содержать ';' и кавычки - экранируем по правилам CSV
    String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    File papkaDlyaFaila() {
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        if (desktop.isDirectory()) return desktop;
        File oneDrive = new File(System.getProperty("user.home"), "OneDrive" + File.separator + "Desktop");
        if (oneDrive.isDirectory()) return oneDrive;
        return new File(System.getProperty("user.dir"));
    }

    void otkrytFail(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                System.out.println("Файл открыт в Excel");
                return;
            }
        } catch (Exception ignored) {
        }
        System.out.println("Откройте файл в Excel вручную");
    }
}
