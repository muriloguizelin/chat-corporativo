package br.ufmt.osguri.broker;

import br.ufmt.osguri.protocolo.MensagemOSGURI;
import br.ufmt.osguri.protocolo.Poder;
import br.ufmt.osguri.protocolo.ProtocoloOSGURI;
import br.ufmt.osguri.protocolo.RelogioVetorial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Worker responsável pelo atendimento individual a um cliente conectado ao Broker OSGURI.
 * Gerenciado via pool de threads (ExecutorService).
 */
public class ClienteHandler implements Runnable {

    private final Socket socket;
    private final ServidorBroker broker;
    private InputStream entrada;
    private OutputStream saida;

    private String usuarioId;
    private String nome;
    private String orgao;
    private Poder poder;
    private final RelogioVetorial relogioVetorial;

    public ClienteHandler(Socket socket, ServidorBroker broker) {
        this.socket = socket;
        this.broker = broker;
        this.relogioVetorial = new RelogioVetorial();
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Poder getPoder() {
        return poder;
    }

    public String getOrgao() {
        return orgao;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public void run() {
        try {
            entrada = socket.getInputStream();
            saida = socket.getOutputStream();

            String linha;
            while ((linha = lerLinha(entrada)) != null) {
                MensagemOSGURI msg = MensagemOSGURI.desserializar(linha);
                if (msg != null) {
                    processarMensagem(msg);
                }
            }
        } catch (IOException e) {
            // Conexão encerrada pelo cliente
        } finally {
            fecharConexao();
        }
    }

    private void processarMensagem(MensagemOSGURI msg) throws IOException {
        String tipo = msg.getTipo();

        // 1. Processamento de LOGIN
        if (tipo.equals(ProtocoloOSGURI.LOGIN)) {
            processarLogin(msg);
            return;
        }

        if (usuarioId == null) {
            enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Login necessario antes de enviar mensagens.");
            return;
        }

        // Atualiza o Relógio Vetorial local a partir do relógio recebido
        RelogioVetorial relogioRecebido = RelogioVetorial.desserializar(msg.getTimestampLogico());
        relogioVetorial.merge(relogioRecebido.getRelogio());
        relogioVetorial.incrementar(usuarioId);

        // 2. Mensagem Direta
        if (tipo.equals(ProtocoloOSGURI.MSG)) {
            ClienteHandler destinoHandler = broker.buscarCliente(msg.getDestino());
            if (destinoHandler == null) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Usuario destino nao encontrado.");
                return;
            }

            // Aplicação da Matriz de Restrição de Comunicação entre Poderes
            if (!Poder.podeComunicar(this.poder, destinoHandler.getPoder())) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, 
                    "BLOQUEIO ENTRE PODERES: Comunicacao direta proibida entre " + this.poder + " e " + destinoHandler.getPoder());
                return;
            }

            // Registra Auditoria de Não-Repúdio
            broker.getLoggerAudit().registrarEvento(msg);

            // Repassa a mensagem ao destinatário
            MensagemOSGURI msgEncaminhada = new MensagemOSGURI(
                ProtocoloOSGURI.MSG, usuarioId, msg.getDestino(), relogioVetorial.serializar(), msg.getConteudo()
            );
            destinoHandler.enviarMensagemRaw(msgEncaminhada.serializar());

        // 3. Envio de Arquivo (Base64)
        } else if (tipo.equals(ProtocoloOSGURI.ARQUIVO)) {
            ClienteHandler destinoHandler = broker.buscarCliente(msg.getDestino());
            if (destinoHandler == null) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Usuario destino nao encontrado.");
                return;
            }

