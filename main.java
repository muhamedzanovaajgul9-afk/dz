import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final int PORT = 8080;
    private static boolean isAuthenticated = false; // ТЗ 5

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new MainHandler());
        server.setExecutor(null);

        System.out.println("==================================================");
        System.out.println("[NEO-SPORT CORE v4.09] СЕРВЕР ИНИЦИАЛИЗИРОВАН НА ПОРТУ: " + PORT);
        System.out.println("URL терминала: http://localhost:" + PORT);
        System.out.println("Мастер-ключ авторизации: логин 'admin', пароль '1234'");
        System.out.println("==================================================");
        server.start();
    }

    static class MainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            Map<String, String> params = new HashMap<>();

            if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                params = parseFormData(body);
            }

            String action = params.getOrDefault("action", "");
            String alertMessage = "";
            String alertType = "info";
            String resultOutput = "";

            if ("login".equals(action)) {
                String login = params.getOrDefault("login", "").trim();
                String pass = params.getOrDefault("password", "").trim();
                if (login.isEmpty() || pass.isEmpty()) {
                    alertMessage = "СБОЙ [ТЗ 2]: Идентификатор атлета и ключ доступа обязательны к заполнению!";
                    alertType = "danger";
                } else if ("admin".equals(login) && "1234".equals(pass)) {
                    isAuthenticated = true;
                    alertMessage = "БИОМЕТРИЧЕСКИЙ ДОСТУП РАЗРЕШЕН. Добро пожаловать в терминал, " + login + ".";
                    alertType = "success";
                } else {
                    alertMessage = "ТРЕВОГА [ТЗ 5]: Некорректный идентификатор или пароль шлюза!";
                    alertType = "danger";
                }
            } else if ("logout".equals(action)) {
                isAuthenticated = false;
                alertMessage = "СЕССИЯ АТЛЕТА ЗАВЕРШЕНА. Терминал заблокирован.";
                alertType = "info";
            } else if (isAuthenticated) {
                switch (action) {
                    case "module1":
                        resultOutput = handleModule1(params);
                        break;
                    case "module2":
                        resultOutput = handleModule2(params);
                        break;
                    case "module3":
                        resultOutput = handleModule3(params);
                        break;
                    case "module4":
                        resultOutput = handleModule4(params);
                        break;
                    case "module5":
                        resultOutput = handleModule5(params);
                        break;
                }
            }

            String responseHtml = renderPage(alertMessage, alertType, resultOutput);
            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // --- Модуль 1: Атлет и био-показатели ---
    private static String handleModule1(Map<String, String> p) {
        String name = p.getOrDefault("name", "").trim();
        String status = p.getOrDefault("status", "").trim();
        String metricStr = p.getOrDefault("metric", "").trim();

        if (name.isEmpty()) return errorHtml("[ТЗ 2] Позывной атлета не может быть пустым!");

        String statusText;
        if ("active".equals(status)) statusText = "<span class='badge-neon-green'>Активный контрагент</span>";
        else if ("inactive".equals(status)) statusText = "<span class='badge-neon-yellow'>В резерве</span>";
        else if ("banned".equals(status)) statusText = "<span class='badge-neon-red'>Дисквалифицирован (БАН)</span>";
        else return errorHtml("[ТЗ 2] Выбран некорректный статус атлета.");

        double metric;
        try {
            metric = Double.parseDouble(metricStr.replace(',', '.'));
        } catch (Exception e) {
            return errorHtml("[ТЗ 2] Ошибка считывания индекса выносливости (число).");
        }

        String metricLevel;
        if (metric < 0) return errorHtml("[ТЗ 2] Показатель VO2/силы не может быть отрицательным.");
        else if (metric < 40) metricLevel = "Низкий КПД (Требуется апгрейд экипировки)";
        else if (metric <= 75) metricLevel = "Оптимальный соревновательный тонус";
        else metricLevel = "Пиковый кибер-уровень (PRO League)";

        return "<div class='res-block'>" +
                "<div class='res-title'>ТЕЛЕМЕТРИЯ АТЛЕТА (РАЗДЕЛ 1)</div>" +
                "<p><b>Позывной / Имя:</b> " + escape(name) + "</p>" +
                "<p><b>Статус контракта (ТЗ 3):</b> " + statusText + "</p>" +
                "<p><b>Биомеханический индекс (ТЗ 4):</b> " + metricLevel + " [" + metric + " pts]</p>" +
                "</div>";
    }

    // --- Модуль 2: Спортивный допуск и верификация ---
    private static String handleModule2(Map<String, String> p) {
        int age;
        try {
            age = Integer.parseInt(p.getOrDefault("age", "").trim());
            if (age < 0 || age > 120) throw new Exception();
        } catch (Exception e) {
            return errorHtml("[ТЗ 2] Укажите валидный биологический возраст атлета (0–120).");
        }

        String ageCategory;
        if (age < 14) ageCategory = "Юниор Лига (до 14 лет)";
        else if (age < 18) ageCategory = "Молодежный дивизион (14–17 лет)";
        else if (age < 65) ageCategory = "Основной дивизион PRO (18–64 года)";
        else ageCategory = "Дивизион Мастерс (65+ лет)";

        boolean isBanned = "on".equals(p.get("isBanned"));
        boolean hasPassport = "on".equals(p.get("hasPassport"));
        boolean feePaid = "on".equals(p.get("feePaid"));
        boolean isStudent = "on".equals(p.get("isStudent"));
        boolean isVeteran = "on".equals(p.get("isVeteran"));

        if (!(!isBanned)) {
            return errorHtml("[ТЗ 9] Допинг-контроль провален! Доступ к складу спортивной экипировки заблокирован.");
        }

        String serviceStatus = (hasPassport && feePaid)
                ? "<span class='badge-neon-green'>Выдача разрешена (ID + Регвзнос)</span>"
                : "<span class='badge-neon-red'>Отказано (Нужен ID атлета и взнос)</span>";

        String discountStatus = (isStudent || isVeteran)
                ? "<span class='badge-neon-cyan'>Льготная квота (Студент / Ветеран спорта)</span>"
                : "Стандартный прейскурант";

        boolean step1 = "on".equals(p.get("step1"));
        boolean step2 = "on".equals(p.get("step2"));
        boolean step3 = "on".equals(p.get("step3"));
        String nestedResult;
        if (step1) {
            if (step2) {
                if (step3) {
                    nestedResult = "<span class='badge-neon-green'>УСПЕХ: 3D-скан тела + чип + смарт-браслет подтверждены</span>";
                } else {
                    nestedResult = "<span class='badge-neon-yellow'>Сбой на Шаге 3: Смарт-браслет не синхронизирован</span>";
                }
            } else {
                nestedResult = "<span class='badge-neon-yellow'>Сбой на Шаге 2: Чип экипировки не найден</span>";
            }
        } else {
            nestedResult = "<span class='badge-neon-red'>Сбой на Шаге 1: 3D-сканер не распознал контуры тела</span>";
        }

        return "<div class='res-block'>" +
                "<div class='res-title'>ДОПУСК И ВЕРИФИКАЦИЯ ЭКИПИРОВКИ (РАЗДЕЛ 2)</div>" +
                "<p><b>Возрастной дивизион (ТЗ 6):</b> " + ageCategory + "</p>" +
                "<p><b>Выдача снаряжения через && (ТЗ 7):</b> " + serviceStatus + "</p>" +
                "<p><b>Клубная квота через || (ТЗ 8):</b> " + discountStatus + "</p>" +
                "<p><b>Допинг-статус !isBanned (ТЗ 9):</b> Чист, доступ к экипировке открыт</p>" +
                "<p><b>Трёхэтапная калибровка (ТЗ 10):</b> " + nestedResult + "</p>" +
                "</div>";
    }

    // --- Модуль 3: Корзина экипировки и расчет выгоды ---
    private static String handleModule3(Map<String, String> p) {
        double purchase, income, expenses;
        int score, failedLogins;
        try {
            purchase = Double.parseDouble(p.getOrDefault("purchase", "0").replace(',', '.'));
            income = Double.parseDouble(p.getOrDefault("income", "0").replace(',', '.'));
            expenses = Double.parseDouble(p.getOrDefault("expenses", "0").replace(',', '.'));
            score = Integer.parseInt(p.getOrDefault("score", "0"));
            failedLogins = Integer.parseInt(p.getOrDefault("failedLogins", "0"));
        } catch (Exception e) {
            return errorHtml("[ТЗ 2] Ошибка числовых данных при расчете экипировки.");
        }

        boolean isVip = "on".equals(p.get("isVip"));
        double discountRate;
        if (purchase > 10000) discountRate = isVip ? 0.25 : 0.15;
        else if (purchase >= 3000) discountRate = isVip ? 0.15 : 0.07;
        else discountRate = isVip ? 0.05 : 0.0;
        double finalPrice = purchase * (1.0 - discountRate);

        double balance = income - expenses;
        String finStatus;
        if (balance > income * 0.3) finStatus = "Профицит бюджета (готов к топовой экипировке)";
        else if (balance >= 0) finStatus = "Стабильный фонд атлета";
        else if (balance > -income * 0.2) finStatus = "Небольшой дефицит бюджета";
        else finStatus = "Критический дефицит (требуется спонсорство)";

        String grade;
        if (score >= 90) grade = "Elite Pro (A) — Допуск к Олимпиаде";
        else if (score >= 75) grade = "Advanced (B) — Норматив КМС/МС выполнен";
        else if (score >= 60) grade = "Amateur (C) — Базовый зачет";
        else grade = "Disqualified (F) — Отправлен на повторную аттестацию";

        boolean newLoc = "on".equals(p.get("newLoc"));
        boolean vpn = "on".equals(p.get("vpn"));
        int riskScore = (newLoc ? 30 : 0) + (vpn ? 25 : 0) + (failedLogins * 10);
        String riskLevel;
        if (riskScore >= 60) riskLevel = "<span class='badge-neon-red'>КРИТИЧЕСКИЙ (Антифрод: аккаунт заморожен)</span>";
        else if (riskScore >= 30) riskLevel = "<span class='badge-neon-yellow'>СРЕДНИЙ (Запрос смарт-токена)</span>";
        else riskLevel = "<span class='badge-neon-green'>БЕЗОПАСНО (Синхронизация стабильна)</span>";

        String goal = p.getOrDefault("goal", "1");
        String reco;
        if ("1".equals(goal)) reco = "Рекомендован смарт-пакет: восстановительное питание + компрессионное белье.";
        else if ("2".equals(goal)) reco = "Рекомендован карбоновый инвентарь + датчики динамической нагрузки.";
        else reco = "Рекомендован титановый силовой экзоскелет и премиум страховка травм.";

        return "<div class='res-block'>" +
                "<div class='res-title'>КАЛЬКУЛЯТОР ЭКИПИРОВКИ И БЮДЖЕТА (РАЗДЕЛ 3)</div>" +
                "<p><b>Чек экипировки (ТЗ 11):</b> Скидка " + (int)(discountRate * 100) + "%, к списанию: <b>" + String.format("%.2f", finalPrice) + " кредитов</b></p>" +
                "<p><b>Спортивный бюджет (ТЗ 12):</b> " + finStatus + " (Остаток: " + String.format("%.2f", balance) + " кр.)</p>" +
                "<p><b>Спортивный норматив (ТЗ 13):</b> " + grade + " [" + score + "/100]</p>" +
                "<p><b>Фрод-мониторинг смарт-карты (ТЗ 14):</b> " + riskLevel + " (Risk index: " + riskScore + ")</p>" +
                "<p><b>Спецификация комплекта (ТЗ 15):</b> " + reco + "</p>" +
                "</div>";
    }

    // --- Модуль 4: Категории спортивных товаров и заказы ---
    private static String handleModule4(Map<String, String> p) {
        String serviceCat = p.getOrDefault("serviceCat", "1");
        String catCapabilities;
        switch (serviceCat) {
            case "1": catCapabilities = "HARDWARE: Экзоскелеты, титановые грифы, умные блины с Bluetooth"; break;
            case "2": catCapabilities = "APPAREL: Кибер-трико, компрессионные термокостюмы с графеновыми нитями"; break;
            case "3": catCapabilities = "BIO-TECH: Изотоники нового поколения, молекулярные BCAA, нейро-восстановление"; break;
            default: catCapabilities = "Неизвестный спортивный сектор"; break;
        }

        String plan = p.getOrDefault("plan", "1");
        int units;
        try {
            units = Integer.parseInt(p.getOrDefault("units", "1"));
        } catch (Exception e) {
            units = 1;
        }
        boolean isUrgent = "on".equals(p.get("isUrgent"));

        double rate;
        switch (plan) {
            case "1": rate = 1000; break;
            case "2": rate = 2500; break;
            case "3": rate = 5000; break;
            default: rate = 1000; break;
        }
        double cost = rate * units * (isUrgent ? 1.5 : 1.0);

        String opType = p.getOrDefault("opType", "A");
        boolean isResident = "on".equals(p.get("isResident"));
        double amount;
        try {
            amount = Double.parseDouble(p.getOrDefault("amount", "0").replace(',', '.'));
        } catch (Exception e) {
            amount = 0;
        }

        String scenarioResult;
        switch (opType) {
            case "A":
                if (isResident && amount <= 500000) {
                    scenarioResult = "Сценарий 1: Экспресс-оплата заказа клубным атлетом. Дрон вылетел.";
                } else {
                    scenarioResult = "Сценарий 2: Крупная оптовая поставка тренажеров — направлена на таможенный контроль.";
                }
                break;
            case "B":
                if (isResident && amount <= 3000000) {
                    scenarioResult = "Сценарий 3: Спортивный лизинг на оборудованный кроссфит-бокс одобрен под 0%.";
                } else {
                    scenarioResult = "Сценарий 4: Лизинг стадионного масштаба — требуется гарантия Олимпийского комитета.";
                }
                break;
            case "C":
                if (amount >= 100000 && (isResident || amount >= 1000000)) {
                    scenarioResult = "Сценарий 5: Открыт PRO-депозит на годовую экипировку с персональным коучем.";
                } else {
                    scenarioResult = "Сценарий 5: Стандартный депозитный абонемент активирован.";
                }
                break;
            default:
                scenarioResult = "Неизвестный сценарий логистики.";
                break;
        }

        return "<div class='res-block'>" +
                "<div class='res-title'>ЛОГИСТИКА И КАТАЛОГ СНАРЯЖЕНИЯ (РАЗДЕЛ 4)</div>" +
                "<p><b>Сектор снаряжения (ТЗ 17):</b> " + catCapabilities + "</p>" +
                "<p><b>Стоимость партии (ТЗ 18):</b> <b>" + String.format("%.2f", cost) + " кр.</b>" + (isUrgent ? " [Дрон-доставка за 15 мин: +50%]" : "") + "</p>" +
                "<p><b>Статус ордера (ТЗ 20):</b> " + scenarioResult + "</p>" +
                "</div>";
    }

    // --- Модуль 5: Спортивный скоринг и контракт атлета ---
    private static String handleModule5(Map<String, String> p) {
        int age, creditScore, expMonths;
        double salary;
        boolean hasOverdue = "on".equals(p.get("hasOverdue"));

        try {
            age = Integer.parseInt(p.getOrDefault("scAge", "0"));
            salary = Double.parseDouble(p.getOrDefault("scSalary", "0").replace(',', '.'));
            creditScore = Integer.parseInt(p.getOrDefault("scScore", "0"));
            expMonths = Integer.parseInt(p.getOrDefault("scExp", "0"));
        } catch (Exception e) {
            return errorHtml("[ТЗ 2] Неверный ввод био-финансовых данных атлета.");
        }

        int totalScore = 0;
        if (age >= 21 && age <= 60) totalScore += 20;
        else if (age >= 18 && age <= 70) totalScore += 10;

        if (salary >= 120000) totalScore += 25;
        else if (salary >= 50000) totalScore += 15;
        else if (salary >= 25000) totalScore += 5;

        if (creditScore >= 720) totalScore += 30;
        else if (creditScore >= 600) totalScore += 15;

        if (expMonths >= 24) totalScore += 15;
        else if (expMonths >= 6) totalScore += 8;

        if (!hasOverdue) totalScore += 10;
        else totalScore -= 30;

        if (totalScore < 0) totalScore = 0;

        String level;
        String badgeClass;
        if (totalScore >= 75) {
            level = "CYBER ATHLETE: ELITE TIER";
            badgeClass = "badge-neon-green";
        } else if (totalScore >= 45) {
            level = "PRO ATHLETE: STANDARD TIER";
            badgeClass = "badge-neon-yellow";
        } else {
            level = "ROOKIE: ENTRY / HIGH RISK";
            badgeClass = "badge-neon-red";
        }

        StringBuilder rec = new StringBuilder("<ul class='futuristic-list'>");
        if (totalScore >= 75) {
            rec.append("<li>Полный доступ к прототипам спортивного инвентаря и экзокостюмов без залога.</li>");
            rec.append("<li>Персональная спонсорская линия и нулевой процент рассрочки на тренажеры.</li>");
        } else if (totalScore >= 45) {
            rec.append("<li>Одобрена клубная карта с лимитом на экипировку до 500,000 кредитов.</li>");
            if (hasOverdue) rec.append("<li>Требуется закрыть клубные задолженности для доступа к PRO-линейке.</li>");
            if (expMonths < 12) rec.append("<li>Подтвердите соревновательный стаж в федерации за последние 6 месяцев.</li>");
        } else {
            rec.append("<li>Доступен только базовый инвентарь для начинающих.</li>");
            rec.append("<li>Требуется поручительство сертифицированного тренера.</li>");
            if (creditScore < 600) rec.append("<li>Повышайте клубный рейтинг регулярными покупками мелкого инвентаря.</li>");
        }
        rec.append("</ul>");

        return "<div class='res-block neon-border'>" +
                "<div class='res-title'>КИБЕРНЕТИЧЕСКИЙ ПАСПОРТ АТЛЕТА // СКОРИНГ (ТЗ 25)</div>" +
                "<p><b>Интегральный рейтинг атлета:</b> <span class='fw-bold text-cyan'>" + totalScore + " / 100 PTS</span></p>" +
                "<p><b>Квалификационный грейд (ТЗ 23):</b> <span class='" + badgeClass + "'>" + level + "</span></p>" +
                "<p class='mb-1'><b>Директивы контракта (ТЗ 24):</b></p>" + rec.toString() +
                "</div>";
    }

    // --- Футуристичный кибер-интерфейс ---
    private static String renderPage(String alertMessage, String alertType, String resultOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>CYBER-ATHLETE // SPORT SYSTEMS</title>");
        sb.append("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
        sb.append("<link href='https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700;900&family=JetBrains+Mono:wght@400;600;800&display=swap' rel='stylesheet'>");
        
        sb.append("<style>");
        sb.append(":root {");
        sb.append("  --bg-color: #080c10;");
        sb.append("  --card-bg: rgba(13, 21, 32, 0.88);");
        sb.append("  --neon-cyan: #00f0ff;");
        sb.append("  --neon-green: #00ff88;");
        sb.append("  --neon-red: #ff0055;");
        sb.append("  --neon-yellow: #ffb703;");
        sb.append("  --border-glow: rgba(0, 240, 255, 0.25);");
        sb.append("  --text-main: #e0f2fe;");
        sb.append("}");
        sb.append("body {");
        sb.append("  background-color: var(--bg-color);");
        sb.append("  color: var(--text-main);");
        sb.append("  font-family: 'JetBrains Mono', monospace;");
        sb.append("  background-image: ");
        sb.append("    linear-gradient(rgba(0, 240, 255, 0.03) 1px, transparent 1px),");
        sb.append("    linear-gradient(90deg, rgba(0, 240, 255, 0.03) 1px, transparent 1px);");
        sb.append("  background-size: 32px 32px;");
        sb.append("  min-height: 100vh;");
        sb.append("}");
        sb.append(".cyber-card {");
        sb.append("  background: var(--card-bg);");
        sb.append("  border: 1px solid var(--border-glow);");
        sb.append("  box-shadow: 0 0 35px rgba(0, 240, 255, 0.12), inset 0 0 15px rgba(0, 240, 255, 0.05);");
        sb.append("  border-radius: 4px;");
        sb.append("  position: relative;");
        sb.append("  backdrop-filter: blur(8px);");
        sb.append("}");
        sb.append(".cyber-card::before {");
        sb.append("  content: ''; position: absolute; top: -1px; left: 10%; width: 80%; height: 2px;");
        sb.append("  background: linear-gradient(90deg, transparent, var(--neon-cyan), transparent);");
        sb.append("}");
        sb.append(".cyber-header {");
        sb.append("  font-family: 'Orbitron', sans-serif;");
        sb.append("  letter-spacing: 2px;");
        sb.append("  color: var(--neon-cyan);");
        sb.append("  text-shadow: 0 0 12px rgba(0, 240, 255, 0.6);");
        sb.append("  border-bottom: 1px solid rgba(0, 240, 255, 0.2);");
        sb.append("  padding: 16px 24px;");
        sb.append("}");
        sb.append(".form-control, .form-select {");
        sb.append("  background-color: #0b131e !important;");
        sb.append("  border: 1px solid #1a324b !important;");
        sb.append("  color: #a5f3fc !important;");
        sb.append("  border-radius: 2px;");
        sb.append("  font-size: 0.9rem;");
        sb.append("}");
        sb.append(".form-control:focus, .form-select:focus {");
        sb.append("  border-color: var(--neon-cyan) !important;");
        sb.append("  box-shadow: 0 0 10px rgba(0, 240, 255, 0.4) !important;");
        sb.append("}");
        sb.append(".nav-pills .nav-link {");
        sb.append("  color: #7dd3fc;");
        sb.append("  border: 1px solid #162c44;");
        sb.append("  margin: 3px;");
        sb.append("  border-radius: 2px;");
        sb.append("  font-size: 0.85rem;");
        sb.append("  text-transform: uppercase;");
        sb.append("  letter-spacing: 1px;");
        sb.append("  transition: all 0.2s;");
        sb.append("}");
        sb.append(".nav-pills .nav-link.active {");
        sb.append("  background-color: transparent !important;");
        sb.append("  color: #000 !important;");
        sb.append("  background: var(--neon-cyan) !important;");
        sb.append("  font-weight: 800;");
        sb.append("  box-shadow: 0 0 15px var(--neon-cyan);");
        sb.append("  border-color: var(--neon-cyan);");
        sb.append("}");
        sb.append(".btn-neon {");
        sb.append("  background: transparent;");
        sb.append("  color: var(--neon-cyan);");
        sb.append("  border: 1px solid var(--neon-cyan);");
        sb.append("  font-family: 'Orbitron', sans-serif;");
        sb.append("  letter-spacing: 1px;");
        sb.append("  text-transform: uppercase;");
        sb.append("  font-size: 0.85rem;");
        sb.append("  border-radius: 2px;");
        sb.append("  transition: all 0.2s;");
        sb.append("}");
        sb.append(".btn-neon:hover {");
        sb.append("  background: var(--neon-cyan);");
        sb.append("  color: #000;");
        sb.append("  box-shadow: 0 0 15px var(--neon-cyan);");
        sb.append("}");
        sb.append(".res-block {");
        sb.append("  background: rgba(4, 9, 15, 0.95);");
        sb.append("  border-left: 3px solid var(--neon-cyan);");
        sb.append("  padding: 16px;");
        sb.append("  margin-bottom: 20px;");
        sb.append("}");
        sb.append(".res-title {");
        sb.append("  font-family: 'Orbitron', sans-serif;");
        sb.append("  color: var(--neon-cyan);");
        sb.append("  font-size: 1rem;");
        sb.append("  margin-bottom: 12px;");
        sb.append("  letter-spacing: 1px;");
        sb.append("}");
        sb.append(".badge-neon-green { color: #000; background: var(--neon-green); padding: 3px 8px; border-radius: 2px; font-weight: 700; }");
        sb.append(".badge-neon-red { color: #fff; background: var(--neon-red); padding: 3px 8px; border-radius: 2px; font-weight: 700; }");
        sb.append(".badge-neon-yellow { color: #000; background: var(--neon-yellow); padding: 3px 8px; border-radius: 2px; font-weight: 700; }");
        sb.append(".badge-neon-cyan { color: #000; background: var(--neon-cyan); padding: 3px 8px; border-radius: 2px; font-weight: 700; }");
        sb.append(".futuristic-list { padding-left: 20px; margin: 8px 0 0 0; }");
        sb.append(".futuristic-list li { margin-bottom: 4px; color: #a5f3fc; }");
        sb.append(".text-cyan { color: var(--neon-cyan); }");
        sb.append("</style>");
        sb.append("</head><body class='py-4'><div class='container' style='max-width: 950px;'>");

        // Карточка приложения
        sb.append("<div class='cyber-card mb-4'>");
        sb.append("<div class='cyber-header d-flex justify-content-between align-items-center'>");
        sb.append("<div><span style='font-size:0.75rem; color:#64748b; display:block;'>CORE TERMINAL // DEPT 409</span>CYBER_ATHLETE :: SPORT OS</div>");
        if (isAuthenticated) {
            sb.append("<form method='POST' class='m-0'><input type='hidden' name='action' value='logout'>");
            sb.append("<button class='btn btn-neon btn-sm'>DISCONNECT [admin]</button></form>");
        }
        sb.append("</div><div class='card-body p-4'>");

        if (!alertMessage.isEmpty()) {
            String alertClass = "alert-info";
            if ("danger".equals(alertType)) alertClass = "alert-danger bg-black text-danger border-danger";
            else if ("success".equals(alertType)) alertClass = "alert-success bg-black text-success border-success";
            sb.append("<div class='alert ").append(alertClass).append(" border'>").append(alertMessage).append("</div>");
        }

        if (!isAuthenticated) {
            sb.append("<h5 class='font-orbitron text-cyan mb-2'>АВТОРИЗАЦИЯ ОПЕРАТОРА (ТЗ 5)</h5>");
            sb.append("<p style='color:#64748b; font-size:0.85rem;'>Вход в распределенную базу данных смарт-экипировки. Тестовый ключ: <b>admin / 1234</b></p>");
            sb.append("<form method='POST'>");
            sb.append("<input type='hidden' name='action' value='login'>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Идентификатор:</label>");
            sb.append("<input type='text' name='login' class='form-control' placeholder='ID оператора' required></div>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Ключ шифрования:</label>");
            sb.append("<input type='password' name='password' class='form-control' placeholder='••••••••' required></div>");
            sb.append("<button type='submit' class='btn btn-neon mt-2'>ИНИЦИАЛИЗИРОВАТЬ СЕССИЮ</button>");
            sb.append("</form>");
        } else {
            sb.append("<ul class='nav nav-pills mb-4' id='pills-tab' role='tablist'>");
            sb.append("<li class='nav-item'><button class='nav-link active' data-bs-toggle='pill' data-bs-target='#tab1'>01 // АТЛЕТ (1-5)</button></li>");
            sb.append("<li class='nav-item'><button class='nav-link' data-bs-toggle='pill' data-bs-target='#tab2'>02 // ДОПУСК (6-10)</button></li>");
            sb.append("<li class='nav-item'><button class='nav-link' data-bs-toggle='pill' data-bs-target='#tab3'>03 // КОРЗИНА (11-15)</button></li>");
            sb.append("<li class='nav-item'><button class='nav-link' data-bs-toggle='pill' data-bs-target='#tab4'>04 // КАТАЛОГ (16-20)</button></li>");
            sb.append("<li class='nav-item'><button class='nav-link' data-bs-toggle='pill' data-bs-target='#tab5'>05 // СКОРИНГ (21-25)</button></li>");
            sb.append("</ul>");

            if (!resultOutput.isEmpty()) {
                sb.append(resultOutput);
            }

            sb.append("<div class='tab-content' id='pills-tabContent'>");

            // Раздел 1
            sb.append("<div class='tab-pane fade show active' id='tab1'>");
            sb.append("<form method='POST'><input type='hidden' name='action' value='module1'>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Позывной атлета (ТЗ 2):</label><input type='text' name='name' class='form-control' value='NeoRunner_01'></div>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Статус контрагента (ТЗ 3):</label>");
            sb.append("<select name='status' class='form-select'><option value='active'>Активный контракт</option><option value='inactive'>В резерве</option><option value='banned'>Дисквалифицирован</option></select></div>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Индекс выносливости / силы (0-100) (ТЗ 4):</label><input type='number' name='metric' class='form-control' value='82'></div>");
            sb.append("<button class='btn btn-neon mt-2'>ПРОВЕРИТЬ БИО-ДАННЫЕ [ТЗ 1-5]</button></form></div>");

            // Раздел 2
            sb.append("<div class='tab-pane fade' id='tab2'>");
            sb.append("<form method='POST'><input type='hidden' name='action' value='module2'>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Биологический возраст (ТЗ 6):</label><input type='number' name='age' class='form-control' value='24'></div>");
            sb.append("<div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='hasPassport' id='c1' checked><label class='form-check-label' for='c1'>Смарт-ID атлета подтвержден (&&, ТЗ 7)</label></div>");
            sb.append("<div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='feePaid' id='c2' checked><label class='form-check-label' for='c2'>Членский взнос гильдии оплачен (&&, ТЗ 7)</label></div>");
            sb.append("<div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='isStudent' id='c3'><label class='form-check-label' for='c3'>Студенческая спортивная лига (||, ТЗ 8)</label></div>");
            sb.append("<div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='isVeteran' id='c4'><label class='form-check-label' for='c4'>Ветеран соревнований PRO (||, ТЗ 8)</label></div>");
            sb.append("<div class='form-check mb-3'><input class='form-check-input' type='checkbox' name='isBanned' id='c5'><label class='form-check-label' for='c5'>Черный список / Допинг-бан (ТЗ 9: !)</label></div>");
            sb.append("<div class='text-cyan fw-bold mb-2'>Вложенные контуры сканирования (ТЗ 10):</div>");
            sb.append("<div class='form-check'><input class='form-check-input' type='checkbox' name='step1' id='s1' checked><label class='form-check-label' for='s1'>Контур 1: 3D-сканирование тела</label></div>");
            sb.append("<div class='form-check'><input class='form-check-input' type='checkbox' name='step2' id='s2' checked><label class='form-check-label' for='s2'>Контур 2: Чип NFC экипировки</label></div>");
            sb.append("<div class='form-check mb-3'><input class='form-check-input' type='checkbox' name='step3' id='s3' checked><label class='form-check-label' for='s3'>Контур 3: Синхронизация смарт-браслета</label></div>");
            sb.append("<button class='btn btn-neon'>ВЕРИФИЦИРОВАТЬ ДОПУСК [ТЗ 6-10]</button></form></div>");

            // Раздел 3
            sb.append("<div class='tab-pane fade' id='tab3'>");
            sb.append("<form method='POST'><input type='hidden' name='action' value='module3'>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Сумма корзины товаров (ТЗ 11):</label><input type='number' name='purchase' class='form-control' value='7500'></div>");
            sb.append("<div class='col d-flex align-items-end'><div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='isVip' id='vip' checked><label class='form-check-label' for='vip'>VIP-карта ProAthlete</label></div></div></div>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Спонсорский бюджет (ТЗ 12):</label><input type='number' name='income' class='form-control' value='120000'></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Расходы на тренировки (ТЗ 12):</label><input type='number' name='expenses' class='form-control' value='45000'></div></div>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Тест-норматив (0-100) (ТЗ 13):</label><input type='number' name='score' class='form-control' value='92'></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Ошибок терминала (ТЗ 14):</label><input type='number' name='failedLogins' class='form-control' value='0'></div></div>");
            sb.append("<div class='my-2'><div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' name='newLoc' id='nl'><label class='form-check-label' for='nl'>Новая арена (геолокация)</label></div>");
            sb.append("<div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' name='vpn' id='vpn'><label class='form-check-label' for='vpn'>Зашифрованный туннель</label></div></div>");
            sb.append("<div class='mb-3'><label class='form-label text-cyan'>Целевая программа апгрейда (ТЗ 15):</label><select name='goal' class='form-select'><option value='1'>Базовое восстановление</option><option value='2'>Прирост мышечной мощности</option><option value='3'>Максимальная защита суставов</option></select></div>");
            sb.append("<button class='btn btn-neon'>РАССЧИТАТЬ СТОИМОСТЬ И БЮДЖЕТ [ТЗ 11-15]</button></form></div>");

            // Раздел 4
            sb.append("<div class='tab-pane fade' id='tab4'>");
            sb.append("<form method='POST'><input type='hidden' name='action' value='module4'>");
            sb.append("<div class='mb-2'><label class='form-label text-cyan'>Категория экипировки (ТЗ 17 switch):</label><select name='serviceCat' class='form-select'><option value='1'>1. Hardware & Экзоскелеты</option><option value='2'>2. Умная компрессионная одежда</option><option value='3'>3. Молекулярный спортпит</option></select></div>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Уровень комплекта (ТЗ 18):</label><select name='plan' class='form-select'><option value='1'>Базовый комплект (1,000 кр)</option><option value='2'>Стандартный Pro (2,500 кр)</option><option value='3'>Cyber Elite (5,000 кр)</option></select></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Количество единиц:</label><input type='number' name='units' class='form-control' value='2'></div>");
            sb.append("<div class='col d-flex align-items-end'><div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='isUrgent' id='urg' checked><label class='form-check-label' for='urg'>Дрон-экспресс (+50%)</label></div></div></div>");
            sb.append("<div class='text-cyan fw-bold mt-3 mb-2'>Комплексный логистический шлюз (ТЗ 20 switch + if-else):</div>");
            sb.append("<div class='row mb-3'><div class='col'><label class='form-label text-cyan'>Тип контракта:</label><select name='opType' class='form-select'><option value='A'>Прямой выкуп (A)</option><option value='B'>Спортивный лизинг (B)</option><option value='C'>Клубный депозит (C)</option></select></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Сумма транзакции:</label><input type='number' name='amount' class='form-control' value='180000'></div>");
            sb.append("<div class='col d-flex align-items-end'><div class='form-check mb-2'><input class='form-check-input' type='checkbox' name='isResident' id='res' checked><label class='form-check-label' for='res'>Клубный резидент</label></div></div></div>");
            sb.append("<button class='btn btn-neon'>СФОРМИРОВАТЬ ОРДЕР [ТЗ 16-20]</button></form></div>");

            // Раздел 5
            sb.append("<div class='tab-pane fade' id='tab5'>");
            sb.append("<form method='POST'><input type='hidden' name='action' value='module5'>");
            sb.append("<p style='color:#64748b; font-size:0.85rem;'>Индивидуальный модуль: Оценка благонадежности и скоринг контракта атлета (ТЗ 21-25)</p>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Возраст атлета:</label><input type='number' name='scAge' class='form-control' value='26'></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Спонсорский доход (кр/мес):</label><input type='number' name='scSalary' class='form-control' value='150000'></div></div>");
            sb.append("<div class='row mb-2'><div class='col'><label class='form-label text-cyan'>Спортивный индекс надежности (300-850):</label><input type='number' name='scScore' class='form-control' value='780'></div>");
            sb.append("<div class='col'><label class='form-label text-cyan'>Соревновательный стаж (мес):</label><input type='number' name='scExp' class='form-control' value='48'></div></div>");
            sb.append("<div class='form-check mb-3'><input class='form-check-input' type='checkbox' name='hasOverdue' id='ovd'><label class='form-check-label' for='ovd'>Имеются невыполненные контракты / штрафы</label></div>");
            sb.append("<button class='btn btn-neon'>ГЕНЕРАЦИЯ КИБЕР-СКОРИНГА [ТЗ 21-25]</button></form></div>");

            sb.append("</div>");
        }

        sb.append("</div></div></div>");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js'></script>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String errorHtml(String msg) {
        return "<div class='res-block' style='border-left-color: var(--neon-red);'>" +
                "<div style='color: var(--neon-red); font-weight: bold;'>[КРИТИЧЕСКАЯ ОШИБКА ДАННЫХ]</div>" +
                "<div class='text-danger'>" + escape(msg) + "</div></div>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        if (formData == null || formData.isEmpty()) return map;
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, val);
        }
        return map;
    }
}