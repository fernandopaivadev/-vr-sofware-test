package com.teste.desktop;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OrderDesktopApp extends JFrame {

    private static final String BASE_URL = "http://localhost:8080/api/pedidos";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JTextField idField = new JTextField(32);
    private final JTextField productField = new JTextField(20);
    private final JTextField quantityField = new JTextField(6);
    private final JButton sendButton = new JButton("Enviar Pedido");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Produto", "Qtd", "Data Criação", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    private final Map<UUID, Integer> pendingRows = new HashMap<>();
    private final Timer timer = new Timer("PollingStatus", true);

    public OrderDesktopApp() {
        super("Sistema de Pedidos - Desktop");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(900, 450);
        setLocationRelativeTo(null);

        newId();
        sendButton.addActionListener(e -> submitOrder());
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pollStatuses();
            }
        }, 4000, 4000);
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel idRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        idRow.add(new JLabel("ID (UUID):"));
        idRow.add(idField);
        JButton newIdButton = new JButton("Novo ID");
        newIdButton.addActionListener(e -> newId());
        idRow.add(newIdButton);
        panel.add(idRow);

        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        formRow.add(new JLabel("Produto:"));
        formRow.add(productField);
        formRow.add(new JLabel("Quantidade:"));
        formRow.add(quantityField);
        formRow.add(sendButton);
        panel.add(formRow);

        return panel;
    }

    private void newId() {
        idField.setText(UUID.randomUUID().toString());
        idField.setToolTipText("Deixe o novo ID ou cole um existente para acompanhar");
    }

    private void submitOrder() {
        String idText = idField.getText().trim();
        String product = productField.getText().trim();
        String quantityText = quantityField.getText().trim();

        try {
            UUID.fromString(idText);
            int quantity = Integer.parseInt(quantityText);
            Map<String, Object> order = new HashMap<>();
            order.put("id", idText);
            order.put("produto", product);
            order.put("quantidade", quantity);
            order.put("dataCriacao", LocalDateTime.now().toString());
            String json = MAPPER.writeValueAsString(order);

            Response response = HttpClient.post(BASE_URL, json);
            if (response.code >= 200 && response.code < 300) {
                tableModel.addRow(new Object[]{idText, product, quantity, LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        "ENVIADO, AGUARDANDO PROCESSO"});
                pendingRows.put(UUID.fromString(idText), tableModel.getRowCount() - 1);
            } else {
                showError("Requisição recusada pelo servidor (" + response.code + "): " + response.body);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "ID inválido ou quantidade não numérica.",
                    "Erro de validação", JOptionPane.WARNING_MESSAGE);
        } catch (IOException e) {
            showError("Não foi possível enviar o pedido. Servidor offline ou inacessível?");
        }
    }

    private void pollStatuses() {
        if (pendingRows.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : pendingRows.entrySet()) {
            UUID id = entry.getKey();
            int row = entry.getValue();
            try {
                Response response = HttpClient.get(BASE_URL + "/status/" + id);
                if (response.code == 200) {
                    JsonNode status = MAPPER.readTree(response.body);
                    String value = status.path("status").asText();
                    if (Status.SUCCESS.equals(value) || Status.FAILURE.equals(value)) {
                        updateRow(row, value);
                        pendingRows.remove(id);
                    }
                }
            } catch (IOException e) {
                updateRow(row, "ERRO AO CONSULTAR");
            }
        }
    }

    private void updateRow(final int row, final String status) {
        SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, row, 4));
    }

    private void showError(final String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this, message, "Falha na requisição", JOptionPane.ERROR_MESSAGE));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OrderDesktopApp().setVisible(true));
    }

    private static final class Status {
        static final String SUCCESS = "SUCESSO";
        static final String FAILURE = "FALHA";
    }

    private static final class HttpClient {

        private static Response execute(String method, String url, String jsonBody) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (jsonBody != null) {
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String body = read(stream);
                return new Response(code, body);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static String read(InputStream stream) throws IOException {
            if (stream == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (Reader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            }
            return sb.toString();
        }

        static Response post(String url, String json) throws IOException {
            return execute("POST", url, json);
        }

        static Response get(String url) throws IOException {
            return execute("GET", url, null);
        }
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}