            if (!Poder.podeComunicar(this.poder, destinoHandler.getPoder())) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, 
                    "BLOQUEIO ENTRE PODERES: Envio de arquivo proibido entre " + this.poder + " e " + destinoHandler.getPoder());
                return;
            }

            broker.getLoggerAudit().registrarEvento(msg);

            MensagemOSGURI msgArquivo = new MensagemOSGURI(
                ProtocoloOSGURI.ARQUIVO, usuarioId, msg.getDestino(), relogioVetorial.serializar(), msg.getConteudo()
            );
            destinoHandler.enviarMensagemRaw(msgArquivo.serializar());

        // 4. Busca / Online Usuários Cadastrados
        } else if (tipo.equals(ProtocoloOSGURI.BUSCA) || tipo.equals(ProtocoloOSGURI.ONLINE)) {
            String lista = broker.listarUsuariosCadastrados();
            enviarResposta(ProtocoloOSGURI.ONLINE, "SISTEMA", usuarioId, lista);

        // 5. Criação de Grupos
        } else if (tipo.equals(ProtocoloOSGURI.GRUPO_CRIAR)) {
            String[] partes = msg.getConteudo().split("\\|");
            if (partes.length < 2) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Uso: GRUPO_CRIAR|nomeGrupo|INSTITUCIONAL/PRIVADO");
                return;
            }
            String nomeGrupo = partes[0].trim();
            Grupo.TipoGrupo tipoGrupo = partes[1].trim().equalsIgnoreCase("INSTITUCIONAL") ? 
                    Grupo.TipoGrupo.INSTITUCIONAL : Grupo.TipoGrupo.PRIVADO;

            boolean criado = broker.criarGrupo(nomeGrupo, tipoGrupo, usuarioId, this.poder);
            if (criado) {
                enviarResposta(ProtocoloOSGURI.OK, "SISTEMA", usuarioId, "Grupo '" + nomeGrupo + "' criado com sucesso.");
            } else {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Grupo ja existente.");
            }

        // 6. Entrar em Grupo
        } else if (tipo.equals(ProtocoloOSGURI.GRUPO_ENTRAR)) {
            String nomeGrupo = msg.getConteudo().trim();
            boolean ok = broker.entrarGrupo(nomeGrupo, usuarioId, this.poder);
            if (ok) {
                enviarResposta(ProtocoloOSGURI.OK, "SISTEMA", usuarioId, "Voce entrou no grupo '" + nomeGrupo + "'.");
            } else {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Falha ao entrar no grupo (Restricao de Poder/Orgao ou grupo inexistente).");
            }

        // 7. Envio de Mensagem em Grupo
        } else if (tipo.equals(ProtocoloOSGURI.GRUPO_MSG)) {
            String[] partes = msg.getConteudo().split("\\|", 2);
            if (partes.length < 2) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Formato invalido para GRUPO_MSG");
                return;
            }
            String nomeGrupo = partes[0].trim();
            String texto = partes[1];

            Grupo grupo = broker.buscarGrupo(nomeGrupo);
            if (grupo == null) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Grupo nao encontrado.");
                return;
            }

            if (!grupo.eMembro(usuarioId)) {
                enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", usuarioId, "Voce nao e membro deste grupo.");
                return;
            }

            broker.getLoggerAudit().registrarEvento(msg);

            for (String membroId : grupo.getMembros()) {
                if (!membroId.equals(usuarioId)) {
                    ClienteHandler h = broker.buscarCliente(membroId);
                    if (h != null) {
                        MensagemOSGURI msgGrupo = new MensagemOSGURI(
                            ProtocoloOSGURI.GRUPO_MSG, usuarioId, nomeGrupo, relogioVetorial.serializar(), texto
                        );
                        h.enviarMensagemRaw(msgGrupo.serializar());
                    }
                }
            }

        // 8. Consulta de Histórico de Auditoria
        } else if (tipo.equals(ProtocoloOSGURI.HISTORICO)) {
            List<String> logs = broker.getLoggerAudit().getHistorico();
            String conteudoHistorico = String.join("\n", logs);
            enviarResposta(ProtocoloOSGURI.HISTORICO, "SISTEMA", usuarioId, conteudoHistorico);
        }
    }

    private void processarLogin(MensagemOSGURI msg) throws IOException {
        String[] partes = msg.getConteudo().split("\\|");
        if (partes.length < 3) {
            enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", "DESCONHECIDO", "Formato de LOGIN invalido.");
            return;
        }

        String idCandidato = partes[0].trim();
        String nomeCandidato = partes[1].trim();
        String orgaoCandidato = partes[2].trim();
        Poder poderCandidato = partes.length >= 4 ? Poder.parse(partes[3]) : Poder.inferirPoder(orgaoCandidato);

        boolean ok = broker.registrarCliente(idCandidato, this);
        if (!ok) {
            enviarResposta(ProtocoloOSGURI.ERRO, "SISTEMA", idCandidato, "ID de usuario ja cadastrado.");
            return;
        }

        this.usuarioId = idCandidato;
        this.nome = nomeCandidato;
        this.orgao = orgaoCandidato;
        this.poder = poderCandidato;
        this.relogioVetorial.incrementar(this.usuarioId);

        System.out.println("Usuário registrado no Broker: " + usuarioId + " [" + poder + " - " + orgao + "]");
        enviarResposta(ProtocoloOSGURI.OK, "SISTEMA", usuarioId, "Conectado com sucesso como " + usuarioId + " (" + poder + ")");
    }

    private void enviarResposta(String tipo, String remetente, String destino, String conteudo) throws IOException {
        MensagemOSGURI resp = new MensagemOSGURI(tipo, remetente, destino, relogioVetorial.serializar(), conteudo);
        enviarMensagemRaw(resp.serializar());
    }

    public synchronized void enviarMensagemRaw(String linha) throws IOException {
        if (saida != null) {
            saida.write((linha + "\n").getBytes(StandardCharsets.UTF_8));
            saida.flush();
        }
    }

    private String lerLinha(InputStream in) throws IOException {
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
        if (usuarioId != null) {
            broker.removerCliente(usuarioId);
            System.out.println("Cliente desconectado do Broker: " + usuarioId);
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
