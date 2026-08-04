package org.distribuidos.chat.client.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import org.distribuidos.chat.client.ClientMain;
import org.distribuidos.chat.shared.FileStartMessage;
import org.distribuidos.chat.shared.TextMessage;

public class SwingClientMain {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equalsIgnoreCase(args[0])) {
            ClientMain.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta invalida. Utilizando porta padrao " + DEFAULT_PORT);
            }
        }

        final String serverHost = host;
        final int serverPort = port;

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Keep the default Swing look and feel.
            }
            new ChatFrame(serverHost, serverPort).setVisible(true);
        });
    }

    private static class ChatFrame extends JFrame implements GuiChatClient.ClientEvents {
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

        private final String host;
        private final int port;
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel root = new JPanel(cardLayout);

        private JTextField stateField;
        private JTextField organizationField;
        private JTextField userField;
        private JLabel loginStatusLabel;
        private JButton loginButton;

        private JLabel currentUserLabel;
        private JLabel clockLabel;
        private JTextArea conversationArea;
        private DefaultListModel<String> onlineUsersModel;
        private JList<String> onlineUsersList;
        private JLabel onlineUsersStatusLabel;
        private JTextField directRecipientField;
        private JTextField directMessageField;
        private JTextField groupField;
        private JTextField groupMessageField;
        private JTextField fileRecipientField;
        private JLabel selectedFileLabel;
        private JProgressBar fileProgressBar;
        private JButton sendFileButton;

        private File selectedFile;
        private GuiChatClient client;
        private Timer onlineUsersTimer;
        private boolean updatingOnlineUsers;

        ChatFrame(String host, int port) {
            super("ChatGov - Cliente");
            this.host = host;
            this.port = port;

            setMinimumSize(new Dimension(760, 560));
            setSize(880, 640);
            setLocationByPlatform(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setJMenuBar(createMenuBar());
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    stopOnlineUsersRefresh();
                    if (client != null) {
                        client.logout();
                    }
                }

                @Override
                public void windowClosed(WindowEvent e) {
                    if (!hasDisplayableFrames()) {
                        System.exit(0);
                    }
                }
            });

            root.add(createLoginPanel(), "login");
            root.add(createChatPanel(), "chat");
            setContentPane(root);
            cardLayout.show(root, "login");
        }

        private JMenuBar createMenuBar() {
            JMenuBar menuBar = new JMenuBar();
            JMenu clientMenu = new JMenu("Cliente");

            JMenuItem newWindowItem = new JMenuItem("Novo cliente");
            newWindowItem.addActionListener(event -> new ChatFrame(host, port).setVisible(true));

            JMenuItem exitItem = new JMenuItem("Sair");
            exitItem.addActionListener(event -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

            clientMenu.add(newWindowItem);
            clientMenu.add(exitItem);
            menuBar.add(clientMenu);
            return menuBar;
        }

        private JPanel createLoginPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(32, 32, 32, 32));

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 214, 220)),
                    new EmptyBorder(24, 24, 24, 24)
            ));

            JLabel title = new JLabel("Login ChatGov");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));

            JLabel serverLabel = new JLabel("Servidor: " + host + ":" + port);
            serverLabel.setForeground(new Color(85, 91, 102));

            stateField = new JTextField(12);
            organizationField = new JTextField(16);
            userField = new JTextField(18);
            loginStatusLabel = new JLabel("Informe estado, orgao e usuario.");
            loginStatusLabel.setForeground(new Color(85, 91, 102));
            loginButton = new JButton("Entrar");
            loginButton.addActionListener(event -> attemptLogin());
            getRootPane().setDefaultButton(loginButton);

            int row = 0;
            addFormField(form, title, 0, row++, 2);
            addFormField(form, serverLabel, 0, row++, 2);
            addLabeledField(form, "Estado", stateField, row++);
            addLabeledField(form, "Orgao", organizationField, row++);
            addLabeledField(form, "Usuario", userField, row++);
            addFormField(form, loginStatusLabel, 0, row++, 2);

            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.gridx = 0;
            buttonConstraints.gridy = row;
            buttonConstraints.gridwidth = 2;
            buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
            buttonConstraints.insets = new Insets(14, 0, 0, 0);
            form.add(loginButton, buttonConstraints);

            GridBagConstraints panelConstraints = new GridBagConstraints();
            panelConstraints.gridx = 0;
            panelConstraints.gridy = 0;
            panelConstraints.weightx = 1;
            panelConstraints.weighty = 1;
            panelConstraints.anchor = GridBagConstraints.CENTER;
            panel.add(form, panelConstraints);
            return panel;
        }

        private JPanel createChatPanel() {
            JPanel panel = new JPanel(new BorderLayout(12, 12));
            panel.setBorder(new EmptyBorder(14, 14, 14, 14));

            JPanel header = new JPanel(new BorderLayout());
            currentUserLabel = new JLabel("Usuario: -");
            currentUserLabel.setFont(currentUserLabel.getFont().deriveFont(Font.BOLD, 15f));
            clockLabel = new JLabel("Lamport: 0");
            clockLabel.setForeground(new Color(85, 91, 102));
            header.add(currentUserLabel, BorderLayout.WEST);
            header.add(clockLabel, BorderLayout.EAST);

            conversationArea = new JTextArea();
            conversationArea.setEditable(false);
            conversationArea.setLineWrap(true);
            conversationArea.setWrapStyleWord(true);
            conversationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            JScrollPane scrollPane = new JScrollPane(conversationArea);

            panel.add(header, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);
            panel.add(createOnlineUsersPanel(), BorderLayout.EAST);
            panel.add(createActionsTabs(), BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createOnlineUsersPanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setPreferredSize(new Dimension(220, 0));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 214, 220)),
                    new EmptyBorder(10, 10, 10, 10)
            ));

            JLabel title = new JLabel("Usuarios conectados");
            title.setFont(title.getFont().deriveFont(Font.BOLD));

            onlineUsersModel = new DefaultListModel<>();
            onlineUsersList = new JList<>(onlineUsersModel);
            onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            onlineUsersList.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting() && !updatingOnlineUsers) {
                    String selectedUser = onlineUsersList.getSelectedValue();
                    if (selectedUser != null) {
                        selectRecipient(selectedUser);
                    }
                }
            });
            onlineUsersList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    String selectedUser = onlineUsersList.getSelectedValue();
                    if (selectedUser != null) {
                        selectRecipient(selectedUser);
                    }
                }
            });

            onlineUsersStatusLabel = new JLabel("Aguardando login");
            onlineUsersStatusLabel.setForeground(new Color(85, 91, 102));

            JButton refreshButton = new JButton("Atualizar");
            refreshButton.addActionListener(event -> refreshOnlineUsers());

            JPanel footer = new JPanel(new BorderLayout(6, 6));
            footer.add(onlineUsersStatusLabel, BorderLayout.CENTER);
            footer.add(refreshButton, BorderLayout.EAST);

            panel.add(title, BorderLayout.NORTH);
            panel.add(new JScrollPane(onlineUsersList), BorderLayout.CENTER);
            panel.add(footer, BorderLayout.SOUTH);
            return panel;
        }

        private JTabbedPane createActionsTabs() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Mensagem", createDirectMessagePanel());
            tabs.addTab("Grupo", createGroupPanel());
            tabs.addTab("Arquivo", createFilePanel());
            return tabs;
        }

        private JPanel createDirectMessagePanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            directRecipientField = new JTextField();
            directMessageField = new JTextField();
            JButton sendButton = new JButton("Enviar");
            sendButton.addActionListener(event -> sendDirectMessage());
            directMessageField.addActionListener(event -> sendDirectMessage());

            panel.add(labeledPanel("Destino", directRecipientField), BorderLayout.WEST);
            panel.add(labeledPanel("Mensagem", directMessageField), BorderLayout.CENTER);
            panel.add(sendButton, BorderLayout.EAST);
            return panel;
        }

        private JPanel createGroupPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel joinRow = new JPanel(new BorderLayout(8, 8));
            groupField = new JTextField();
            JButton joinButton = new JButton("Entrar no grupo");
            joinButton.addActionListener(event -> joinGroup());
            joinRow.add(labeledPanel("Grupo", groupField), BorderLayout.CENTER);
            joinRow.add(joinButton, BorderLayout.EAST);

            JPanel broadcastRow = new JPanel(new BorderLayout(8, 8));
            groupMessageField = new JTextField();
            JButton broadcastButton = new JButton("Broadcast");
            broadcastButton.addActionListener(event -> sendBroadcast());
            groupMessageField.addActionListener(event -> sendBroadcast());
            broadcastRow.add(labeledPanel("Mensagem", groupMessageField), BorderLayout.CENTER);
            broadcastRow.add(broadcastButton, BorderLayout.EAST);

            panel.add(joinRow);
            panel.add(Box.createVerticalStrut(8));
            panel.add(broadcastRow);
            return panel;
        }

        private JPanel createFilePanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            fileRecipientField = new JTextField();
            selectedFileLabel = new JLabel("Nenhum arquivo selecionado");
            fileProgressBar = new JProgressBar(0, 100);
            fileProgressBar.setStringPainted(true);
            fileProgressBar.setString("Aguardando");

            JButton chooseFileButton = new JButton("Escolher");
            chooseFileButton.addActionListener(event -> chooseFile());

            sendFileButton = new JButton("Enviar arquivo");
            sendFileButton.setEnabled(false);
            sendFileButton.addActionListener(event -> sendSelectedFile());

            JPanel left = new JPanel(new BorderLayout(8, 8));
            left.add(labeledPanel("Destino", fileRecipientField), BorderLayout.NORTH);
            left.add(selectedFileLabel, BorderLayout.CENTER);
            left.add(fileProgressBar, BorderLayout.SOUTH);

            JPanel right = new JPanel(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(0, 0, 8, 0);
            right.add(chooseFileButton, constraints);
            constraints.gridy = 1;
            constraints.insets = new Insets(0, 0, 0, 0);
            right.add(sendFileButton, constraints);

            panel.add(left, BorderLayout.CENTER);
            panel.add(right, BorderLayout.EAST);
            return panel;
        }

        private void attemptLogin() {
            String state = normalizeIdentifierPart(stateField.getText());
            String organization = normalizeIdentifierPart(organizationField.getText());
            String user = normalizeIdentifierPart(userField.getText());

            if (state.isEmpty() || organization.isEmpty() || user.isEmpty()) {
                showLoginStatus("Preencha os tres campos para formar estado.orgao.usuario.", true);
                return;
            }

            String username = state + "." + organization + "." + user;
            setLoginBusy(true);
            showLoginStatus("Conectando e enviando login como " + username + "...", false);

            Thread loginThread = new Thread(() -> {
                try {
                    GuiChatClient nextClient = new GuiChatClient(this);
                    client = nextClient;
                    nextClient.connect(host, port);
                    nextClient.login(username);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        setLoginBusy(false);
                        showLoginStatus("Falha no login: " + e.getMessage(), true);
                    });
                }
            }, "GuiLoginThread");
            loginThread.setDaemon(true);
            loginThread.start();
        }

        private void sendDirectMessage() {
            String recipient = normalizeFullIdentifier(directRecipientField.getText());
            String content = directMessageField.getText().trim();
            if (recipient.isEmpty() || content.isEmpty()) {
                appendSystem("Informe destino e mensagem.");
                return;
            }

            try {
                client.sendText(recipient, content);
                appendOwnMessage(recipient, content);
                directMessageField.setText("");
                updateClockLabel();
            } catch (Exception e) {
                appendError(e.getMessage());
            }
        }

        private void joinGroup() {
            String group = normalizeFullIdentifier(groupField.getText());
            if (group.isEmpty()) {
                appendSystem("Informe o nome do grupo.");
                return;
            }

            try {
                client.joinGroup(group);
                appendSystem("Solicitando entrada no grupo " + group + "...");
                updateClockLabel();
            } catch (Exception e) {
                appendError(e.getMessage());
            }
        }

        private void sendBroadcast() {
            String group = normalizeFullIdentifier(groupField.getText());
            String content = groupMessageField.getText().trim();
            if (group.isEmpty() || content.isEmpty()) {
                appendSystem("Informe grupo e mensagem.");
                return;
            }

            try {
                client.sendBroadcast(group, content);
                appendOwnBroadcast(group, content);
                groupMessageField.setText("");
                updateClockLabel();
            } catch (Exception e) {
                appendError(e.getMessage());
            }
        }

        private void chooseFile() {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
                selectedFileLabel.setText(selectedFile.getName() + " (" + selectedFile.length() + " bytes)");
                sendFileButton.setEnabled(true);
                fileProgressBar.setValue(0);
                fileProgressBar.setString("Pronto para envio");
            }
        }

        private void sendSelectedFile() {
            String recipient = normalizeFullIdentifier(fileRecipientField.getText());
            if (recipient.isEmpty()) {
                appendSystem("Informe o destino do arquivo.");
                return;
            }
            if (selectedFile == null) {
                appendSystem("Escolha um arquivo antes de enviar.");
                return;
            }

            try {
                fileProgressBar.setIndeterminate(true);
                fileProgressBar.setString("Enviando...");
                client.sendFile(recipient, selectedFile);
                appendSystem("Enviando arquivo '" + selectedFile.getName() + "' para " + recipient + ".");
            } catch (Exception e) {
                fileProgressBar.setIndeterminate(false);
                fileProgressBar.setString("Falha");
                appendError(e.getMessage());
            }
        }

        @Override
        public void onConnected(String host, int port) {
            SwingUtilities.invokeLater(() -> showLoginStatus("Conectado em " + host + ":" + port + ".", false));
        }

        @Override
        public void onLoginAccepted(String username, String message) {
            SwingUtilities.invokeLater(() -> {
                setLoginBusy(false);
                currentUserLabel.setText("Usuario: " + username);
                updateClockLabel();
                cardLayout.show(root, "chat");
                appendSystem(message);
                appendSystem("Grupo nacional.geral ja fica disponivel apos o login.");
                getRootPane().setDefaultButton(null);
                startOnlineUsersRefresh();
            });
        }

        @Override
        public void onAck(String message) {
            SwingUtilities.invokeLater(() -> {
                if (fileProgressBar.isIndeterminate() && message.startsWith("Arquivo '")) {
                    fileProgressBar.setIndeterminate(false);
                    fileProgressBar.setValue(100);
                    fileProgressBar.setString("Enviado");
                }
                appendSystem("ACK: " + message);
                updateClockLabel();
            });
        }

        @Override
        public void onError(String message) {
            SwingUtilities.invokeLater(() -> {
                if (isLoginVisible()) {
                    setLoginBusy(false);
                    showLoginStatus("Erro: " + message, true);
                } else {
                    if (fileProgressBar.isIndeterminate()) {
                        fileProgressBar.setIndeterminate(false);
                        fileProgressBar.setString("Falha");
                    }
                    appendError(message);
                }
            });
        }

        @Override
        public void onTextMessage(TextMessage message, long localLamportClock) {
            SwingUtilities.invokeLater(() -> {
                appendIncoming("Privado", message.getSender(), message.getContent(), localLamportClock);
                updateClockLabel();
            });
        }

        @Override
        public void onBroadcastMessage(TextMessage message, long localLamportClock) {
            SwingUtilities.invokeLater(() -> {
                appendIncoming("Grupo " + message.getRecipient(), message.getSender(), message.getContent(), localLamportClock);
                updateClockLabel();
            });
        }

        @Override
        public void onFileStart(FileStartMessage message) {
            SwingUtilities.invokeLater(() -> {
                appendSystem("Recebendo arquivo '" + message.getFileName() + "' de " + message.getSender()
                        + " (" + message.getFileSize() + " bytes).");
                fileProgressBar.setIndeterminate(false);
                fileProgressBar.setValue(0);
                fileProgressBar.setString("Recebendo " + message.getFileName());
                updateClockLabel();
            });
        }

        @Override
        public void onFileProgress(String transferId, String fileName, long receivedBytes, long totalBytes) {
            SwingUtilities.invokeLater(() -> {
                int percentage = totalBytes <= 0 ? 100 : (int) Math.min(100, receivedBytes * 100 / totalBytes);
                fileProgressBar.setValue(percentage);
                fileProgressBar.setString("Recebendo " + percentage + "%");
            });
        }

        @Override
        public void onFileComplete(String transferId, String fileName, File file) {
            SwingUtilities.invokeLater(() -> {
                fileProgressBar.setValue(100);
                fileProgressBar.setString("Recebido");
                appendSystem("Download finalizado: " + file.getPath());
            });
        }

        @Override
        public void onOnlineUsers(List<String> users) {
            SwingUtilities.invokeLater(() -> updateOnlineUsers(users));
        }

        @Override
        public void onDisconnected(String message) {
            SwingUtilities.invokeLater(() -> {
                stopOnlineUsersRefresh();
                if (isLoginVisible()) {
                    setLoginBusy(false);
                    showLoginStatus(message, true);
                } else {
                    appendError(message);
                }
            });
        }

        private void startOnlineUsersRefresh() {
            refreshOnlineUsers();
            onlineUsersTimer = new Timer(3000, event -> refreshOnlineUsers());
            onlineUsersTimer.start();
        }

        private void stopOnlineUsersRefresh() {
            if (onlineUsersTimer != null) {
                onlineUsersTimer.stop();
                onlineUsersTimer = null;
            }
        }

        private void refreshOnlineUsers() {
            if (client == null || client.getUsername() == null) {
                return;
            }

            Thread refreshThread = new Thread(() -> {
                try {
                    client.requestOnlineUsers();
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> appendError("Falha ao atualizar usuarios online: " + e.getMessage()));
                }
            }, "GuiOnlineUsersRefreshThread");
            refreshThread.setDaemon(true);
            refreshThread.start();
        }

        private void updateOnlineUsers(List<String> users) {
            String previousSelection = onlineUsersList.getSelectedValue();
            updatingOnlineUsers = true;
            try {
                onlineUsersModel.clear();

                String currentUsername = client != null ? client.getUsername() : null;
                for (String user : users) {
                    if (!user.equals(currentUsername)) {
                        onlineUsersModel.addElement(user);
                    }
                }

                if (previousSelection != null && onlineUsersModel.contains(previousSelection)) {
                    onlineUsersList.setSelectedValue(previousSelection, true);
                }
            } finally {
                updatingOnlineUsers = false;
            }

            int otherUsers = onlineUsersModel.size();
            onlineUsersStatusLabel.setText(otherUsers == 1 ? "1 outro usuario" : otherUsers + " outros usuarios");
        }

        private void selectRecipient(String username) {
            directRecipientField.setText(username);
            fileRecipientField.setText(username);
            directMessageField.requestFocusInWindow();
        }

        private void appendOwnMessage(String recipient, String content) {
            appendLine("[" + timeNow() + "] [Lamport: " + client.getLamportClock() + "] Eu -> " + recipient + ": " + content);
        }

        private void appendOwnBroadcast(String group, String content) {
            appendLine("[" + timeNow() + "] [Lamport: " + client.getLamportClock() + "] Eu -> [" + group + "]: " + content);
        }

        private void appendIncoming(String scope, String sender, String content, long clock) {
            appendLine("[" + timeNow() + "] [Lamport: " + clock + "] [" + scope + "] " + sender + ": " + content);
        }

        private void appendSystem(String message) {
            appendLine("[" + timeNow() + "] [Sistema] " + message);
        }

        private void appendError(String message) {
            appendLine("[" + timeNow() + "] [Erro] " + message);
        }

        private void appendLine(String line) {
            conversationArea.append(line + System.lineSeparator());
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        }

        private void updateClockLabel() {
            if (client != null) {
                clockLabel.setText("Lamport: " + client.getLamportClock());
            }
        }

        private void setLoginBusy(boolean busy) {
            loginButton.setEnabled(!busy);
            stateField.setEnabled(!busy);
            organizationField.setEnabled(!busy);
            userField.setEnabled(!busy);
        }

        private void showLoginStatus(String message, boolean error) {
            loginStatusLabel.setText(message);
            loginStatusLabel.setForeground(error ? new Color(168, 46, 46) : new Color(85, 91, 102));
        }

        private boolean isLoginVisible() {
            for (Component component : root.getComponents()) {
                if (component.isVisible()) {
                    return component != root.getComponent(1);
                }
            }
            return true;
        }

        private String normalizeIdentifierPart(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private String normalizeFullIdentifier(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private String timeNow() {
            return LocalTime.now().format(TIME_FORMAT);
        }

        private JPanel labeledPanel(String label, JTextField field) {
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            JLabel jLabel = new JLabel(label);
            jLabel.setForeground(new Color(70, 76, 86));
            panel.add(jLabel, BorderLayout.NORTH);
            panel.add(field, BorderLayout.CENTER);
            return panel;
        }

        private void addLabeledField(JPanel panel, String label, JTextField field, int row) {
            JLabel jLabel = new JLabel(label);
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.WEST;
            labelConstraints.insets = new Insets(8, 0, 4, 14);
            panel.add(jLabel, labelConstraints);

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row;
            fieldConstraints.weightx = 1;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.insets = new Insets(8, 0, 4, 0);
            panel.add(field, fieldConstraints);
        }

        private void addFormField(JPanel panel, Component component, int x, int y, int width) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = x;
            constraints.gridy = y;
            constraints.gridwidth = width;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(0, 0, 10, 0);
            panel.add(component, constraints);
        }

        private boolean hasDisplayableFrames() {
            for (Frame frame : Frame.getFrames()) {
                if (frame.isDisplayable()) {
                    return true;
                }
            }
            return false;
        }
    }
}
