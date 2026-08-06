package br.ufmt.osguri.client;

import br.ufmt.osguri.protocolo.MensagemOSGURI;
import br.ufmt.osguri.protocolo.Poder;
import br.ufmt.osguri.protocolo.ProtocoloOSGURI;
import br.ufmt.osguri.protocolo.RelogioVetorial;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente de Linha de Comando (CLI) para o sistema de comunicação corporativo OSGURI.
 */
public class ClienteCLI {

    private final String host;
    private final int porta;
    private Socket socket;
    private InputStream entrada;
    private OutputStream saida;

    private String usuarioId;
    private String nome;
    private String orgao;
    private Poder poder;

    private final RelogioVetorial relogioVetorial;
    private final List<MensagemOSGURI> historicoMensagens;
    private boolean executando = true;

    public ClienteCLI(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.relogioVetorial = new RelogioVetorial();
        this.historicoMensagens = Collections.synchronizedList(new ArrayList<>());
    }

    public void iniciar() {
        Scanner scanner = new Scanner(System.in);

        try {
            socket = new Socket(host, porta);
            entrada = socket.getInputStream();
            saida = socket.getOutputStream();

            System.out.println("=================================================");
            System.out.println("   BEM-VINDO AO CLIENTE CORPORATIVO OSGURI");
            System.out.println("=================================================");

            // 1. Sigla do Estado
            System.out.print("Digite a sigla do seu estado: ");
            String estado = scanner.nextLine().trim();

            // 2. Seleção do Poder com validação (1-4)
            Poder poderSelecionado = null;
            while (poderSelecionado == null) {
                System.out.println("\nSelecione o seu Poder:");
                System.out.println(" 1. EXECUTIVO");
                System.out.println(" 2. LEGISLATIVO");
                System.out.println(" 3. JUDICIARIO");
                System.out.println(" 4. CONTROLE");
                System.out.print("Digite a opção (1-4): ");
                String opc = scanner.nextLine().trim();

                switch (opc) {
                    case "1":
                        poderSelecionado = Poder.EXECUTIVO;
                        break;
                    case "2":
                        poderSelecionado = Poder.LEGISLATIVO;
                        break;
                    case "3":
                        poderSelecionado = Poder.JUDICIARIO;
                        break;
                    case "4":
                        poderSelecionado = Poder.CONTROLE;
                        break;
                    default:
                        System.out.println("\nOpção inválida! Digite um número de 1 a 4.");
                }
            }
            this.poder = poderSelecionado;

            // 3. Órgão
            System.out.print("\nDigite o nome do órgão: ");
            this.orgao = scanner.nextLine().trim();

            // 4. Nome do Usuário
            System.out.print("Digite seu nome: ");
            this.nome = scanner.nextLine().trim();

            // Formatação do ID único do usuário
            this.usuarioId = (estado + "-" + orgao + "-" + nome).toUpperCase();

            // Registro do Login no Protocolo OSGURI
            relogioVetorial.incrementar(this.usuarioId);
            String payloadLogin = usuarioId + "|" + nome + "|" + orgao + "|" + poder.name();
            MensagemOSGURI msgLogin = new MensagemOSGURI(ProtocoloOSGURI.LOGIN, usuarioId, "BROKER", relogioVetorial.serializar(), payloadLogin);
            enviarMensagem(msgLogin.serializar());

            String respostaStr = lerLinha(entrada);
            MensagemOSGURI respLogin = MensagemOSGURI.desserializar(respostaStr);

            if (respLogin != null && respLogin.getTipo().equals(ProtocoloOSGURI.OK)) {
                System.out.println("\n[SISTEMA] " + respLogin.getConteudo());
            } else {
                String erroStr = respLogin != null ? respLogin.getConteudo() : "Falha na resposta do Broker.";
                System.out.println("\n[ERRO DE LOGIN] " + erroStr);
                return;
            }

            // Thread secundária para escutar o Broker em segundo plano
            Thread listenerThread = new Thread(this::escutarBroker);
            listenerThread.start();

            // Loop Principal do CLI
            exibirMenu();
            while (executando) {
                System.out.print("\nOSGURI> ");
                String linhaInput = scanner.nextLine().trim();
                if (linhaInput.isEmpty()) continue;

                if (linhaInput.equalsIgnoreCase("/sair")) {
                    executando = false;
                    break;
                } else if (linhaInput.startsWith("/msg ")) {
                    processarMsg(linhaInput);
                } else if (linhaInput.startsWith("/arquivo ")) {
                    processarArquivo(linhaInput);
                } else if (linhaInput.equalsIgnoreCase("/online") || linhaInput.startsWith("/busca")) {
                    processarOnline();
                } else if (linhaInput.startsWith("/grupo ")) {
                    processarGrupo(linhaInput);
                } else if (linhaInput.equalsIgnoreCase("/historico")) {
                    exibirHistoricoCausal();
                } else if (linhaInput.equalsIgnoreCase("/ajuda")) {
                    exibirMenu();
                } else {
                    System.out.println("Comando inválido. Digite /ajuda para listar os comandos.");
                }
            }

        } catch (IOException e) {
            System.err.println("Erro na conexão com o Broker: " + e.getMessage());
        } finally {
            fecharConexao();
        }
    }

    private void exibirMenu() {
        System.out.println("\n--- COMANDOS DO SISTEMA OSGURI ---");
        System.out.println(" /msg <destinatarioId> <mensagem>       - Enviar mensagem privada");
        System.out.println(" /arquivo <destinatarioId> <caminho>   - Transmitir arquivo (Base64)");
        System.out.println(" /online                               - Ver usuários conectados");
        System.out.println(" /grupo criar <nome> <TIPO>            - Criar grupo (INSTITUCIONAL|PRIVADO)");
        System.out.println(" /grupo entrar <nome>                  - Entrar em um grupo");
        System.out.println(" /grupo msg <nome> <texto>             - Enviar mensagem em grupo");
        System.out.println(" /historico                            - Exibir histórico ordenado causalmente");
        System.out.println(" /sair                                 - Encerrar sessão");
        System.out.println("-----------------------------------\n");
    }

    private void processarMsg(String comando) throws IOException {
        String[] partes = comando.substring(5).trim().split(" ", 2);
        if (partes.length < 2) {
            System.out.println("Uso: /msg <destinatarioId> <mensagem>");
            return;
        }
        String destino = partes[0].trim();
        String texto = partes[1].trim();

        relogioVetorial.incrementar(usuarioId);
        MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.MSG, usuarioId, destino, relogioVetorial.serializar(), texto);
        enviarMensagem(msg.serializar());
        historicoMensagens.add(msg);
        System.out.println("[ENVIADO] Mensagem encaminhada ao Broker.");
    }

    private void processarArquivo(String comando) throws IOException {
        String[] partes = comando.substring(9).trim().split(" ", 2);
        if (partes.length < 2) {
            System.out.println("Uso: /arquivo <destinatarioId> <caminhoArquivo>");
            return;
        }
        String destino = partes[0].trim();
        String caminho = partes[1].trim();

        File arq = new File(caminho);
        if (!arq.exists() || !arq.isFile()) {
            System.out.println("Arquivo não encontrado: " + caminho);
            return;
        }

        byte[] fileBytes;
        try (FileInputStream fis = new FileInputStream(arq)) {
            fileBytes = fis.readAllBytes();
        }
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        String payload = arq.getName() + "|" + base64Content;

        relogioVetorial.incrementar(usuarioId);
        MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.ARQUIVO, usuarioId, destino, relogioVetorial.serializar(), payload);
        enviarMensagem(msg.serializar());
        System.out.println("[ENVIADO] Arquivo '" + arq.getName() + "' codificado em Base64 e enviado ao Broker.");
    }

    private void processarOnline() throws IOException {
        relogioVetorial.incrementar(usuarioId);
        MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.ONLINE, usuarioId, "BROKER", relogioVetorial.serializar(), "");
        enviarMensagem(msg.serializar());
    }

    private void processarGrupo(String comando) throws IOException {
        String subComando = comando.substring(7).trim();
        if (subComando.startsWith("criar ")) {
            String[] partes = subComando.substring(6).trim().split(" ", 2);
            if (partes.length < 2) {
                System.out.println("Uso: /grupo criar <nomeGrupo> <INSTITUCIONAL|PRIVADO>");
                return;
            }
            String nomeGrupo = partes[0];
            String tipo = partes[1].toUpperCase();

            relogioVetorial.incrementar(usuarioId);
            String payload = nomeGrupo + "|" + tipo;
            MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.GRUPO_CRIAR, usuarioId, "BROKER", relogioVetorial.serializar(), payload);
            enviarMensagem(msg.serializar());

        } else if (subComando.startsWith("entrar ")) {
            String nomeGrupo = subComando.substring(7).trim();
            relogioVetorial.incrementar(usuarioId);
            MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.GRUPO_ENTRAR, usuarioId, "BROKER", relogioVetorial.serializar(), nomeGrupo);
            enviarMensagem(msg.serializar());

        } else if (subComando.startsWith("msg ")) {
            String[] partes = subComando.substring(4).trim().split(" ", 2);
            if (partes.length < 2) {
                System.out.println("Uso: /grupo msg <nomeGrupo> <mensagem>");
                return;
            }
            String nomeGrupo = partes[0];
            String texto = partes[1];

            relogioVetorial.incrementar(usuarioId);
            String payload = nomeGrupo + "|" + texto;
            MensagemOSGURI msg = new MensagemOSGURI(ProtocoloOSGURI.GRUPO_MSG, usuarioId, "BROKER", relogioVetorial.serializar(), payload);
            enviarMensagem(msg.serializar());
        } else {
            System.out.println("Subcomando inválido. Use criar, entrar ou msg.");
        }
    }

    private void escutarBroker() {
        try {
            String linha;
            while (executando && (linha = lerLinha(entrada)) != null) {
                MensagemOSGURI msg = MensagemOSGURI.desserializar(linha);
                if (msg == null) continue;

                RelogioVetorial rvRecebido = RelogioVetorial.desserializar(msg.getTimestampLogico());
                relogioVetorial.merge(rvRecebido.getRelogio());

                historicoMensagens.add(msg);

                String tipo = msg.getTipo();
                if (tipo.equals(ProtocoloOSGURI.MSG)) {
                    System.out.println("\n[" + msg.getRemetente() + " (" + msg.getTimestampLogico() + ")]: " + msg.getConteudo());

                } else if (tipo.equals(ProtocoloOSGURI.ARQUIVO)) {
                    salvarArquivoRecebido(msg);

                } else if (tipo.equals(ProtocoloOSGURI.BUSCA) || tipo.equals(ProtocoloOSGURI.ONLINE)) {
                    System.out.println("\n[USUÁRIOS ONLINE]" + msg.getConteudo());

                } else if (tipo.equals(ProtocoloOSGURI.GRUPO_MSG)) {
                    System.out.println("\n[GRUPO " + msg.getDestino() + " - " + msg.getRemetente() + "]: " + msg.getConteudo());

                } else if (tipo.equals(ProtocoloOSGURI.OK)) {
                    System.out.println("\n[OK] " + msg.getConteudo());

                } else if (tipo.equals(ProtocoloOSGURI.ERRO)) {
                    System.out.println("\n[ERRO/RESTRIÇÃO] " + msg.getConteudo());
                }
            }
        } catch (IOException e) {
            if (executando) {
                System.out.println("\nConexão com o Broker encerrada.");
            }
        }
    }

    private void salvarArquivoRecebido(MensagemOSGURI msg) {
        String[] partes = msg.getConteudo().split("\\|", 2);
        if (partes.length < 2) return;

        String nomeArquivo = partes[0];
        String base64Content = partes[1];

        try {
            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            File pastaDownloads = new File("downloads");
            if (!pastaDownloads.exists()) {
                pastaDownloads.mkdirs();
            }

            File arqSaida = new File(pastaDownloads, nomeArquivo);
            try (FileOutputStream fos = new FileOutputStream(arqSaida)) {
                fos.write(fileBytes);
            }

            System.out.println("\n[ARQUIVO RECEBIDO] Arquivo '" + nomeArquivo + "' (de " + msg.getRemetente() + ") salvo em downloads/");

        } catch (Exception e) {
            System.err.println("Erro ao salvar arquivo recebido: " + e.getMessage());
        }
    }

    private void exibirHistoricoCausal() {
        System.out.println("\n=== HISTÓRICO DE MENSAGENS (ORDEM CAUSAL / RELÓGIO VETORIAL) ===");
        synchronized (historicoMensagens) {
            List<MensagemOSGURI> copia = new ArrayList<>(historicoMensagens);
            copia.sort((m1, m2) -> {
                RelogioVetorial rv1 = RelogioVetorial.desserializar(m1.getTimestampLogico());
                RelogioVetorial rv2 = RelogioVetorial.desserializar(m2.getTimestampLogico());
                return rv1.compareTo(rv2);
            });

            for (MensagemOSGURI m : copia) {
                System.out.println(" - " + m.getTimestampLogico() + " | " + m.getTipo() + " | " + m.getRemetente() + " -> " + m.getDestino() + ": " + m.getConteudo());
            }
        }
        System.out.println("=================================================================\n");
    }

    private synchronized void enviarMensagem(String linha) throws IOException {
        if (saida != null) {
            saida.write((linha + "\n").getBytes(StandardCharsets.UTF_8));
            saida.flush();
        }
    }

    private static String lerLinha(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') baos.write(b);
        }
        if (b == -1 && baos.size() == 0) return null;
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void fecharConexao() {
        executando = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        String host = System.getenv("OSGURI_HOST");
        if (host == null || host.trim().isEmpty()) {
            host = "localhost";
        }
        int porta = ProtocoloOSGURI.PORTA_PADRAO;
        String envPorta = System.getenv("OSGURI_PORT");
        if (envPorta != null && !envPorta.trim().isEmpty()) {
            try {
                porta = Integer.parseInt(envPorta.trim());
            } catch (NumberFormatException ignored) {}
        }

        if (args.length > 0 && !args[0].trim().isEmpty()) host = args[0].trim();
        if (args.length > 1 && !args[1].trim().isEmpty()) {
            try {
                porta = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        ClienteCLI cliente = new ClienteCLI(host, porta);
        cliente.iniciar();
    }
}
